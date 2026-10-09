package com.boxowl.aroundtheworld.expedition.credit

/**
 * Зачёт пути для новой экспедиции v2 (документ: docs/design/v22a-credit-calculation.md).
 *
 * No Android dependencies: everything here is JVM-unit-testable.
 * Единицы — целые метры (Long); книжные метры = подтверждённые реальные метры × 20
 * (решение владельца от 2026-10-09: 1 реальный км = 20 книжных км). Дневного лимита
 * нет: все подтверждённые метры засчитываются целиком, без усечения и потолка.
 * Округления нет — оно остаётся представлению.
 */

/** Книжных метров на один подтверждённый реальный метр (безразмерный коэффициент k = 20). */
const val BOOK_METERS_PER_REAL_METER: Long = 20L

/**
 * Чистый зачёт одного календарного дня: пара (реальные метры, книжные метры).
 *
 * [realMeters] — подтверждённые целые реальные метры за день (заменяющий агрегат,
 * не приращение). Отрицательный вход — явный отказ (IllegalArgumentException).
 * Книжные метры вычисляются точным целочисленным умножением при создании;
 * переполнение — явное ArithmeticException.
 */
data class DayCredit(val realMeters: Long) {
    init {
        require(realMeters >= 0L) { "Confirmed meters must be non-negative, got $realMeters" }
    }

    /** Книжные метры = реальные × [BOOK_METERS_PER_REAL_METER]; форсируется при создании. */
    val bookMeters: Long = Math.multiplyExact(realMeters, BOOK_METERS_PER_REAL_METER)
}
