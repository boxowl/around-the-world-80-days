package com.boxowl.aroundtheworld.expedition.scene

import android.provider.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.boxowl.aroundtheworld.expedition.FirstLegMapProjection
import com.boxowl.aroundtheworld.expedition.adjusted
import com.boxowl.aroundtheworld.expedition.celestial
import com.boxowl.aroundtheworld.expedition.celestialPosition
import com.boxowl.aroundtheworld.expedition.dayPhaseAt
import com.boxowl.aroundtheworld.expedition.hero
import com.boxowl.aroundtheworld.expedition.paletteFor
import com.boxowl.aroundtheworld.expedition.sky
import com.boxowl.aroundtheworld.expedition.stars
import com.boxowl.aroundtheworld.expedition.DayPhase
import java.time.LocalTime
import kotlin.math.abs
import kotlin.math.exp
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Pure browse-over-the-past math (V2.1 proto, owner prompt §3.1). The confirmed
 * progress position is never touched by browsing: [browsePosition] is a purely
 * visual camera target, always clamped to `[0, confirmed]`. Dragging converts
 * pixels to route positions (one world unit = canvas height, one route position
 * = [SEGMENT_LENGTH] world units); fling inertia decays exponentially and is
 * hard-clamped at the confirmed position — inertia can never fly into the
 * future. A backward steps correction squeezes a stored browse position down
 * to the new confirmed boundary via [squeezeBrowse].
 */
internal object JourneyBrowse {
    /** Distance behind the confirmed position from which the "browsing" label shows. */
    const val BROWSE_EPSILON = 0.01f

    /** Fling inertia decay time constant, seconds (short, no long glides). */
    const val INERTIA_TAU_SEC = 0.18f

    /** Inertia stops below this speed, route positions per second. */
    const val INERTIA_STOP_SPEED = 0.02f

    /** Browse position clamped to `[0, confirmed]` (and the confirmed to the route). */
    fun clampBrowse(browse: Float, confirmed: Float, maxPosition: Float): Float =
        browse.coerceIn(0f, confirmed.coerceIn(0f, maxPosition))

    /**
     * Horizontal drag in pixels → route-position delta. Content follows the
     * finger: swiping right looks BACK (negative delta), swiping left forward.
     */
    fun dragDeltaPositions(dragAmountPx: Float, viewHeightPx: Float): Float {
        if (viewHeightPx <= 0f) return 0f
        return -dragAmountPx / viewHeightPx / SEGMENT_LENGTH
    }

    /** Inertia velocity after [dtSec] of exponential decay; sign is preserved. */
    fun inertiaVelocityAt(velocity: Float, dtSec: Float): Float {
        if (dtSec <= 0f) return velocity
        return velocity * exp(-dtSec / INERTIA_TAU_SEC)
    }

    /** Effective render target: the browsed position, never ahead of the confirmed one. */
    fun effectiveTarget(browse: Float?, confirmed: Float, maxPosition: Float): Float =
        clampBrowse(browse ?: confirmed, confirmed, maxPosition)

    /**
     * Squeeze a stored browse position after the confirmed boundary moved.
     * A forward sync leaves browsing untouched; a backward correction pulls the
     * browsed position down to the new boundary. `null` (not browsing) stays `null`.
     */
    fun squeezeBrowse(browse: Float?, confirmed: Float, maxPosition: Float): Float? =
        browse?.let { clampBrowse(it, confirmed, maxPosition) }

    /** True while the browsed position is meaningfully behind the confirmed one. */
    fun isBrowsing(browse: Float?, confirmed: Float): Boolean =
        browse != null && browse < confirmed - BROWSE_EPSILON

    /**
     * Exclusive writer of the camera position (V2.1 review fix). Exactly one
     * owner at a time: the chase plan (CHASE), the finger (DRAG) or the fling
     * (INERTIA). The gesture captures ownership atomically in [ownerOnDragStart]
     * before the first applied delta and keeps it through the inertia until
     * [ownerOnInertiaEnd]; the chase loop must check [chaseMayWrite] BEFORE
     * applying every frame, so a stale plan frame can never roll the camera
     * back after a capture.
     */
    enum class CameraOwner { CHASE, DRAG, INERTIA }

    /** The chase plan may write the camera position only while it owns it. */
    fun chaseMayWrite(owner: CameraOwner): Boolean = owner == CameraOwner.CHASE

