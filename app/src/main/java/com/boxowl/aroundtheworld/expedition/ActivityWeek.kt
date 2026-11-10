package com.boxowl.aroundtheworld.expedition

import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Saved, confirmed v1 steps in the seven calendar days ending today in the expedition zone. */
internal data class ActivityWeek(
    val firstDay: LocalDate,
    val lastDay: LocalDate,
    val windowDays: Int,
    val knownDays: Int,
    val knownSteps: Long?,
) {
    companion object {
        fun from(expedition: Expedition, now: Instant): ActivityWeek {
            val today = now.atZone(expedition.zone).toLocalDate()
            val first = maxOf(expedition.startDate, today.minusDays(6))
            val windowDays = if (first > today) 0 else ChronoUnit.DAYS.between(first, today).toInt() + 1
            var knownDays = 0
            var knownSteps = 0L
            repeat(windowDays) { offset ->
                expedition.dailySteps[first.plusDays(offset.toLong())]?.let { steps ->
                    knownDays++
                    knownSteps = Math.addExact(knownSteps, steps)
                }
            }
            return ActivityWeek(first, today, windowDays, knownDays, knownSteps.takeIf { knownDays > 0 })
        }
    }
}
