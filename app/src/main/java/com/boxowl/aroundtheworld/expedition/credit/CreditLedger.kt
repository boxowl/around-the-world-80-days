package com.boxowl.aroundtheworld.expedition.credit

import java.time.LocalDate
import java.util.Collections

/**
 * Снимок подтверждённых дневных зачётов многодневной экспедиции v2.
 *
 * Состояние — карта LocalDate → подтверждённые реальные метры за день. Каждое
 * значение — заменяющий агрегат, никогда не приращение. Итоги ([totalRealMeters],
 * [totalBookMeters]) — ВСЕГДА производные суммы через addExact/multiplyExact,
 * без накопителя: повторное чтение тех же значений даёт равный снимок
 * (equality по содержимому дней), двойное начисление невозможно.
 *
 * Снимок глубоко неизменяем: [of] и [reconcile] делают защитную неизменяемую
 * копию переданной карты. Мутация исходной карты вызывающего кода после создания
 * не меняет ни итоги, ни equality уже созданного снимка и не обходит раннюю
 * проверку переполнения в init.
 *
 * Монотонность не требуется: поздняя корректировка заменяет значение дня целиком,
 * позиция может как вырасти, так и откатиться (явный 0 — законная замена).
 * Модуль не моделирует открытые истории/штампы и не распоряжается ими — это
 * задача V2.2b; он отвечает только за арифметику зачёта.
 */
class CreditLedger private constructor(private val days: Map<LocalDate, Long>) {
    init {
        require(days.values.all { it >= 0L }) { "Confirmed meters must be non-negative" }
        totalBookMeters // Раннее обнаружение переполнения до принятия или сохранения снимка.
    }

    /** День → подтверждённые реальные метры; неизменяемое представление внутреннего состояния. */
    val confirmedMetersByDay: Map<LocalDate, Long>
        get() = days

    /** Сумма подтверждённых реальных метров по всем дням; переполнение — ArithmeticException. */
    val totalRealMeters: Long get() = days.values.fold(0L, Math::addExact)

    /** Книжный итог = [totalRealMeters] × [BOOK_METERS_PER_REAL_METER], точно и без округления. */
    val totalBookMeters: Long get() = Math.multiplyExact(totalRealMeters, BOOK_METERS_PER_REAL_METER)

    /**
     * Зачёт одного дня. null — день неизвестен (нет подтверждённого значения);
     * DayCredit(0) — подтверждённый ноль. Эти случаи различны по контракту.
     */
    fun creditForDay(date: LocalDate): DayCredit? = days[date]?.let(::DayCredit)

    /**
     * Частичная замена дневных агрегатов (семантика Map.plus): даты, отсутствующие
     * в [replacements], остаются без изменений; переданное значение заменяет значение
     * дня целиком — в большую или меньшую сторону, включая явный 0.
     * Отрицательные значения отвергаются (IllegalArgumentException из init).
     * Результат изолирован от последующих мутаций [replacements].
     */
    fun reconcile(replacements: Map<LocalDate, Long>): CreditLedger =
        CreditLedger(Collections.unmodifiableMap(days + replacements))

    override fun equals(other: Any?): Boolean = other is CreditLedger && days == other.days

    override fun hashCode(): Int = days.hashCode()

    override fun toString(): String = "CreditLedger(confirmedMetersByDay=$days)"

    companion object {
        /**
         * Создаёт снимок с защитной копией [confirmedMetersByDay]: последующие
         * изменения исходной карты вызывающего кода снимок не затрагивают.
         */
        fun of(confirmedMetersByDay: Map<LocalDate, Long> = emptyMap()): CreditLedger =
            CreditLedger(Collections.unmodifiableMap(LinkedHashMap(confirmedMetersByDay)))
    }
}
