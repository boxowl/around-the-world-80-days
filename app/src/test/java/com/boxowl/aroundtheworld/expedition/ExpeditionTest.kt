package com.boxowl.aroundtheworld.expedition

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class ExpeditionTest {
    private val start = Instant.parse("2026-09-13T07:00:00Z")
    private val zone = ZoneId.of("Europe/Moscow")
    private val date = LocalDate.of(2026, 9, 13)
    private fun expedition(mode: JourneyMode = JourneyMode.WAGER) = Expedition(start, zone, mode)

    @Test fun selectablePacesScaleEveryThresholdAndFinishFirstLegExactly() {
        PACES.forEach { pace ->
            val journey = Expedition(start, zone, JourneyMode.FREE, paceStepsPerDay = pace)
            assertEquals(pace * 80L, journey.worldGoal)
            assertEquals(pace * 7L, journey.firstLegGoal)
            assertEquals(0L, journey.stops.first().threshold)
            assertEquals(journey.firstLegGoal, journey.stops.last().threshold)
            assertEquals(FIRST_LEG.map { it.id }, journey.stops.map { it.id })
            assertTrue(journey.stops.zipWithNext().all { (a, b) -> a.threshold < b.threshold })
            journey.stops.drop(1).forEach { stop ->
                val before = journey.reconcile(mapOf(date to stop.threshold - 1), start.plusSeconds(1))
                assertEquals(stop.id, before.nextStop?.id)
                assertFalse(stop.id in before.unlocked)
                val at = before.reconcile(mapOf(date to stop.threshold), start.plusSeconds(2))
                assertTrue(stop.id in at.unlocked)
            }
        }
        assertEquals(714L, scaledThreshold(1_000L, 400_000L))
        assertEquals(1_429L, scaledThreshold(1_000L, 800_000L))
    }

    @Test fun customizedWagerUsesFixedGoalAndCorrectionKeepsDiary() {
        val journey = Expedition(start, zone, JourneyMode.WAGER, paceStepsPerDay = 5_000)
        val arrived = journey.reconcile(mapOf(date to 400_000L), start.plusSeconds(1))
        assertEquals(WagerStatus.WON, arrived.wagerStatus(start.plusSeconds(1)))
        val corrected = arrived.reconcile(mapOf(date to 34_999L), start.plusSeconds(2))
        assertEquals(WagerStatus.ACTIVE, corrected.wagerStatus(start.plusSeconds(2)))
        assertEquals("suez", corrected.nextStop?.id)
        assertTrue("suez" in corrected.unlocked)
        assertEquals(34_999L, corrected.firstLegSteps)
        assertEquals(35_000L, corrected.firstLegGoal)
        assertEquals(WagerStatus.NOT_APPLICABLE, corrected.copy(mode = JourneyMode.FREE).wagerStatus(start.plusSeconds(2)))
        assertThrows(IllegalArgumentException::class.java) { journey.copy(paceStepsPerDay = 6_000) }
        assertThrows(IllegalArgumentException::class.java) { journey.copy(worldGoal = 560_000L) }
    }

    @Test fun everyStopUnlocksExactlyAtItsThreshold() {
        val thresholds = listOf(
            "london" to 0L, "departure" to 1_000L, "dover" to 4_000L,
            "calais" to 7_000L, "paris" to 14_000L, "alps" to 21_000L,
            "turin" to 28_000L, "brindisi" to 35_000L,
            "mediterranean" to 42_000L, "suez" to 49_000L,
        )
        assertEquals(thresholds, FIRST_LEG.map { it.id to it.threshold })
        assertEquals(setOf("london"), expedition().unlocked)
        thresholds.drop(1).forEachIndexed { index, (id, threshold) ->
            val before = expedition().reconcile(mapOf(date to threshold - 1), start.plusSeconds(1))
            assertFalse("$id must remain locked before its threshold", id in before.unlocked)
            assertEquals(id, before.nextStop?.id)
            val at = before.reconcile(mapOf(date to threshold), start.plusSeconds(2))
            assertEquals(thresholds.take(index + 2).map { it.first }.toSet(), at.unlocked)
            assertEquals(thresholds.getOrNull(index + 2)?.first, at.nextStop?.id)
        }
    }

    @Test fun largeReadUnlocksEveryCrossedEventAndCapsOnlyFirstLegPosition() {
        val journey = expedition().reconcile(mapOf(date to 60_000L), start.plusSeconds(1))
        assertEquals(FIRST_LEG.map { it.id }.toSet(), journey.unlocked)
        assertEquals(60_000L, journey.totalSteps)
        assertEquals(49_000L, journey.firstLegSteps)
        assertNull(journey.nextStop)
        assertEquals(journey, journey.reconcile(mapOf(date to 60_000L), start.plusSeconds(1)))
    }

    @Test fun replacementsPreserveMissingDatesAndNeverAccumulateRepeatedReads() {
        val readAt = start.plus(Duration.ofDays(2))
        val first = expedition().reconcile(mapOf(date to 3_000L, date.plusDays(1) to 2_000L), readAt)
        val replacement = first.reconcile(mapOf(date to 3_500L), readAt.plusSeconds(1))
        assertEquals(5_500L, replacement.totalSteps)
        assertEquals(2_000L, replacement.dailySteps[date.plusDays(1)])
        val repeated = replacement.reconcile(mapOf(date to 3_500L), readAt.plusSeconds(2))
        assertEquals(5_500L, repeated.totalSteps)
        val zeroed = repeated.reconcile(mapOf(date to 0L), readAt.plusSeconds(3))
        assertEquals(2_000L, zeroed.totalSteps)
        assertEquals(zeroed.dailySteps, zeroed.reconcile(emptyMap(), readAt.plusSeconds(4)).dailySteps)
        assertEquals(5_000L, first.totalSteps)
    }

    @Test fun downwardCorrectionMovesPositionBackButKeepsDiary() {
        val arrived = expedition().reconcile(mapOf(date to 49_000L), start.plusSeconds(1))
        val corrected = arrived.reconcile(mapOf(date to 500L), start.plusSeconds(2))
        assertEquals(500L, corrected.firstLegSteps)
        assertEquals("departure", corrected.nextStop?.id)
        assertEquals(arrived.unlocked, corrected.unlocked)
        val erased = corrected.reconcile(mapOf(date to 0L), start.plusSeconds(3))
        assertEquals(0L, erased.totalSteps)
        assertEquals(arrived.unlocked, erased.unlocked)
    }

    @Test fun deadlineUsesEightyCalendarDatesAcrossSpringAndAutumnDst() {
        val london = ZoneId.of("Europe/London")
        val spring = Expedition(Instant.parse("2026-03-01T12:00:00Z"), london, JourneyMode.WAGER)
        assertEquals(Instant.parse("2026-05-19T23:00:00Z"), spring.deadline)
        assertEquals(1907L, Duration.between(spring.startedAt, spring.deadline).toHours())
        assertEquals(80L, spring.dayNumber(spring.deadline.minusSeconds(1)))
        assertEquals(81L, spring.dayNumber(spring.deadline))
        assertEquals(WagerStatus.ACTIVE, spring.wagerStatus(spring.deadline.minusNanos(1)))
        assertEquals(WagerStatus.EXPIRED, spring.wagerStatus(spring.deadline))
        val autumn = Expedition(Instant.parse("2026-10-01T11:00:00Z"), london, JourneyMode.WAGER)
        assertEquals(Instant.parse("2026-12-20T00:00:00Z"), autumn.deadline)
        assertEquals(1909L, Duration.between(autumn.startedAt, autumn.deadline).toHours())
    }

    @Test fun localMidnightAdvancesDayEvenWhenLessThanTwentyFourHoursPassed() {
        val journey = expedition()
        assertEquals(1L, journey.dayNumber(start.minusSeconds(1)))
        assertEquals(1L, journey.dayNumber(Instant.parse("2026-09-13T20:59:59Z")))
        assertEquals(2L, journey.dayNumber(Instant.parse("2026-09-13T21:00:00Z")))
    }

    @Test fun lateDataCanWinExpiredWagerButPostDeadlineStepsCannot() {
        val journey = expedition()
        val lastEligibleDate = date.plusDays(79)
        val firstIneligibleDate = date.plusDays(80)
        val lateReadAt = journey.deadline.plus(Duration.ofDays(2))
        val expired = journey.reconcile(
            mapOf(lastEligibleDate to WORLD_GOAL - 1, firstIneligibleDate to 1L), lateReadAt,
        )
        assertEquals(WORLD_GOAL, expired.totalSteps)
        assertEquals(WORLD_GOAL - 1, expired.wagerSteps)
        assertEquals(WagerStatus.EXPIRED, expired.wagerStatus(lateReadAt))
        val corrected = expired.reconcile(mapOf(lastEligibleDate to WORLD_GOAL), lateReadAt.plusSeconds(1))
        assertEquals(WagerStatus.WON, corrected.wagerStatus(lateReadAt.plusSeconds(1)))
        val removed = corrected.reconcile(mapOf(lastEligibleDate to WORLD_GOAL - 1), lateReadAt.plusSeconds(2))
        assertEquals(WagerStatus.EXPIRED, removed.wagerStatus(lateReadAt.plusSeconds(2)))
        assertEquals(corrected.unlocked, removed.unlocked)
    }

    @Test fun targetCanWinBeforeDeadlineAndFreeModeNeverHasWagerStatus() {
        val won = expedition().reconcile(mapOf(date to WORLD_GOAL), start.plusSeconds(1))
        assertEquals(WagerStatus.WON, won.wagerStatus(start.plusSeconds(1)))
        val free = expedition(JourneyMode.FREE)
        assertEquals(WagerStatus.NOT_APPLICABLE, free.wagerStatus(start))
        val continued = free.reconcile(mapOf(date.plusDays(100) to WORLD_GOAL), start.plus(Duration.ofDays(101)))
        assertEquals(WORLD_GOAL, continued.totalSteps)
        assertEquals(WagerStatus.NOT_APPLICABLE, continued.wagerStatus(continued.deadline.plus(Duration.ofDays(30))))
        assertTrue("suez" in continued.unlocked)
    }

    @Test fun invalidCountsDatesAndStaleReadsAreRejectedWithoutChangingSnapshot() {
        val readAt = start.plusSeconds(10)
        val journey = expedition().reconcile(mapOf(date to 4_000L), readAt)
        assertThrows(IllegalArgumentException::class.java) { journey.reconcile(mapOf(date to -1L), readAt) }
        assertThrows(IllegalArgumentException::class.java) { journey.reconcile(mapOf(date.minusDays(1) to 1L), readAt) }
        assertThrows(IllegalArgumentException::class.java) { journey.reconcile(mapOf(date.plusDays(1) to 1L), readAt) }
        assertThrows(IllegalArgumentException::class.java) { journey.reconcile(emptyMap(), start.minusNanos(1)) }
        assertThrows(IllegalArgumentException::class.java) { journey.reconcile(emptyMap(), readAt.minusNanos(1)) }
        assertEquals(4_000L, journey.totalSteps)
        assertEquals(readAt, journey.lastReadAt)
    }

    @Test fun overflowIsRejectedWhenCombiningIndividuallyValidDates() {
        val journey = expedition().reconcile(mapOf(date to Long.MAX_VALUE), start.plusSeconds(1))
        assertThrows(ArithmeticException::class.java) {
            journey.reconcile(mapOf(date.plusDays(1) to 1L), start.plus(Duration.ofDays(1)))
        }
        assertEquals(Long.MAX_VALUE, journey.totalSteps)
        assertEquals(49_000L, journey.firstLegSteps)
    }

    @Test fun restoredSnapshotsMustHaveValidVersionDiaryAndReadTime() {
        assertThrows(IllegalArgumentException::class.java) { expedition().copy(routeVersion = 2) }
        assertThrows(IllegalArgumentException::class.java) { expedition().copy(unlocked = setOf("london", "unknown")) }
        assertThrows(IllegalArgumentException::class.java) { expedition().copy(unlocked = emptySet()) }
        assertThrows(IllegalArgumentException::class.java) { expedition().copy(dailySteps = mapOf(date to 1L)) }
        assertThrows(IllegalArgumentException::class.java) { expedition().copy(lastReadAt = start.minusNanos(1)) }
        assertThrows(IllegalArgumentException::class.java) {
            expedition().copy(dailySteps = mapOf(date.plusDays(1) to 1L), lastReadAt = start)
        }
    }
}
