package com.boxowl.aroundtheworld.expedition.credit

/** Converts confirmed steps to estimated real metres for one calendar day. */
object StepDistance {
    const val DEFAULT_STRIDE_MILLIMETERS: Long = 750L

    fun realMeters(steps: Long, strideMillimeters: Long): Long {
        require(steps >= 0L) { "steps must be non-negative" }
        require(strideMillimeters > 0L) { "strideMillimeters must be positive" }
        return Math.multiplyExact(steps, strideMillimeters) / 1_000L
    }
}
