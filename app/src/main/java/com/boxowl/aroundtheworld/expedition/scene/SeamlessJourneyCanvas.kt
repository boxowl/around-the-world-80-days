package com.boxowl.aroundtheworld.expedition.scene

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
import kotlinx.coroutines.flow.first

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
) {
    val resolver = LocalContext.current.contentResolver
    val reducedMotion = remember {
        Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
    val position = remember { Animatable(targetPosition) }
    val walkPhase = remember { mutableFloatStateOf(0f) }
    val gait = remember { mutableFloatStateOf(0f) }
    val latestTarget by rememberUpdatedState(targetPosition)
    val layout = FIRST_LEG_LAYOUT
    LaunchedEffect(reducedMotion) {
        while (true) {
            val target = latestTarget
            if (reducedMotion || position.value == target) {
                if (position.value != target) position.snapTo(target)
                gait.floatValue = 0f // at rest the hero stands, the stride freezes
                // At rest: sleep until a new target arrives instead of polling frames.
                snapshotFlow { latestTarget }.first { it != target }
            } else {
                // New plan from the currently displayed value. The step is
                // applied BEFORE the target-change check: a continuously moving
                // target (debug auto pass) would otherwise starve the plan into
                // an endless replan without a single step. Replanning starts
                // the next plan from the last frame's clock, so no frame is lost.
                val from = position.value
                var startNanos = withFrameNanos { it }
                var previousNanos = startNanos
                var running = true
                while (running) {
                    val now = withFrameNanos { it }
                    val before = layout.worldXAt(position.value)
                    val next = ChaseAnimation.chaseValueAt(from, target, (now - startNanos) / 1_000_000)
                    position.snapTo(next)
                    val after = layout.worldXAt(next)
                    val dt = ((now - previousNanos) / 1_000_000_000f).coerceIn(0f, 0.1f)
                    previousNanos = now
                    walkPhase.floatValue = WalkCycle.advancePhase(walkPhase.floatValue, after - before)
                    gait.floatValue = WalkCycle.gaitAt(gait.floatValue, (after - before) / dt.coerceAtLeast(1e-3f), dt)
                    if (latestTarget != target) {
                        startNanos = now // replan from here, this frame already counted
                        break
                    }
                    running = next != target
                }
            }
        }
    }
    Canvas(modifier.semantics { contentDescription = description }) {
        val viewWidth = size.width / size.height
        val heroWorldX = layout.worldXAt(position.value)
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
}
