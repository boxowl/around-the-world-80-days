package com.boxowl.aroundtheworld.expedition.scene

import com.boxowl.aroundtheworld.expedition.frac
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Continuous walking surface (P07, relief enriched in P08): the hero's feet,
 * the ground fill and every ground-standing object sample the same profile, so
 * the hero never floats or sinks. Each stop pins a level once. Land segments
 * add multi-frequency hills (three integer harmonics with per-segment amplitude
 * jitter) that vanish — value AND slope — near every anchor. Level changes are
 * localized ledges (P08): the Brindisi pier stays flat stone until a short
 * gangway ramp climbs to deck level right before a8; the deck is truly flat
 * until a short lip steps down onto the Suez bank at a9. [supportAt] classifies
 * what the hero stands on, so the stride can stop aboard the ship.
 */
internal object TerrainProfile {
    /** Ground level (canvas-height fraction, y grows downward) at each stop anchor. */
    val LEVELS = floatArrayOf(0.68f, 0.68f, 0.68f, 0.68f, 0.68f, 0.68f, 0.68f, 0.68f, 0.83f, 0.78f)

    /** What the hero stands on at a world position (P08). */
    enum class Support { LAND, PIER_RAMP, DECK, BANK }

    private const val LAND_LEVEL = 0.68f
    private const val DECK_LEVEL = 0.83f

    /** Gangway length in world units; the ramp ends exactly at the deck anchor. */
    const val RAMP_LEN = 0.5f

    /** Fraction of the sea segment the deck stays flat before the lip down to the bank. */
    private const val DECK_FLIP_T = 0.85f

    private const val HILL_AMP1 = 0.014f
    private const val HILL_AMP2 = 0.0065f
    private const val HILL_AMP3 = 0.003f
    private const val TAPER = 0.18f
    private const val TWO_PI = (2 * PI).toFloat()

    /** Anchor index where the deck level starts (Brindisi → Mediterranean). */
    private fun deckAnchor(): Int = LEVELS.indexOfFirst { it == DECK_LEVEL }

    /** World X where the boarding gangway starts climbing. */
    fun rampStartX(layout: WorldLayout): Float = layout.anchorX(deckAnchor()) - RAMP_LEN

    fun supportAt(layout: WorldLayout, worldX: Float): Support {
        val deckStart = layout.anchorX(deckAnchor())
        val bankStart = layout.anchorX(deckAnchor() + 1)
        return when {
            worldX >= bankStart -> Support.BANK
            worldX >= deckStart -> Support.DECK
            worldX >= deckStart - RAMP_LEN -> Support.PIER_RAMP
            else -> Support.LAND
        }
    }

    private fun smooth(t: Float) = t * t * (3f - 2f * t)
    private fun smoothDeriv(t: Float) = 6f * t * (1f - t)
    private fun isLandStop(index: Int) = LEVELS[index] == LAND_LEVEL
    private fun hillsAllowed(segment: Int) = isLandStop(segment) && isLandStop(segment + 1)

    /**
     * Localized level change: flat until [t0], then a smoothstep to the next
     * level. Both value and slope vanish at t0, keeping the profile C1.
     */
    private fun ledge(t: Float, t0: Float): Float =
        if (t <= t0) 0f else smooth(((t - t0) / (1f - t0)).coerceIn(0f, 1f))

    private fun ledgeDeriv(t: Float, t0: Float): Float =
        if (t <= t0 || t >= 1f) 0f else smoothDeriv((t - t0) / (1f - t0)) / (1f - t0)

    /** Ledge start for a level-changing segment; -1 when the segment is flat. */
    private fun ledgeStart(segment: Int): Float = when {
        LEVELS[segment] == LEVELS[segment + 1] -> -1f
        LEVELS[segment + 1] == DECK_LEVEL -> 1f - RAMP_LEN / SEGMENT_LENGTH // pier → gangway
        else -> DECK_FLIP_T // deck → bank lip
    }

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
        val t0 = ledgeStart(segment)
        val base = if (t0 < 0f) LEVELS[segment]
            else LEVELS[segment] + (LEVELS[segment + 1] - LEVELS[segment]) * ledge(t, t0)
        if (!hillsAllowed(segment)) return base
        return base + hills(segment, t)
    }

    /** d(groundY)/d(worldX) — analytic, continuous across anchors by construction. */
    fun groundSlopeAt(layout: WorldLayout, worldX: Float): Float {
        val (segment, t) = segmentAt(layout, worldX)
        val t0 = ledgeStart(segment)
        val dBase = if (t0 < 0f) 0f
            else (LEVELS[segment + 1] - LEVELS[segment]) * ledgeDeriv(t, t0) / SEGMENT_LENGTH
        if (!hillsAllowed(segment)) return dBase
        return dBase + hillsDeriv(segment, t) / SEGMENT_LENGTH
    }
}
