package com.boxowl.aroundtheworld.expedition.scene

import com.boxowl.aroundtheworld.expedition.frac
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Continuous walking surface (P07, relief enriched in P08): the hero's feet,
 * the ground fill and every ground-standing object sample the same profile, so
 * the hero never floats or sinks. Each stop pins a level once; levels join by
 * smoothstep (zero slope at anchors). Land segments add multi-frequency hills
 * (three integer harmonics with per-segment amplitude jitter) that vanish —
 * value AND slope — near every anchor, so stops stay on calm flat ground and
 * groundYAt/groundSlopeAt remain analytic and continuous everywhere.
 */
internal object TerrainProfile {
    /** Ground level (canvas-height fraction, y grows downward) at each stop anchor. */
    val LEVELS = floatArrayOf(0.68f, 0.68f, 0.68f, 0.68f, 0.68f, 0.68f, 0.68f, 0.68f, 0.83f, 0.78f)

    private const val HILL_AMP1 = 0.014f
    private const val HILL_AMP2 = 0.0065f
    private const val HILL_AMP3 = 0.003f
    private const val TAPER = 0.18f
    private const val TWO_PI = (2 * PI).toFloat()

    private fun smooth(t: Float) = t * t * (3f - 2f * t)
    private fun smoothDeriv(t: Float) = 6f * t * (1f - t)
    private fun isLandStop(index: Int) = LEVELS[index] == LEVELS[0]
    private fun hillsAllowed(segment: Int) = isLandStop(segment) && isLandStop(segment + 1)

    /** Per-segment amplitude jitter, stable for a given segment and harmonic. */
    private fun ampJitter(segment: Int, harmonic: Int): Float =
        0.8f + 0.35f * frac(sin(segment * 17.31 + harmonic * 7.77) * 43758.5453)

    /** Envelope vanishing with zero slope at both segment ends. */
    private fun envelope(t: Float): Float =
        smooth((t / TAPER).coerceIn(0f, 1f)) * smooth(((1f - t) / TAPER).coerceIn(0f, 1f))

    private fun envelopeDeriv(t: Float): Float {
        val ul = (t / TAPER).coerceIn(0f, 1f)
        val ur = ((1f - t) / TAPER).coerceIn(0f, 1f)
        val del = if (t >= TAPER) 0f else smoothDeriv(ul) / TAPER
        val der = if ((1f - t) >= TAPER) 0f else -smoothDeriv(ur) / TAPER
        return del * smooth(ur) + smooth(ul) * der
    }

    private fun hills(segment: Int, t: Float): Float =
        envelope(t) * (
            HILL_AMP1 * ampJitter(segment, 1) * sin(TWO_PI * t) +
                HILL_AMP2 * ampJitter(segment, 2) * sin(2f * TWO_PI * t) +
                HILL_AMP3 * ampJitter(segment, 3) * sin(3f * TWO_PI * t)
            )

    private fun hillsDeriv(segment: Int, t: Float): Float {
        val env = envelope(t)
        val dEnv = envelopeDeriv(t)
        val v = HILL_AMP1 * ampJitter(segment, 1) * sin(TWO_PI * t) +
            HILL_AMP2 * ampJitter(segment, 2) * sin(2f * TWO_PI * t) +
            HILL_AMP3 * ampJitter(segment, 3) * sin(3f * TWO_PI * t)
        val dv = HILL_AMP1 * ampJitter(segment, 1) * TWO_PI * cos(TWO_PI * t) +
            HILL_AMP2 * ampJitter(segment, 2) * 2f * TWO_PI * cos(2f * TWO_PI * t) +
            HILL_AMP3 * ampJitter(segment, 3) * 3f * TWO_PI * cos(3f * TWO_PI * t)
        return dEnv * v + env * dv
    }

    private fun segmentAt(layout: WorldLayout, worldX: Float): Pair<Int, Float> {
        val pos = ((worldX - WORLD_MARGIN_START) / SEGMENT_LENGTH)
            .coerceIn(0f, (layout.stopCount - 1).toFloat())
        val segment = pos.toInt().coerceAtMost(layout.stopCount - 2)
        return segment to (pos - segment).coerceIn(0f, 1f)
    }

    /** Ground level as a canvas-height fraction at [worldX]. */
    fun groundYAt(layout: WorldLayout, worldX: Float): Float {
        val (segment, t) = segmentAt(layout, worldX)
        val base = LEVELS[segment] + (LEVELS[segment + 1] - LEVELS[segment]) * smooth(t)
        if (!hillsAllowed(segment)) return base
        return base + hills(segment, t)
    }

    /** d(groundY)/d(worldX) — analytic, continuous across anchors by construction. */
    fun groundSlopeAt(layout: WorldLayout, worldX: Float): Float {
        val (segment, t) = segmentAt(layout, worldX)
        val dBase = (LEVELS[segment + 1] - LEVELS[segment]) * smoothDeriv(t) / SEGMENT_LENGTH
        if (!hillsAllowed(segment)) return dBase
        return dBase + hillsDeriv(segment, t) / SEGMENT_LENGTH
    }
}
