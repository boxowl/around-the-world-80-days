package com.boxowl.aroundtheworld.expedition.credit

import com.boxowl.aroundtheworld.expedition.JourneyMode
import com.boxowl.aroundtheworld.expedition.WagerStatus
import com.boxowl.aroundtheworld.expedition.passport.PassportV1
import com.boxowl.aroundtheworld.expedition.passport.StampId
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** Calendar outcome from persisted daily credits; later reads can revise past days. */
object V2WagerCalendar {
    /** Same exact geometric finish as the final passport stamp; UI rounds it to 40 502 km. */
    private val finishBookMeters = PassportV1.thresholdBookMeters(StampId.LONDON_FINISH)

    data class Result(
        val dayNumber: Long,
        val deadline: Instant?,
        val wagerBookMeters: Long,
        val status: WagerStatus,
    )

    fun evaluate(
        startedAt: Instant,
        zone: ZoneId,
        mode: JourneyMode,
        ledger: CreditLedger,
        now: Instant,
    ): Result {
        val startDate = startedAt.atZone(zone).toLocalDate()
        val endDate = startDate.plusDays(80)
        val deadline = endDate.atStartOfDay(zone).toInstant()
        val dayNumber = (ChronoUnit.DAYS.between(startDate, now.atZone(zone).toLocalDate()) + 1)
            .coerceAtLeast(1)
        val wagerRealMeters = ledger.confirmedMetersByDay
            .filterKeys { it >= startDate && it < endDate }
            .values.fold(0L, Math::addExact)
        val wagerBookMeters = Math.multiplyExact(wagerRealMeters, BOOK_METERS_PER_REAL_METER)
        val status = when {
            mode == JourneyMode.FREE -> WagerStatus.NOT_APPLICABLE
            wagerBookMeters.toDouble() >= finishBookMeters -> WagerStatus.WON
            now >= deadline -> WagerStatus.EXPIRED
            else -> WagerStatus.ACTIVE
        }
        return Result(dayNumber, if (mode == JourneyMode.WAGER) deadline else null, wagerBookMeters, status)
    }
}