    /** Gesture start: the finger takes the camera from any state. */
    fun ownerOnDragStart(): CameraOwner = CameraOwner.DRAG

    /** Gesture end: the fling inherits ownership, otherwise the plan resumes. */
    fun ownerOnDragEnd(startsInertia: Boolean): CameraOwner =
        if (startsInertia) CameraOwner.INERTIA else CameraOwner.CHASE

    /** Gesture cancelled (no fling): ownership returns to the plan. */
    fun ownerOnGestureCancel(): CameraOwner = CameraOwner.CHASE

    /**
     * Inertia finished (stopped or cancelled). Ownership returns to the plan
     * ONLY if inertia still holds it — a cancelled fling whose job ends after
     * a new drag already captured the camera must not downgrade DRAG.
     */
    fun ownerOnInertiaEnd(current: CameraOwner): CameraOwner =
        if (current == CameraOwner.INERTIA) CameraOwner.CHASE else current
}

/**
 * Seamless journey scene (P07): one continuous side-scrolling world, a camera
 * following the hero, and the terrain profile under the hero's feet. No progress
 * is invented here — the position comes from the confirmed expedition snapshot;
 * animations only catch the view up to it.
 *
 * Animation policy (owner decision, P08): first composition starts already at
 * the saved position; a target change starts a [ChaseAnimation] plan from the
 * currently displayed value that reaches the target exactly within 2 s (250 ms
 * + 1.75 s per route position); backward corrections use the same function
 * without rebuilding the world; reduced-motion snaps instantly. Once settled,
 * the frame loop sleeps until the target changes again — in a still world
 * nothing moves and nothing is redrawn.
 *
 * Browse-over-the-past (V2.1 proto, owner prompt §3.1, [browseEnabled]): a
 * horizontal drag looks back along the already walked path; the visual browse
 * position is strictly separated from the confirmed progress position and can
 * never pass it (drag and inertia are hard-clamped). "К текущей позиции"
 * returns through the same [ChaseAnimation]; a new sync only moves the browse
 * boundary and never yanks the camera out of browsing; a backward correction
 * squeezes the browsed position to the new boundary. Reduced-motion: drag
 * follows the finger directly, return snaps instantly.
 *
 * Camera ownership (V2.1 review fix, [JourneyBrowse.CameraOwner]): exactly one
 * writer of the camera position at a time. A drag captures ownership in
 * `onDragStart` and keeps it through the fling; the chase loop re-checks
 * [JourneyBrowse.chaseMayWrite] before applying EVERY frame, so a stale plan
 * frame can never roll the camera back after a capture. When the fling ends,
 * ownership returns to the plan, which resumes from the currently displayed
 * value.
 */
@Composable
internal fun SeamlessJourneyCanvas(
    projection: FirstLegMapProjection,
    time: LocalTime,
    modifier: Modifier = Modifier,
) {
    val target = routePositionOf(projection)
    SeamlessJourneyCanvas(target, sceneDescriptionAt(target), time, modifier)
}

