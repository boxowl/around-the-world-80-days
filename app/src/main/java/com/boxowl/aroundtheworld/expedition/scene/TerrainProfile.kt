package com.boxowl.aroundtheworld.expedition.scene

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Continuous walking surface (P07): the hero's feet, the ground fill and every
 * ground-standing object sample the same profile, so the hero never floats or
 * sinks. Each stop pins a level once; levels join by smoothstep (zero slope at
 * anchors), land segments add a gentle sinusoidal hill that vanishes at anchors
 * and tapers out next to flat stops (pier → deck plateau → Suez bank).
 */
internal object TerrainProfile {
    /** Ground level (canvas-height fraction, y grows downward) at each stop anchor. */
    val LEVELS = floatArrayOf(0.68f, 0.68f, 0.68f, 0.68f, 0.68f, 0.68f, 0.68f, 0.68f, 0.83f, 0.78f)

    private const val HILL_AMP = 0.008f
    private const val TAPER = 0.2f
    private const val TWO_PI = (2 * PI).toFloat()

    private fun smooth(t: Float) = t * t * (3f - 2f * t)
    private fun smoothDeriv(t: Float) = 6f * t * (1f - t)
    private fun isLandStop(index: Int) = LEVELS[index] == LEVELS[0]
    private fun hillsAllowed(segment: Int) = isLandStop(segment) && isLandStop(segment + 1)

    private fun envelope(layout: WorldLayout, segment: Int, t: Float): Float {
        val leftFull = segment > 0 && hillsAllowed(segment - 1)
        val rightFull = segment < layout.stopCount - 2 && hillsAllowed(segment + 1)
        val el = if (leftFull) 1f else smooth((t / TAPER).coerceIn(0f, 1f))
        val er = if (rightFull) 1f else smooth(((1f - t) / TAPER).coerceIn(0f, 1f))
        return el * er
    }

    private fun envelopeDeriv(layout: WorldLayout, segment: Int, t: Float): Float {
        val leftFull = segment > 0 && hillsAllowed(segment - 1)
        val rightFull = segment < layout.stopCount - 2 && hillsAllowed(segment + 1)
        val ul = (t / TAPER).coerceIn(0f, 1f)
        val ur = ((1f - t) / TAPER).coerceIn(0f, 1f)
        val el = if (leftFull) 1f else smooth(ul)
        val er = if (rightFull) 1f else smooth(ur)
        val del = if (leftFull || t >= TAPER) 0f else smoothDeriv(ul) / TAPER
        val der = if (rightFull || (1f - t) >= TAPER) 0f else -smoothDeriv(ur) / TAPER
        return del * er + el * der
    }

    private fun segmentAt(layout: WorldLayout, worldX: Float): Pair<Int, Float> {
        val pos = ((worldX - WORLD_MARGIN_START) / SEGMENT_LENGTH)
            .coerceIn(0f, (layout.stopCount - 1).toFloat())
        val segment = pos.toInt().coerceAtMost(layout.stopCount - 2)
        return segment to pos - segment
    }

    /** Ground level as a canvas-height fraction at [worldX]. */
    fun groundYAt(layout: WorldLayout, worldX: Float): Float {
        val (segment, t) = segmentAt(layout, worldX)
        val base = LEVELS[segment] + (LEVELS[segment + 1] - LEVELS[segment]) * smooth(t)
        if (!hillsAllowed(segment)) return base
        return base + HILL_AMP * sin(TWO_PI * t) * envelope(layout, segment, t)
    }

    /** d(groundY)/d(worldX) — analytic, continuous across anchors by construction. */
    fun groundSlopeAt(layout: WorldLayout, worldX: Float): Float {
        val (segment, t) = segmentAt(layout, worldX)
        val dBase = (LEVELS[segment + 1] - LEVELS[segment]) * smoothDeriv(t) / SEGMENT_LENGTH
        if (!hillsAllowed(segment)) return dBase
        val env = envelope(layout, segment, t)
        val dEnv = envelopeDeriv(layout, segment, t)
        return dBase +
            HILL_AMP * (TWO_PI * cos(TWO_PI * t) * env + sin(TWO_PI * t) * dEnv) / SEGMENT_LENGTH
    }
}
