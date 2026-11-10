package com.boxowl.aroundtheworld.expedition.path

/** V2 illumination from the displayed estimated real distance, never the wall clock. */
object DistanceLighting {
    private const val PERIOD_KM = 30.0
    private const val DUSK_KM = 20.0
    private const val HALF_TRANSITION_KM = 0.5

    /** 0 is fully light, 1 is fully dark; boundaries at 20 and 30 km are transition midpoints. */
    fun darknessAtRealKm(realKm: Double): Double {
        require(realKm.isFinite() && realKm >= 0.0) { "realKm must be finite and non-negative" }
        val phase = realKm % PERIOD_KM
        return when {
            phase < HALF_TRANSITION_KM -> 1.0 - smoothstep((phase + HALF_TRANSITION_KM) / 1.0)
            phase < DUSK_KM - HALF_TRANSITION_KM -> 0.0
            phase <= DUSK_KM + HALF_TRANSITION_KM ->
                smoothstep((phase - (DUSK_KM - HALF_TRANSITION_KM)) / 1.0)
            phase < PERIOD_KM - HALF_TRANSITION_KM -> 1.0
            else -> 1.0 - smoothstep((phase - (PERIOD_KM - HALF_TRANSITION_KM)) / 1.0)
        }
    }

    private fun smoothstep(t: Double): Double = t * t * (3.0 - 2.0 * t)
}
