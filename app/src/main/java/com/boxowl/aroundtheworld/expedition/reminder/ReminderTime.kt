package com.boxowl.aroundtheworld.expedition.reminder

import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

/** Next local 19:00 strictly after [now], recalculated after every delivery or clock change. */
object ReminderTime {
    private val hour = LocalTime.of(19, 0)

    fun next(now: Instant, zone: ZoneId): Instant {
        val today = now.atZone(zone).toLocalDate()
        val target = today.atTime(hour).atZone(zone).toInstant()
        return if (target.isAfter(now)) target else today.plusDays(1).atTime(hour).atZone(zone).toInstant()
    }
}
