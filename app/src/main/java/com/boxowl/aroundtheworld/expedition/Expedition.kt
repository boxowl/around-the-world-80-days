package com.boxowl.aroundtheworld.expedition

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** Each supplied date is a replacement aggregate, never an increment. */
data class Expedition(
    val startedAt: Instant,
    val zone: ZoneId,
    val mode: JourneyMode,
    val paceStepsPerDay: Int = BASE_PACE,
    val worldGoal: Long = paceStepsPerDay * 80L,
    val firstLegGoal: Long = paceStepsPerDay * 7L,
    val goalExplanation: String = goalExplanation(paceStepsPerDay),
    val dailySteps: Map<LocalDate, Long> = emptyMap(),
    val unlocked: Set<String> = setOf("london"),
    val lastReadAt: Instant? = null,
    val routeVersion: Int = 1,
) {
    init {
        require(routeVersion == 1) { "Unsupported route version" }
        require(paceStepsPerDay in PACES) { "Unsupported pace" }
        require(worldGoal == paceStepsPerDay * 80L && firstLegGoal == paceStepsPerDay * 7L)
        require(goalExplanation.isNotBlank())
        require(dailySteps.values.all { it >= 0 })
        require(dailySteps.keys.all { it >= startDate })
        require(unlocked.all { id -> FIRST_LEG.any { it.id == id } })
        require("london" in unlocked)
        require(dailySteps.isEmpty() || lastReadAt != null)
        require(lastReadAt == null || lastReadAt >= startedAt)
        require(lastReadAt == null || dailySteps.keys.all { it <= lastReadAt.atZone(zone).toLocalDate() })
        totalSteps // Detect overflow before accepting or persisting a snapshot.
    }
    val startDate: LocalDate get() = startedAt.atZone(zone).toLocalDate()
    val deadline: Instant get() = startDate.plusDays(80).atStartOfDay(zone).toInstant()
    val totalSteps: Long get() = dailySteps.values.fold(0L, Math::addExact)
    val wagerSteps: Long get() = dailySteps.filterKeys { it < startDate.plusDays(80) }.values.fold(0L, Math::addExact)
    val firstLegSteps: Long get() = totalSteps.coerceAtMost(firstLegGoal)
    val stops: List<RouteStop> get() = FIRST_LEG.map { it.copy(threshold = scaledThreshold(it.threshold, worldGoal)) }
    val nextStop: RouteStop? get() = stops.firstOrNull { it.threshold > totalSteps }
    fun dayNumber(now: Instant): Long = (ChronoUnit.DAYS.between(startDate, now.atZone(zone).toLocalDate()) + 1).coerceAtLeast(1)
    fun wagerStatus(now: Instant): WagerStatus = when {
        mode == JourneyMode.FREE -> WagerStatus.NOT_APPLICABLE
        wagerSteps >= worldGoal -> WagerStatus.WON
        now >= deadline -> WagerStatus.EXPIRED
        else -> WagerStatus.ACTIVE
    }

    /** The caller must clip the first day's aggregate to startedAt and use this expedition's zone.
     * Missing dates stay unchanged; an explicitly supplied zero clears that date.
     * Only a successfully completed read may call this method, never an access/network error.
     */
    fun reconcile(replacements: Map<LocalDate, Long>, readAt: Instant): Expedition {
        require(readAt >= startedAt)
        require(lastReadAt == null || readAt >= lastReadAt) { "Stale read" }
        val revised = copy(dailySteps = dailySteps + replacements, lastReadAt = readAt)
        return revised.copy(unlocked = unlocked + revised.stops.filter { it.threshold <= revised.totalSteps }.map { it.id })
    }
}
enum class JourneyMode { WAGER, FREE }
enum class WagerStatus { NOT_APPLICABLE, ACTIVE, WON, EXPIRED }
data class RouteStop(val id: String, val name: String, val threshold: Long, val diary: String)
const val WORLD_GOAL = 560_000L
const val FIRST_LEG_GOAL = 49_000L
const val BASE_PACE = 7_000
val PACES = listOf(5_000, BASE_PACE, 10_000)
fun goalExplanation(pace: Int): String = "$pace шагов в день × 80 календарных дней; первый участок — $pace × 7. Пороговые события масштабированы от базового маршрута."
fun scaledThreshold(base: Long, worldGoal: Long): Long {
    require(base in 0..WORLD_GOAL && worldGoal > 0)
    return Math.addExact(Math.multiplyExact(base, worldGoal), WORLD_GOAL / 2) / WORLD_GOAL
}
val FIRST_LEG = listOf(
    RouteStop("london", "Лондон", 0, "Паспорт раскрыт на первой странице. Впереди — дорога к морю и целый мир за окном."),
    RouteStop("departure", "За лондонскими крышами", 1_000, "Крыши остаются позади. Первые поля сменяют город, и путешествие обретает свой ритм."),
    RouteStop("dover", "Дувр", 4_000, "Белые скалы встречают поезд. Дальше путь продолжится по воде."),
    RouteStop("calais", "Кале", 7_000, "Первая переправа завершена. На другом берегу уже ждёт железная дорога."),
    RouteStop("paris", "Париж", 14_000, "Короткая пересадка, гул вокзала и билет на следующий поезд. Мы продолжаем путь на юг."),
    RouteStop("alps", "Альпы", 21_000, "За окном поднимаются снежные вершины. Свет гор сменяется темнотой тоннеля."),
    RouteStop("turin", "Турин", 28_000, "Воздух становится теплее. Железная дорога ведёт нас всё ближе к итальянскому побережью."),
    RouteStop("brindisi", "Бриндизи", 35_000, "Багаж перенесён на пароход. Теперь вместо стука колёс слышен ровный ход машины."),
    RouteStop("mediterranean", "Средиземное море", 42_000, "С палубы видны только вода и небо. В дневнике появляется первая морская запись."),
    RouteStop("suez", "Суэц", 49_000, "Первый большой участок позади. Штамп Суэца занимает своё место в паспорте."),
)
