package com.boxowl.aroundtheworld.expedition.scene

import com.boxowl.aroundtheworld.expedition.FirstLegMapProjection
import com.boxowl.aroundtheworld.expedition.JOURNEY_SCENES

/**
 * World geometry of the seamless journey (P07). One world unit equals the scene
 * canvas height; visual units are NOT kilometers and do not map to real steps.
 * Stop anchors are evenly spaced by [SEGMENT_LENGTH]; inside a segment the hero
 * advances by the walked-steps fraction, so different segments may "move" at
 * different visual speeds. The mapping is continuous across every stop boundary.
 */
internal const val SEGMENT_LENGTH = 1.4f

/** Decorated world margin before London and after Suez (world units). */
internal const val WORLD_MARGIN_START = 0.55f
internal const val WORLD_MARGIN_END = 0.6f

internal class WorldLayout(val stopCount: Int) {
    init { require(stopCount >= 2) }
    val start: Float = 0f
    val end: Float = WORLD_MARGIN_START + (stopCount - 1) * SEGMENT_LENGTH + WORLD_MARGIN_END

    fun anchorX(index: Int): Float {
        require(index in 0 until stopCount)
        return WORLD_MARGIN_START + index * SEGMENT_LENGTH
    }

    /** Continuous route position (stop index + in-segment fraction) → world X. */
    fun worldXAt(position: Float): Float =
        WORLD_MARGIN_START + position.coerceIn(0f, (stopCount - 1).toFloat()) * SEGMENT_LENGTH
}

internal val FIRST_LEG_LAYOUT = WorldLayout(JOURNEY_SCENES.size)

/**
 * Route position from the saved expedition snapshot. Unknown progress
 * (no daily steps at all) pins the view to the start, as in P04/P06.
 */
internal fun routePositionOf(projection: FirstLegMapProjection): Float {
    if (projection.knownSteps == null) return 0f
    val position = projection.currentIndex.toFloat() +
        (projection.nextIndex?.let { projection.fractionToNext } ?: 0f)
    return position.coerceIn(0f, (projection.stops.size - 1).toFloat())
}

/** Accessibility description of the scene, derived from the target (confirmed) position. */
internal fun sceneDescriptionAt(position: Float): String {
    val scenes = JOURNEY_SCENES
    val clamped = position.coerceIn(0f, (scenes.size - 1).toFloat())
    val index = clamped.toInt().coerceAtMost(scenes.size - 1)
    val fraction = clamped - index
    return if (fraction > 0f && index + 1 < scenes.size) {
        "Сцена пути: переход ${scenes[index].label} → ${scenes[index + 1].label}"
    } else {
        "Сцена пути: ${scenes[index].label}: ${scenes[index].details}"
    }
}

internal class SceneMood(val haze: Float, val warmth: Float)

/**
 * Per-stop sky adjustments (haze/warmth) as a smooth function of world X:
 * each stop contributes a smooth pulse with a half-segment window, so adjacent
 * windows never overlap and silhouettes stay solid — only the sky shifts.
 */
internal fun sceneMoodAt(layout: WorldLayout, worldX: Float): SceneMood {
    var haze = 0f
    var warmth = 0f
    for (i in 0 until layout.stopCount) {
        val d = kotlin.math.abs(worldX - layout.anchorX(i)) / (0.5f * SEGMENT_LENGTH)
        if (d < 1f) {
            val t = 1f - d
            val w = t * t * (3f - 2f * t)
            haze += JOURNEY_SCENES[i].haze * w
            warmth += JOURNEY_SCENES[i].warmth * w
        }
    }
    return SceneMood(haze, warmth)
}
