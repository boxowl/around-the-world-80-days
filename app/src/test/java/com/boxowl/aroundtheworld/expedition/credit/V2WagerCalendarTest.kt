package com.boxowl.aroundtheworld.expedition.credit

import com.boxowl.aroundtheworld.expedition.JourneyMode
import com.boxowl.aroundtheworld.expedition.WagerStatus
import com.boxowl.aroundtheworld.expedition.passport.PassportV1
import com.boxowl.aroundtheworld.expedition.passport.StampId
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.ceil
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class V2WagerCalendarTest {
    private val zone = ZoneId.of("Europe/Moscow")
    private val started = Instant.parse("2026-11-10T19:30:00Z") // 22:30, Moscow.
    private val startDate = LocalDate.of(2026, 11, 10)
    private val deadline = startDate.plusDays(80).atStartOfDay(zone).toInstant()

    @Test fun firstDayEndsAtNearestMidnightAndDay80EndsAtDeadline() {
        val empty = CreditLedger.of()
        assertEquals(1L, V2WagerCalendar.evaluate(started, zone, JourneyMode.WAGER, empty, started).dayNumber)
        assertEquals(2L, V2WagerCalendar.evaluate(started, zone, JourneyMode.WAGER, empty,
            Instant.parse("2026-11-10T21:00:00Z")).dayNumber)
        assertEquals(80L, V2WagerCalendar.evaluate(started, zone, JourneyMode.WAGER, empty,
            deadline.minusNanos(1)).dayNumber)
        assertEquals(WagerStatus.ACTIVE, V2WagerCalendar.evaluate(started, zone, JourneyMode.WAGER,
            empty, deadline.minusNanos(1)).status)
        assertEquals(WagerStatus.EXPIRED, V2WagerCalendar.evaluate(started, zone, JourneyMode.WAGER,
            empty, deadline).status)
        assertEquals(deadline, V2WagerCalendar.evaluate(started, zone, JourneyMode.WAGER, empty, started).deadline)
    }

    @Test fun lateCorrectionInsideWindowCanChangeExpiredToWon() {
        val firstWinningRealMeters = ceil(PassportV1.thresholdBookMeters(StampId.LONDON_FINISH) /
            BOOK_METERS_PER_REAL_METER).toLong()
        val before = CreditLedger.of(mapOf(startDate to firstWinningRealMeters - 1L))
        val after = before.reconcile(mapOf(startDate to firstWinningRealMeters))
        assertEquals((firstWinningRealMeters - 1L) * BOOK_METERS_PER_REAL_METER,
            V2WagerCalendar.evaluate(started, zone, JourneyMode.WAGER, before, deadline).wagerBookMeters)
        assertEquals(WagerStatus.EXPIRED, V2WagerCalendar.evaluate(started, zone, JourneyMode.WAGER,
            before, deadline).status)
        assertEquals(WagerStatus.WON, V2WagerCalendar.evaluate(started, zone, JourneyMode.WAGER,
            after, deadline.plusSeconds(100)).status)
        assertEquals(WagerStatus.EXPIRED, V2WagerCalendar.evaluate(started, zone, JourneyMode.WAGER,
            after.reconcile(mapOf(startDate to 0L)), deadline.plusSeconds(100)).status)
    }

    @Test fun daysAfterWindowAddToPathButNotWager() {
        val ledger = CreditLedger.of(mapOf(startDate.plusDays(80) to 2_100_000L))
        val result = V2WagerCalendar.evaluate(started, zone, JourneyMode.WAGER, ledger, deadline)
        assertEquals(42_000_000L, ledger.totalBookMeters)
        assertEquals(0L, result.wagerBookMeters)
        assertEquals(WagerStatus.EXPIRED, result.status)
    }

    @Test fun freeJourneyHasNoDeadlineOrWagerOutcome() {
        val result = V2WagerCalendar.evaluate(started, zone, JourneyMode.FREE, CreditLedger.of(), deadline)
        assertNull(result.deadline)
        assertEquals(WagerStatus.NOT_APPLICABLE, result.status)
    }

    @Test fun fixedZoneAndDstDetermineCalendarDays() {
        val newYork = ZoneId.of("America/New_York")
        val start = Instant.parse("2026-03-07T23:30:00Z")
        val midnight = LocalDate.of(2026, 3, 8).atStartOfDay(newYork).toInstant()
        val result = V2WagerCalendar.evaluate(start, newYork, JourneyMode.WAGER, CreditLedger.of(), midnight)
        assertEquals(2L, result.dayNumber)
        assertEquals(LocalDate.of(2026, 3, 7).plusDays(80).atStartOfDay(newYork).toInstant(), result.deadline)
    }
}
