package com.boxowl.aroundtheworld.expedition.scene

/**
 * Single camera for the whole world (P07). The hero stays at [HERO_FRACTION] of
 * the viewport width; the world slides past. All parallax layers derive from the
 * same camera X — there are no resets at segment boundaries.
 *
 * Viewport width is expressed in world units (canvasWidth / canvasHeight).
 */
internal enum class SceneLayer(val parallax: Float) {
    FAR(0.25f),
    MID(0.55f),
    NEAR(1.0f),
}

internal object JourneyCamera {
    const val HERO_FRACTION = 0.42f

    fun minCameraX(layout: WorldLayout, viewWidth: Float): Float =
        layout.start + HERO_FRACTION * viewWidth

    fun maxCameraX(layout: WorldLayout, viewWidth: Float): Float =
        layout.end - (1f - HERO_FRACTION) * viewWidth

    /** Camera X for a hero at [heroWorldX], clamped so world edges never enter the screen. */
    fun cameraXFor(heroWorldX: Float, layout: WorldLayout, viewWidth: Float): Float {
        val min = minCameraX(layout, viewWidth)
        val max = maxCameraX(layout, viewWidth)
        if (min > max) return (min + max) / 2f // viewport wider than the world: center it
        return heroWorldX.coerceIn(min, max)
    }

    /** Hero screen X as a fraction of the viewport width (≈[HERO_FRACTION], shifted by clamps). */
    fun heroScreenFraction(heroWorldX: Float, cameraX: Float, viewWidth: Float): Float =
        HERO_FRACTION + (heroWorldX - cameraX) / viewWidth

    /** World X → screen X as a fraction of the viewport width for a parallax [layer]. */
    fun screenFraction(worldX: Float, layer: SceneLayer, cameraX: Float, viewWidth: Float): Float =
        (HERO_FRACTION * viewWidth + (worldX - cameraX) * layer.parallax) / viewWidth
}
