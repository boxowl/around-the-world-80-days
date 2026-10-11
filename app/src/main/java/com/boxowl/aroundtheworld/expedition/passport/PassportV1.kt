package com.boxowl.aroundtheworld.expedition.passport

import com.boxowl.aroundtheworld.expedition.route.RouteDatasetV1
import com.boxowl.aroundtheworld.expedition.route.RouteMeasure
import java.time.Instant
import java.util.Collections

/**
 * Стабильный идентификатор отметки паспорта v2. Ровно 9 отметок: стартовый
 * Лондон и 8 финишей глав (Суэц, Бомбей, Калькутта, Гонконг, Иокогама,
 * Сан-Франциско, Нью-Йорк, финальный Лондон).
 *
 * [id] — стабильное строковое значение, зафиксированное контрактными тестами
 * (PassportV1Test); менять нельзя — на него будет опираться хранение V2.4b.
 * [nodeId] — геометрический узел маршрута: старт и финиш Лондона имеют ОДИН
 * nodeId ("london"), но РАЗНЫЕ StampId и разные пороги (0 и D).
 *
 * Порядок объявления — порядок маршрута: пороги строго возрастают.
 */
enum class StampId(val id: String, val nodeId: String) {
    LONDON_START("stamp-london-start", "london"),
    SUEZ_FINISH("stamp-suez-finish", "suez"),
    BOMBAY_FINISH("stamp-bombay-finish", "bombay"),
    CALCUTTA_FINISH("stamp-calcutta-finish", "calcutta"),
    HONG_KONG_FINISH("stamp-hong-kong-finish", "hong-kong"),
    YOKOHAMA_FINISH("stamp-yokohama-finish", "yokohama"),
    SAN_FRANCISCO_FINISH("stamp-san-francisco-finish", "san-francisco"),
    NEW_YORK_FINISH("stamp-new-york-finish", "new-york"),
    LONDON_FINISH("stamp-london-finish", "london"),
}

/**
 * Неизменяемый снимок выданных отметок паспорта v2.
 *
 * Состояние — карта StampId → момент выдачи (null = дата неизвестна, например
 * штамп восстановлен без даты). Наличие ключа означает «штамп выдан»; отсутствие
 * ключа — «штамп не выдан».
 *
 * Снимок глубоко неизменяем по образцу CreditLedger: [of] делает защитную
 * неизменяемую копию переданной карты, [granted] — неизменяемое представление
 * (cast к MutableMap бросает UnsupportedOperationException). Мутация исходной
 * карты вызывающего кода после создания снимок не затрагивает.
 *
 * Неизвестные StampId невозможны типобезопасно: ключи карты — enum StampId,
 * строковые id фиксируются тестами.
 */
class PassportSnapshot private constructor(val granted: Map<StampId, Instant?>) {

    override fun equals(other: Any?): Boolean = other is PassportSnapshot && granted == other.granted

    override fun hashCode(): Int = granted.hashCode()

    override fun toString(): String = "PassportSnapshot(granted=$granted)"

    companion object {
        /**
         * Создаёт снимок с защитной копией [granted]: последующие изменения
         * исходной карты вызывающего кода снимок не затрагивают.
         */
        fun of(granted: Map<StampId, Instant?> = emptyMap()): PassportSnapshot =
            PassportSnapshot(Collections.unmodifiableMap(LinkedHashMap(granted)))
    }
}

/**
 * Чистая доменная модель паспорта v2 (V2.4a, docs/design/v24a-passport.md).
 *
 * Пороги — функция от геометрии маршрута (RouteDatasetV1/RouteMeasure),
 * вычисляются при загрузке класса и НЕ являются литералами: стартовый Лондон —
 * 0 книжных метров; каждый финиш главы — кумулятив RouteMeasure.cumulativeKm()
 * после соответствующего плеча × 1000; финальный Лондон — RouteMeasure.totalKm()
 * × 1000. Отсутствие плеча в датасете — fail fast при инициализации.
 *
 * Единицы: пороги и прогресс — КНИЖНЫЕ метры (километры RouteMeasure × 1000.0).
 * Коэффициент перевода реальных метров в книжные здесь НЕ применяется: он уже
 * учтён раньше в CreditLedger.totalBookMeters.
 *
 * Правило границы (без скрытого допуска и без преждевременного округления):
 * порог хранится как Double в книжных метрах и может иметь дробную часть
 * (например, Суэц — ≈ 4 339 152.49; значение округлено для чтения, точный
 * порог вычисляется из геометрии и не округляется). Штамп выдаётся тогда и
 * только тогда, когда totalBookMeters.toDouble() >= порог. Прогресс
 * (≤ ~40.6M метров) точно представим в Double, поэтому граница
 * детерминирована: для Суэца 4 339 152 м не хватает, 4 339 153 м достаточно.
 *
 * V2.4a НЕ хранит и НЕ восстанавливает штампы: хранение, восстановление после
 * перезапуска, backfill исторических дат, источник календаря для observedAt и
 * миграция БД — критерии V2.4b и здесь проверенными не заявляются.
 */
