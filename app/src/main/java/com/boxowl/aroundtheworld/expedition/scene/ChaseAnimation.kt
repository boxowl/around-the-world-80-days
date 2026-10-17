package com.boxowl.aroundtheworld.expedition.scene

import kotlin.math.abs

/**
 * Time-boxed catch-up animation for the journey camera (P08, replacing the
 * unbounded exponential chase of P07). A plan is fixed when the target changes:
 * the animation starts at the currently displayed value and must reach the
 * target EXACTLY no later than [planDurationMs]. A new target discards the old
 * plan and starts a fresh one from wherever the display currently is.
 */
internal object ChaseAnimation {
    const val MAX_DURATION_MS = 2_000L
    const val BASE_MS = 250L
    private const val PER_POSITION_MS = 1_750f

    /** Settle duration: 250 ms + 1.75 s per route position, capped at 2 s. */
    fun planDurationMs(distance: Float): Long =
        (BASE_MS + distance * PER_POSITION_MS).toLong().coerceAtMost(MAX_DURATION_MS)

    /**
     * Value of a plan from [from] to [target] after [elapsedMs]. Eases with a
     * cubic-out curve; at and past the deadline returns [target] EXACTLY, so
     * the camera never settles asymptotically short of the confirmed position.
     */
    fun chaseValueAt(from: Float, target: Float, elapsedMs: Long): Float {
        if (from == target) return target
        val duration = planDurationMs(abs(target - from))
        if (elapsedMs >= duration) return target
        if (elapsedMs <= 0L) return from
        val t = elapsedMs.toFloat() / duration
        val eased = 1f - (1f - t) * (1f - t) * (1f - t)
        return from + (target - from) * eased
    }
}