/** Core renderer; the debug demo drives this overload directly. */
@Composable
internal fun SeamlessJourneyCanvas(
    targetPosition: Float,
    description: String,
    time: LocalTime,
    modifier: Modifier = Modifier,
    browseEnabled: Boolean = false,
) {
    val resolver = LocalContext.current.contentResolver
    val reducedMotion = remember {
        Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
    val layout = FIRST_LEG_LAYOUT
    val maxPosition = (layout.stopCount - 1).toFloat()
    val position = remember { mutableFloatStateOf(targetPosition) }
    val browse = remember { mutableStateOf<Float?>(null) }
    val walkPhase = remember { mutableFloatStateOf(0f) }
    val gait = remember { mutableFloatStateOf(0f) }
    val latestTarget by rememberUpdatedState(targetPosition)
    val scope = rememberCoroutineScope()
    var inertiaJob by remember { mutableStateOf<Job?>(null) }
    var cameraOwner by remember { mutableStateOf(JourneyBrowse.CameraOwner.CHASE) }

    /**
     * Fling after a browse drag: exponential decay, hard stop at the confirmed
     * boundary. Owns the camera (INERTIA) for its whole lifetime; ownership is
     * released in `finally` and only if inertia still holds it — a fling
     * cancelled by a new drag must not hand the camera back under the finger.
     */
    suspend fun runBrowseInertia(initialVelocity: Float) {
        try {
            var velocity = initialVelocity
            var previousNanos = withFrameNanos { it }
            while (abs(velocity) > JourneyBrowse.INERTIA_STOP_SPEED) {
                val now = withFrameNanos { it }
                val dt = ((now - previousNanos) / 1_000_000_000f).coerceIn(0f, 0.1f)
                previousNanos = now
                val before = layout.worldXAt(position.floatValue)
                val next = JourneyBrowse.clampBrowse(
                    position.floatValue + velocity * dt, latestTarget, maxPosition,
                )
                position.floatValue = next
                browse.value = next
                val after = layout.worldXAt(next)
                walkPhase.floatValue = WalkCycle.advancePhase(walkPhase.floatValue, after - before)
                val strideSpeed = if (TerrainProfile.supportAt(layout, after) == TerrainProfile.Support.DECK) 0f
                    else (after - before) / dt.coerceAtLeast(1e-3f)
                gait.floatValue = WalkCycle.gaitAt(gait.floatValue, strideSpeed, dt)
                val boundary = latestTarget.coerceIn(0f, maxPosition)
                if ((velocity > 0f && next >= boundary) || (velocity < 0f && next <= 0f)) break
                velocity = JourneyBrowse.inertiaVelocityAt(velocity, dt)
            }
            gait.floatValue = 0f
        } finally {
            cameraOwner = JourneyBrowse.ownerOnInertiaEnd(cameraOwner)
        }
    }

    LaunchedEffect(reducedMotion) {
        while (true) {
            // A new confirmed position only moves the browse boundary: forward
            // syncs leave the camera where the user is looking; backward
            // corrections squeeze the browsed position down to the new edge.
            browse.value = JourneyBrowse.squeezeBrowse(browse.value, latestTarget, maxPosition)
            val target = browse.value ?: latestTarget
            if (!JourneyBrowse.chaseMayWrite(cameraOwner)) {
                // The finger or the fling owns the camera: the plan must not
                // write a single frame until ownership comes back.
                snapshotFlow { cameraOwner }.first { JourneyBrowse.chaseMayWrite(it) }
            } else if (reducedMotion || position.floatValue == target) {
                if (position.floatValue != target) {
                    position.floatValue = target
                    gait.floatValue = 0f
                }
                // At rest: sleep until the effective target moves (confirmed
                // change, drag, inertia or "К текущей позиции") or a gesture
                // captures the camera, instead of polling.
                snapshotFlow {
                    cameraOwner to JourneyBrowse.effectiveTarget(browse.value, latestTarget, maxPosition)
                }.first { (owner, effective) ->
                    !JourneyBrowse.chaseMayWrite(owner) || effective != target
                }
            } else {
                // New plan from the currently displayed value. The step is
                // applied BEFORE the target-change check: a continuously moving
                // target (debug auto pass) would otherwise starve the plan into
                // an endless replan without a single step. Replanning starts
                // the next plan from the last frame's clock, so no frame is lost.
                val from = position.floatValue
                var startNanos = withFrameNanos { it }
                var previousNanos = startNanos
                var running = true
                while (running) {
                    val now = withFrameNanos { it }
                    if (!JourneyBrowse.chaseMayWrite(cameraOwner)) break
                    val before = layout.worldXAt(position.floatValue)
                    val next = ChaseAnimation.chaseValueAt(from, target, (now - startNanos) / 1_000_000)
                    position.floatValue = next
                    val after = layout.worldXAt(next)
                    val dt = ((now - previousNanos) / 1_000_000_000f).coerceIn(0f, 0.1f)
                    previousNanos = now
                    walkPhase.floatValue = WalkCycle.advancePhase(walkPhase.floatValue, after - before)
                    // Aboard the ship (DECK) the hero stands at ease — the water,
                    // shore and hull sliding past carry the motion instead.
                    val strideSpeed = if (TerrainProfile.supportAt(layout, after) == TerrainProfile.Support.DECK) 0f
                        else (after - before) / dt.coerceAtLeast(1e-3f)
                    gait.floatValue = WalkCycle.gaitAt(gait.floatValue, strideSpeed, dt)
                    if (next == target) {
                        gait.floatValue = 0f // landed: the hero stands, the stride freezes
                        running = false
                    } else if (
                        JourneyBrowse.effectiveTarget(browse.value, latestTarget, maxPosition) != target
                    ) {
                        startNanos = now // replan from here, this frame already counted
                        break
                    }
                }
            }
        }
    }

    val gestureModifier = if (browseEnabled) {
        Modifier.pointerInput(Unit) {
            var lastDragUptime = -1L
            var dragVelocity = 0f
            detectHorizontalDragGestures(
                onDragStart = {
                    // Cancel the fling first: its `finally` must not downgrade
                    // the DRAG ownership we are about to take.
                    inertiaJob?.cancel()
                    // Atomic capture: from this line the chase plan cannot
                    // write the camera until the fling after this drag ends.
                    cameraOwner = JourneyBrowse.ownerOnDragStart()
                    // Enter browsing wherever the camera currently is (possibly mid-chase).
                    browse.value = JourneyBrowse.clampBrowse(
                        position.floatValue, latestTarget, maxPosition,
                    )
                    lastDragUptime = -1L
                    dragVelocity = 0f
                },
                onDragEnd = {
                    val startsInertia = !reducedMotion && abs(dragVelocity) > JourneyBrowse.INERTIA_STOP_SPEED
                    cameraOwner = JourneyBrowse.ownerOnDragEnd(startsInertia)
                    if (startsInertia) {
                        inertiaJob = scope.launch { runBrowseInertia(dragVelocity) }
                    } else {
                        gait.floatValue = 0f
                    }
                    dragVelocity = 0f
                },
                onDragCancel = {
                    cameraOwner = JourneyBrowse.ownerOnGestureCancel()
                    gait.floatValue = 0f
                    dragVelocity = 0f
                },
            ) { change, dragAmount ->
                change.consume()
                val dt = if (lastDragUptime >= 0L) {
                    ((change.uptimeMillis - lastDragUptime) / 1000f).coerceIn(0.001f, 0.1f)
                } else {
                    0.016f
                }
                lastDragUptime = change.uptimeMillis
                val deltaPositions = JourneyBrowse.dragDeltaPositions(dragAmount, size.height.toFloat())
                val instantVelocity = deltaPositions / dt
                dragVelocity = if (dragVelocity == 0f) instantVelocity
                    else dragVelocity * 0.5f + instantVelocity * 0.5f
                // The camera follows the finger strictly (snap, no chase).
                val before = layout.worldXAt(position.floatValue)
                val next = JourneyBrowse.clampBrowse(
                    position.floatValue + deltaPositions, latestTarget, maxPosition,
                )
                position.floatValue = next
                browse.value = next
                val after = layout.worldXAt(next)
                walkPhase.floatValue = WalkCycle.advancePhase(walkPhase.floatValue, after - before)
                val strideSpeed = if (TerrainProfile.supportAt(layout, after) == TerrainProfile.Support.DECK) 0f
                    else (after - before) / dt
                gait.floatValue = WalkCycle.gaitAt(gait.floatValue, strideSpeed, dt)
            }
        }
    } else {
        Modifier
    }

    Box(modifier) {
        Canvas(
            Modifier
                .fillMaxSize()
                .then(gestureModifier)
                .semantics { contentDescription = description },
        ) {
            val viewWidth = size.width / size.height
            val heroWorldX = layout.worldXAt(position.floatValue)
            val cameraX = JourneyCamera.cameraXFor(heroWorldX, layout, viewWidth)
            val mood = sceneMoodAt(layout, heroWorldX)
            val palette = paletteFor(time).adjusted(mood.haze, mood.warmth)
            sky(palette)
            stars(palette.starAlpha)
            val phase = dayPhaseAt(time)
            celestial(palette, celestialPosition(time, phase), moon = phase == DayPhase.NIGHT)
            drawWorldSegments(layout, cameraX, viewWidth, palette)
            hero(
                JourneyCamera.screenFraction(heroWorldX, SceneLayer.NEAR, cameraX, viewWidth) * size.width,
                TerrainProfile.groundYAt(layout, heroWorldX) * size.height,
                palette,
                walkPhase.floatValue,
                gait.floatValue,
            )
        }
        if (browseEnabled && JourneyBrowse.isBrowsing(browse.value, latestTarget)) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 8.dp),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
                contentColor = MaterialTheme.colorScheme.onSurface,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Просмотр пройденного",
                        modifier = Modifier.padding(start = 16.dp),
                        style = MaterialTheme.typography.labelMedium,
                    )
                    TextButton(onClick = {
                        inertiaJob?.cancel()
                        browse.value = null // chase animation returns to the confirmed position
                    }) { Text("К текущей позиции") }
                }
            }
        }
    }
}
