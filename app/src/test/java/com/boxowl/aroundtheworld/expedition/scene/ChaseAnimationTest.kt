package com.boxowl.aroundtheworld.expedition.scene

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * P08 contract tests for the time-boxed camera catch-up: the animation must
 * finish exactly at the target within the planned duration (≤ 2 s), ease
 * monotonically in both directions, and replan correctly from any displayed
 * mid-flight value.
 */
class ChaseAnimationTest {

    @Test fun durationIsCappedAtTwoSeconds() {
        assertTrue(ChaseAnimation.planDurationMs(100f) <= ChaseAnimation.MAX_DURATION_MS)
        assertEquals(ChaseAnimation.MAX_DURATION_MS, ChaseAnimation.planDurationMs(100f))
        assertEquals(ChaseAnimation.MAX_DURATION_MS, ChaseAnimation.planDurationMs(3f))
    }

    @Test fun smallStepSettlesQuickly() {
        assertEquals(250L + (0.01f * 1750f).toLong(), ChaseAnimation.planDurationMs(0.01f))
        assertTrue(ChaseAnimation.planDurationMs(0.01f) < 500L)
        assertTrue(ChaseAnimation.planDurationMs(0.5f) < 2000L)
    }

    @Test fun zeroDistanceIsInstant() {
        assertEquals(ChaseAnimation.BASE_MS, ChaseAnimation.planDurationMs(0f))
        assertEquals(1.5f, ChaseAnimation.chaseValueAt(1.5f, 1.5f, 0L), 0f)
        assertEquals(1.5f, ChaseAnimation.chaseValueAt(1.5f, 1.5f, 5_000L), 0f)
    }

    @Test fun finishesExactlyAtTheDeadline() {
        for ((from, target) in listOf(0f to 3f, 3f to 0f, 6.5f to 3.5f, 0f to 9f)) {
            val duration = ChaseAnimation.planDurationMs(abs(target - from))
            assertEquals(target, ChaseAnimation.chaseValueAt(from, target, duration), 0f)
            assertEquals(target, ChaseAnimation.chaseValueAt(from, target, duration + 10_000L), 0f)
        }
    }

    @Test fun startsExactlyAtTheDisplayedValue() {
        assertEquals(2.3f, ChaseAnimation.chaseValueAt(2.3f, 5f, 0L), 0f)
        assertEquals(2.3f, ChaseAnimation.chaseValueAt(2.3f, 0f, 0L), 0f)
    }

    @Test fun forwardRunIsMonotone() {
        val duration = ChaseAnimation.planDurationMs(4f)
        var previous = -1f
        var elapsed = 0L
        while (elapsed <= duration) {
            val value = ChaseAnimation.chaseValueAt(0f, 4f, elapsed)
            assertTrue("not monotone at $elapsed ms", value >= previous)
            assertTrue(value in 0f..4f)
            previous = value
            elapsed += 37L
        }
        assertEquals(4f, ChaseAnimation.chaseValueAt(0f, 4f, duration), 0f)
    }

    @Test fun backwardRunIsMonotone() {
        val duration = ChaseAnimation.planDurationMs(3f)
        var previous = Float.MAX_VALUE
        var elapsed = 0L
        while (elapsed <= duration) {
            val value = ChaseAnimation.chaseValueAt(6.5f, 3.5f, elapsed)
            assertTrue("not monotone at $elapsed ms", value <= previous)
            assertTrue(value in 3.5f..6.5f)
            previous = value
            elapsed += 41L
        }
        assertEquals(3.5f, ChaseAnimation.chaseValueAt(6.5f, 3.5f, duration), 0f)
    }

    @Test fun backwardIsTheMirrorOfForward() {
        for (elapsed in listOf(0L, 100L, 500L, 900L, 1500L)) {
            val forward = ChaseAnimation.chaseValueAt(0f, 4f, elapsed)
            val backward = ChaseAnimation.chaseValueAt(4f, 0f, elapsed)
            assertEquals(forward, 4f - backward, 1e-6f)
        }
    }

    @Test fun replanFromMidFlightStillFinishesExactly() {
        // A plan was heading 0 → 9; at 600 ms a new target 2 arrives. The new
        // plan starts at the displayed value and ends exactly at 2.
        val displayed = ChaseAnimation.chaseValueAt(0f, 9f, 600L)
        assertTrue(displayed in 0f..9f)
        val duration = ChaseAnimation.planDurationMs(abs(2f - displayed))
        assertTrue(duration <= ChaseAnimation.MAX_DURATION_MS)
        assertEquals(2f, ChaseAnimation.chaseValueAt(displayed, 2f, duration), 0f)
        var elapsed = 0L
        var previous = Float.MAX_VALUE
        while (elapsed <= duration) {
            val value = ChaseAnimation.chaseValueAt(displayed, 2f, elapsed)
            assertTrue(value <= previous)
            previous = value
            elapsed += 53L
        }
    }
}
