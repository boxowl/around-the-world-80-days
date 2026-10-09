package com.boxowl.aroundtheworld.expedition.scene

import kotlin.math.abs
import kotlin.math.exp

/**
 * Hero stride cycle (P08). The phase is driven purely by world displacement —
 * when the world is still, the phase is frozen and the hero stands; walking
 * backwards (corrections) runs the phase in reverse. [gaitAt] is the smoothed
 * stride amplitude with ≈150 ms attack/decay, so the hero eases into and out
 * of the walk instead of snapping between poses.
 */
internal object WalkCycle {
    /** World distance of one full stride cycle (two steps). */
    const val STRIDE_WORLD = 0.045f

    /** World speed (units/second) at which the stride reaches full amplitude. */
    const val FULL_GAIT_SPEED = 0.10f

    private const val GAIT_TAU_SEC = 0.15f

    /** Phase after moving [deltaWorldX] world units; wraps into [0, 1). */
    fun advancePhase(phase: Float, deltaWorldX: Float): Float {
        val p = (phase + deltaWorldX / STRIDE_WORLD) % 1f
        return if (p < 0f) p + 1f else p
    }

    /** Smoothed stride amplitude in [0, 1] for the measured [worldSpeed]. */
    fun gaitAt(previous: Float, worldSpeed: Float, dtSec: Float): Float {
        if (dtSec <= 0f) return previous.coerceIn(0f, 1f)
        val target = (abs(worldSpeed) / FULL_GAIT_SPEED).coerceIn(0f, 1f)
        val k = 1f - exp(-dtSec / GAIT_TAU_SEC)
        return (previous + (target - previous) * k).coerceIn(0f, 1f)
    }
}
