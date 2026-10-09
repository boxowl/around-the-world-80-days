package com.boxowl.aroundtheworld.expedition.credit

import java.time.LocalDate

/**
 * Снимок подтверждённых дневных зачётов многодневной экспедиции v2.
 *
 * Состояние — карта LocalDate → подтверждённые реальные метры за день. Каждое
 * значение — заменяющий агрегат, никогда не приращение. Итоги ([totalRealMeters],
 * [totalBookMeters]) — ВСЕГДА производные суммы через addExact/multiplyExact,
 * без накопителя: повторное чтение тех же значений даёт равный снимок
 * (data class equality), двойное начисление невозможно.
 *
 * Монотонность не требуется: поздняя корректировка заменяет значение дня целиком,
 * позиция может как вырасти, так и откатиться (явный 0 — законная замена).
 * Модуль не моделирует открытые истории/штампы и не распоряжается ими — это
 * задача V2.2b; он отвечает только за арифметику зачёта.
 */
data class CreditLedger(val confirmedMetersByDay: Map<LocalDate, Long> = emptyMap()) {
    init {
        require(confirmedMetersByDay.values.all { it >= 0L }) { "Confirmed meters must be non-negative" }
        totalBookMeters // Раннее обнаружение переполнения до принятия или сохранения снимка.
    }

    /** Сумма подтверждённых реальных метров по всем дням; переполнение — ArithmeticException. */
    val totalRealMeters: Long get() = confirmedMetersByDay.values.fold(0L, Math::addExact)

    /** Книжный итог = [totalRealMeters] × [BOOK_METERS_PER_REAL_METER], точно и без округления. */
    val totalBookMeters: Long get() = Math.multiplyExact(totalRealMeters, BOOK_METERS_PER_REAL_METER)

    /**
     * Зачёт одного дня. null — день неизвестен (нет подтверждённого значения);
     * DayCredit(0) — подтверждённый ноль. Эти случаи различны по контракту.
     */
    fun creditForDay(date: LocalDate): DayCredit? = confirmedMetersByDay[date]?.let(::DayCredit)

    /**
     * Частичная замена дневных агрегатов (семантика Map.plus): даты, отсутствующие
     * в [replacements], остаются без изменений; переданное значение заменяет значение
     * дня целиком — в большую или меньшую сторону, включая явный 0.
     * Отрицательные значения отвергаются (IllegalArgumentException из init).
     */
    fun reconcile(replacements: Map<LocalDate, Long>): CreditLedger =
        CreditLedger(confirmedMetersByDay + replacements)
}
