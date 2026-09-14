package com.boxowl.aroundtheworld.expedition

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FirstLegMapProjectionTest {
    private val started = Instant.parse("2026-09-15T08:00:00Z")
    private val date = LocalDate.parse("2026-09-15")
    private fun expedition(pace: Int = BASE_PACE) = Expedition(
        startedAt = started, zone = ZoneId.of("UTC"), mode = JourneyMode.FREE,
        paceStepsPerDay = pace,
    )
    private fun withSteps(base: Expedition, steps: Long) = base.reconcile(
        mapOf(date to steps), started.plusSeconds(60),
    )

    @Test fun missingAndConfirmedZeroDiffer() {
        val unknown = FirstLegMapProjection.from(expedition())
        assertNull(unknown.knownSteps)
        assertEquals(MapStopState.CURRENT, unknown.stops.first().state)
        val zero = FirstLegMapProjection.from(withSteps(expedition(), 0))
        assertEquals(0L, zero.knownSteps)
        assertEquals(1, zero.nextIndex)
    }

    @Test fun eachScaledThresholdMovesTheCurrentStopAtEveryPace() {
        for (pace in PACES) {
            val base = expedition(pace)
            val route = base.stops
            for (index in 1 until route.size) {
                val before = FirstLegMapProjection.from(withSteps(base, route[index].threshold - 1))
                assertEquals(index - 1, before.currentIndex)
                assertEquals(index, before.nextIndex)
                val at = FirstLegMapProjection.from(withSteps(base, route[index].threshold))
                assertEquals(index, at.currentIndex)
                assertTrue(at.stops[index].diaryAvailable)
            }
        }
    }

    @Test fun travelerInterpolatesAndStopsAtSuez() {
        val base = expedition()
        val between = FirstLegMapProjection.from(withSteps(base, 2_500))
        assertEquals(1, between.currentIndex)
        assertEquals(2, between.nextIndex)
        assertEquals(.5f, between.fractionToNext, .001f)
        val beyond = FirstLegMapProjection.from(withSteps(base, 70_000))
        assertEquals(9, beyond.currentIndex)
        assertNull(beyond.nextIndex)
        assertEquals(base.firstLegGoal, beyond.goal)
    }

    @Test fun rollbackMovesTravelerWithoutClosingKnownEntries() {
        val all = withSteps(expedition(), 49_000)
        val corrected = all.reconcile(mapOf(date to 500), started.plusSeconds(120))
        val map = FirstLegMapProjection.from(corrected)
        assertEquals(0, map.currentIndex)
        assertEquals(1, map.nextIndex)
        assertEquals(MapStopState.REMEMBERED, map.stops.last().state)
        assertTrue(map.stops.all { it.diaryAvailable })
    }
}