object PassportV1 {

    /** Плечи-концы глав по StampId (кроме старта и финиша Лондона), в порядке маршрута. */
    private val chapterFinishLegs: List<Pair<StampId, String>> = listOf(
        StampId.SUEZ_FINISH to "leg-06b-port-said-suez",
        StampId.BOMBAY_FINISH to "leg-08-aden-bombay",
        StampId.CALCUTTA_FINISH to "leg-11-allahabad-calcutta",
        StampId.HONG_KONG_FINISH to "leg-13-singapore-hong-kong",
        StampId.YOKOHAMA_FINISH to "leg-15-shanghai-mouth-yokohama",
        StampId.SAN_FRANCISCO_FINISH to "leg-16-yokohama-san-francisco",
        StampId.NEW_YORK_FINISH to "leg-20-omaha-new-york",
    )

    private val thresholds: Map<StampId, Double> = computeThresholds()

    private fun computeThresholds(): Map<StampId, Double> {
        val indexByLegId = RouteDatasetV1.legs.withIndex().associate { it.value.id to it.index }
        val cumulative = RouteMeasure.cumulativeKm()
        val result = LinkedHashMap<StampId, Double>()
        result[StampId.LONDON_START] = 0.0
        for ((stampId, legId) in chapterFinishLegs) {
            val index = indexByLegId[legId]
                ?: error("PassportV1: chapter finish leg not found in RouteDatasetV1: $legId")
            result[stampId] = cumulative[index] * 1000.0
        }
        result[StampId.LONDON_FINISH] = RouteMeasure.totalKm() * 1000.0
        return Collections.unmodifiableMap(result)
    }

    /**
     * Порог выдачи [stampId] в книжных метрах (Double, может иметь дробную
     * часть). Для граничных сравнений порог не округлять — см. правило границы
     * в KDoc класса.
     */
    fun thresholdBookMeters(stampId: StampId): Double = thresholds.getValue(stampId)

    /**
     * Монотонная выдача штампов: результат = объединение (union) набора
     * [existing] и штампов, чьи пороги достигнуты прогрессом [totalBookMeters].
     *
     * Инварианты:
     * - переданный набор никогда не уменьшается: откат прогресса (меньший
     *   totalBookMeters) НЕ отзывает ранее выданные штампы;
     * - даты переданных штампов НЕ переписываются: ни дата, ни старый null не
     *   заменяются новым observedAt;
     * - новое пересечение получает переданное [observedAt] (может быть null —
     *   «дата неизвестна»); задним числом дата из текущего итога не выводится;
     * - идемпотентность: reconcile(reconcile(x, m, t), m, t) == reconcile(x, m, t);
     * - [totalBookMeters] < 0 — IllegalArgumentException.
     */
    fun reconcile(
        existing: PassportSnapshot,
        totalBookMeters: Long,
        observedAt: Instant? = null,
    ): PassportSnapshot {
        require(totalBookMeters >= 0L) { "totalBookMeters must be non-negative: $totalBookMeters" }
        val progress = totalBookMeters.toDouble()
        val merged = LinkedHashMap<StampId, Instant?>(existing.granted.size + StampId.entries.size)
        merged.putAll(existing.granted)
        for ((stampId, threshold) in thresholds) {
            if (!merged.containsKey(stampId) && progress >= threshold) {
                merged[stampId] = observedAt
            }
        }
        return PassportSnapshot.of(merged)
    }
}
