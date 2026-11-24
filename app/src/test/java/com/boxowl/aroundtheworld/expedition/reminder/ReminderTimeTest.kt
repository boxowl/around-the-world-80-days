package com.boxowl.aroundtheworld.expedition.reminder

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class ReminderTimeTest {
    private val zone = ZoneId.of("Europe/Moscow")

    @Test fun nextIsStrictlyAfterNowAtLocalNineteen() {
        assertEquals(Instant.parse("2026-11-24T16:00:00Z"),
            ReminderTime.next(Instant.parse("2026-11-24T15:59:59Z"), zone))
        assertEquals(Instant.parse("2026-11-25T16:00:00Z"),
            ReminderTime.next(Instant.parse("2026-11-24T16:00:00Z"), zone))
    }

    @Test fun daylightSavingKeepsLocalHourAcrossShortAndLongDays() {
        val newYork = ZoneId.of("America/New_York")
        for (date in listOf(LocalDate.parse("2026-03-08"), LocalDate.parse("2026-11-01"))) {
            val before = date.minusDays(1).atTime(20, 0).atZone(newYork).toInstant()
            val next = ReminderTime.next(before, newYork).atZone(newYork)
            assertEquals(date, next.toLocalDate())
            assertEquals(LocalTime.of(19, 0), next.toLocalTime())
        }
    }
}
