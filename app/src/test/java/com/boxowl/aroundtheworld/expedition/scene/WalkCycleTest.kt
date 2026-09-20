package com.boxowl.aroundtheworld.expedition.scene

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * P08 contract tests for the hero stride cycle: the phase follows world
 * displacement monotonically in both directions, freezes in a still world, and
 * the gait amplitude rises with speed and decays to rest without overshoot.
 */
class WalkCycleTest {

    @Test fun phaseAdvancesForwardWithWorldMovement() {
        var phase = 0.2f
        var x = 0f
        var previous = phase
        repeat(50) {
            x += 0.003f
            phase = WalkCycle.advancePhase(phase, 0.003f)
            assertTrue(phase >= 0f && phase < 1f)
            // Monotonic modulo wrap: either grew or wrapped from near-1 to near-0.
            assertTrue(phase >= previous || (previous > 0.9f && phase < 0.1f))
            previous = phase
        }
    }

    @Test fun phaseRunsBackwardsOnCorrections() {
        var phase = 0.5f
        var previous = phase
        repeat(40) {
            phase = WalkCycle.advancePhase(phase, -0.004f)
            assertTrue(phase >= 0f && phase < 1f)
            assertTrue(phase <= previous || (previous < 0.1f && phase > 0.9f))
            previous = phase
        }
    }

    @Test fun fullStrideDistanceWrapsThePhaseOnce() {
        val phase = WalkCycle.advancePhase(0.3f, WalkCycle.STRIDE_WORLD)
        assertEquals(0.3f, phase, 1e-6f)
        val half = WalkCycle.advancePhase(0.3f, WalkCycle.STRIDE_WORLD / 2)
        assertEquals(0.8f, half, 1e-6f)
    }

    @Test fun stillWorldFreezesThePhase() {
        assertEquals(0.7f, WalkCycle.advancePhase(0.7f, 0f), 0f)
        assertEquals(0.0f, WalkCycle.advancePhase(0.0f, 0f), 0f)
    }

    @Test fun gaitRisesWithSpeedAndIsBounded() {
        var gait = 0f
        repeat(30) { gait = WalkCycle.gaitAt(gait, WalkCycle.FULL_GAIT_SPEED, 0.05f) }
        assertTrue("gait should approach 1 at full speed, was $gait", gait > 0.95f)
        assertTrue(gait <= 1f)
        var half = 0f
        repeat(30) { half = WalkCycle.gaitAt(half, WalkCycle.FULL_GAIT_SPEED / 2, 0.05f) }
        assertTrue(half in 0.4f..0.6f)
    }

    @Test fun gaitDecaysToRestInAStillWorld() {
        var gait = 1f
        repeat(40) { gait = WalkCycle.gaitAt(gait, 0f, 0.05f) }
        assertTrue("gait should decay to ~0, was $gait", gait < 0.01f)
        assertTrue(gait >= 0f)
    }

    @Test fun gaitAttackAndDecayAreGradual() {
        val step = WalkCycle.gaitAt(0f, WalkCycle.FULL_GAIT_SPEED, 0.016f)
        assertTrue("one frame must not jump to full gait", step < 0.2f)
        assertTrue(step > 0f)
    }
}
