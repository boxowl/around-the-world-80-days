package com.boxowl.aroundtheworld.expedition.credit

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * Контрактные тесты расчёта зачёта пути v2 (docs/design/v22a-credit-calculation.md).
 * Числа: k = 20 книжных метров на реальный метр (решение владельца от 2026-10-09);
 * дневного лимита нет. Длина маршрута из PR #15 (D ≈ 40 502 книжных км) участвует
 * только в проверке порядка величины и не масштабирует расчёт.
 */
class CreditLedgerTest {

    private val day1: LocalDate = LocalDate.of(2026, 10, 10)
    private val day2: LocalDate = LocalDate.of(2026, 10, 11)
    private val day3: LocalDate = LocalDate.of(2026, 10, 12)

    @Test
    fun `zero meters credit zero book meters`() {
        val credit = DayCredit(0L)
        assertEquals(0L, credit.realMeters)
        assertEquals(0L, credit.bookMeters)
    }

    @Test
    fun `one day credits exactly twenty book meters per real meter`() {
        assertEquals(30_000L, DayCredit(1_500L).bookMeters)
    }

    @Test
    fun `meters above the former daily limit credit in full`() {
        // Лимита больше нет: 5 500 и 20 000 м засчитываются целиком, без усечения.
        assertEquals(110_000L, DayCredit(5_500L).bookMeters)
        assertEquals(400_000L, DayCredit(20_000L).bookMeters)
    }

    @Test
    fun `negative input is rejected`() {
        assertThrows(IllegalArgumentException::class.java) { DayCredit(-1L) }
        assertThrows(IllegalArgumentException::class.java) { CreditLedger.of(mapOf(day1 to -1L)) }
        assertThrows(IllegalArgumentException::class.java) {
            CreditLedger.of().reconcile(mapOf(day1 to -5L))
        }
    }

    @Test
    fun `day multiplication overflow throws ArithmeticException`() {
        assertThrows(ArithmeticException::class.java) { DayCredit(Long.MAX_VALUE) }
    }

    @Test
    fun `multi-day totals are derived sums without an accumulator`() {
        val ledger = CreditLedger.of(mapOf(day1 to 1_500L, day2 to 5_500L, day3 to 20_000L))
        assertEquals(27_000L, ledger.totalRealMeters)
        assertEquals(540_000L, ledger.totalBookMeters)
    }

    @Test
    fun `repeated reads of the same values are idempotent`() {
        val reads = mapOf(day1 to 1_500L, day2 to 5_500L)
        val first = CreditLedger.of().reconcile(reads)
        val second = first.reconcile(reads) // повторное чтение тех же значений
        assertEquals(first, second)
        assertEquals(first.totalRealMeters, second.totalRealMeters)
        assertEquals(first.totalBookMeters, second.totalBookMeters)
    }

    @Test
    fun `late correction replaces the day and may roll the position back`() {
        val ledger = CreditLedger.of(mapOf(day1 to 20_000L, day2 to 5_000L))
        val correctedDown = ledger.reconcile(mapOf(day1 to 3_000L))
        assertEquals(8_000L, correctedDown.totalRealMeters)
        assertEquals(160_000L, correctedDown.totalBookMeters)
        assertEquals(DayCredit(3_000L), correctedDown.creditForDay(day1))
        val correctedUp = correctedDown.reconcile(mapOf(day1 to 25_000L))
        assertEquals(30_000L, correctedUp.totalRealMeters)
        assertEquals(600_000L, correctedUp.totalBookMeters)
    }

    @Test
    fun `explicit zero replaces the day value`() {
        val ledger = CreditLedger.of(mapOf(day1 to 5_500L)).reconcile(mapOf(day1 to 0L))
        assertEquals(DayCredit(0L), ledger.creditForDay(day1))
        assertEquals(0L, ledger.totalRealMeters)
        assertEquals(0L, ledger.totalBookMeters)
    }

    @Test
    fun `dates missing from a replacement batch stay unchanged`() {
        val ledger = CreditLedger.of(mapOf(day1 to 1_500L, day2 to 5_500L))
        val revised = ledger.reconcile(mapOf(day2 to 6_000L, day3 to 700L))
        assertEquals(DayCredit(1_500L), revised.creditForDay(day1))
        assertEquals(DayCredit(6_000L), revised.creditForDay(day2))
        assertEquals(8_200L, revised.totalRealMeters)
    }

