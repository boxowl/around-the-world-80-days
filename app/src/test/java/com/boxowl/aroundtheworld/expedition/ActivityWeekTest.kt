package com.boxowl.aroundtheworld.expedition

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ActivityWeekTest {
    private val utc = ZoneId.of("UTC")

    @Test fun sevenDayWindowIncludesTodayAndExcludesOlderDay() {
        val expedition = expedition(
            startedAt = "2026-10-01T08:00:00Z",
            readAt = "2026-11-10T12:00:00Z",
            days = mapOf(
                LocalDate.parse("2026-11-03") to 1_000L,
                LocalDate.parse("2026-11-04") to 0L,
                LocalDate.parse("2026-11-10") to 230L,
            ),
        )

        val week = ActivityWeek.from(expedition, Instant.parse("2026-11-10T12:00:00Z"))
        assertEquals(LocalDate.parse("2026-11-04"), week.firstDay)
        assertEquals(LocalDate.parse("2026-11-10"), week.lastDay)
        assertEquals(7, week.windowDays)
        assertEquals(2, week.knownDays)
        assertEquals(230L, week.knownSteps)
    }

    @Test fun daysBeforeStartAreNotReportedAsMissingAndUnknownIsNotZero() {
        val started = expedition(
            startedAt = "2026-11-08T09:00:00Z",
            readAt = "2026-11-10T10:00:00Z",
            days = emptyMap(),
        )
        val empty = ActivityWeek.from(started, Instant.parse("2026-11-10T10:00:00Z"))
        assertEquals(3, empty.windowDays)
        assertEquals(0, empty.knownDays)
        assertNull(empty.knownSteps)

        val zero = ActivityWeek.from(
            started.copy(dailySteps = mapOf(LocalDate.parse("2026-11-09") to 0L)),
            Instant.parse("2026-11-10T10:00:00Z"),
        )
        assertEquals(1, zero.knownDays)
        assertEquals(0L, zero.knownSteps)
    }

    @Test fun windowUsesExpeditionZoneAcrossUtcMidnight() {
        val tokyo = ZoneId.of("Asia/Tokyo")
        val expedition = expedition(
            startedAt = "2026-11-09T00:00:00Z",
            readAt = "2026-11-09T15:30:00Z",
            zone = tokyo,
            days = mapOf(LocalDate.parse("2026-11-10") to 42L),
        )

        val week = ActivityWeek.from(expedition, Instant.parse("2026-11-09T15:30:00Z"))
        assertEquals(LocalDate.parse("2026-11-10"), week.lastDay)
        assertEquals(2, week.windowDays)
        assertEquals(42L, week.knownSteps)
    }

    @Test fun correctedDayReplacesItsOldValueInProjection() {
        val day = LocalDate.parse("2026-11-09")
        val original = expedition(
            startedAt = "2026-11-08T09:00:00Z",
            readAt = "2026-11-10T10:00:00Z",
            days = mapOf(day to 700L),
        )
        val now = Instant.parse("2026-11-10T10:00:00Z")

        assertEquals(700L, ActivityWeek.from(original, now).knownSteps)
        assertEquals(450L, ActivityWeek.from(original.copy(dailySteps = mapOf(day to 450L)), now).knownSteps)
    }

    private fun expedition(
        startedAt: String,
        readAt: String,
        days: Map<LocalDate, Long>,
        zone: ZoneId = utc,
    ) = Expedition(
        startedAt = Instant.parse(startedAt),
        zone = zone,
        mode = JourneyMode.FREE,
        dailySteps = days,
        lastReadAt = Instant.parse(readAt),
    )
}
