package com.boxowl.aroundtheworld.expedition.scene

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
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
import kotlin.math.abs
import kotlin.math.exp

/**
 * Seamless journey scene (P07): one continuous side-scrolling world, a camera
 * following the hero, and the terrain profile under the hero's feet. No progress
 * is invented here — the position comes from the confirmed expedition snapshot;
 * animations only catch the view up to it.
 *
 * Animation policy (owner decision): first composition starts already at the
 * saved position; small updates settle briefly (≈250–750 ms), big jumps are
 * capped at 2 s; backward corrections use the same function without rebuilding
 * the world; reduced-motion snaps instantly. The chase is frame-driven (one
 * persistent loop), so a continuously moving target — the debug auto pass —
 * cannot starve it, and it pauses whenever frames stop (background/tab switch).
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
    val latestTarget by rememberUpdatedState(targetPosition)
    LaunchedEffect(reducedMotion) {
        var previousFrame = withFrameNanos { it }
        var tau = 0.25f
        var lastTarget = latestTarget
        while (true) {
            val now = withFrameNanos { it }
            val dt = ((now - previousFrame) / 1_000_000_000f).coerceIn(0f, 0.1f)
            previousFrame = now
            val target = latestTarget
            if (target != lastTarget) {
                // Settle ≈95 % within 250 ms + 1.75 s per route position, capped at 2 s.
                val distance = abs(target - position.value)
                tau = (250f + distance * 1750f).coerceAtMost(2000f) / 3000f
                lastTarget = target
            }
            val value = position.value
            val next = if (reducedMotion) target
                else value + (target - value) * (1f - exp(-dt / tau))
            if (abs(next - value) > 1e-6f) position.snapTo(next)
        }
    }
    val layout = FIRST_LEG_LAYOUT
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
        )
    }
}