    @Test
    fun `unknown day differs from a confirmed zero`() {
        val ledger = CreditLedger.of(mapOf(day1 to 0L))
        assertNull(ledger.creditForDay(day2)) // день неизвестен — нет подтверждённого значения
        assertEquals(DayCredit(0L), ledger.creditForDay(day1)) // подтверждённый ноль
        assertNotEquals(ledger.creditForDay(day2), ledger.creditForDay(day1))
    }

    @Test
    fun `total overflow across days throws ArithmeticException`() {
        assertThrows(ArithmeticException::class.java) {
            CreditLedger.of(mapOf(day1 to Long.MAX_VALUE - 1L, day2 to 2L))
        }
        // Сумма реальных метров не переполняется, но книжный итог (× 20) — да.
        assertThrows(ArithmeticException::class.java) {
            CreditLedger.of(mapOf(day1 to Long.MAX_VALUE / BOOK_METERS_PER_REAL_METER + 1L))
        }
    }

    @Test
    fun `empty ledger has zero totals and empty reconcile changes nothing`() {
        val empty = CreditLedger.of()
        assertEquals(0L, empty.totalRealMeters)
        assertEquals(0L, empty.totalBookMeters)
        val ledger = CreditLedger.of(mapOf(day1 to 1_500L))
        assertEquals(ledger, ledger.reconcile(emptyMap()))
    }

    @Test
    fun `overflow domains of per-day sum and total multiplication coincide`() {
        // При всех значениях >= 0 переполнение суммы дневных книжных метров
        // (sum of 20 * r) наступает ровно тогда же, когда переполняется
        // 20 * sum(r): оба условия равносильны sum(r) > Long.MAX_VALUE / 20.
        val boundary = CreditLedger.of(mapOf(day1 to Long.MAX_VALUE / BOOK_METERS_PER_REAL_METER))
        assertEquals(
            Long.MAX_VALUE / BOOK_METERS_PER_REAL_METER * BOOK_METERS_PER_REAL_METER,
            boundary.totalBookMeters,
        )
        assertThrows(ArithmeticException::class.java) {
            CreditLedger.of(
                mapOf(
                    day1 to Long.MAX_VALUE / BOOK_METERS_PER_REAL_METER,
                    day2 to 1L,
                ),
            )
        }
    }

    @Test
    fun `mutating the source map after creation does not change the snapshot`() {
        val source = mutableMapOf(day1 to 1_500L)
        val ledger = CreditLedger.of(source)
        source[day1] = 0L
        source[day2] = 20_000L
        assertEquals(1_500L, ledger.totalRealMeters)
        assertEquals(30_000L, ledger.totalBookMeters)
        assertEquals(DayCredit(1_500L), ledger.creditForDay(day1))
        assertNull(ledger.creditForDay(day2))
        assertEquals(CreditLedger.of(mapOf(day1 to 1_500L)), ledger)

        // Мутация источника за пределы Long не меняет снимок и не обходит раннюю
        // проверку переполнения: итоги уже созданного снимка стабильны.
        source[day1] = Long.MAX_VALUE
        source[day2] = Long.MAX_VALUE
        assertEquals(1_500L, ledger.totalRealMeters)
        assertEquals(30_000L, ledger.totalBookMeters)
    }

    @Test
    fun `reconcile result is isolated from later mutations of the replacements map`() {
        val replacements = mutableMapOf(day1 to 5_000L)
        val ledger = CreditLedger.of(mapOf(day2 to 700L)).reconcile(replacements)
        replacements[day1] = 9_999L
        assertEquals(5_700L, ledger.totalRealMeters)
        assertEquals(DayCredit(5_000L), ledger.creditForDay(day1))
    }

    @Test
    fun `exposed days map rejects mutation even through a mutable cast`() {
        val ledger = CreditLedger.of(mapOf(day1 to 1_500L))
        @Suppress("UNCHECKED_CAST")
        val casted = ledger.confirmedMetersByDay as MutableMap<LocalDate, Long>
        assertThrows(UnsupportedOperationException::class.java) { casted[day2] = 1L }
        assertEquals(1_500L, ledger.totalRealMeters)
    }

    @Test
    fun `book route length matches the yearly walking order of magnitude`() {
        // D ≈ 40 502 книжных км (измеренная длина маршрута, PR #15) при k = 20
        // соответствует 40_502_000 / 20 = 2_025_100 реальным метрам (~2 025 км,
        // порядок величины годичного пути пешком). Только проверка порядка: ни D,
        // ни годичная норма в расчёт не подставляются.
        val ledger = CreditLedger.of(mapOf(day1 to 2_025_100L))
        assertEquals(40_502_000L, ledger.totalBookMeters)
        assertEquals(2_025_100L, ledger.totalRealMeters)
    }
}
