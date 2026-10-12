package com.boxowl.aroundtheworld.debug.globe

import com.boxowl.aroundtheworld.expedition.credit.CreditLedger
import java.time.LocalDate

/**
 * V2.0d debug slice: synthetic scenarios -> CreditLedger snapshots.
 *
 * Pure JVM logic (unit-tested in SliceScenarioTest). Dates are fixed and fake
 * on purpose: this slice never touches Room/Health Connect, and the banner in
 * GlobeSliceActivity marks everything as synthetic. Real meters enter
 * CreditLedger as REPLACING daily aggregates (never increments); book km come
 * only from CreditLedger.totalBookMeters (k = 20 applied exactly once, inside
 * CreditLedger), divided by 1000 for km — nothing here multiplies by k again.
 */
object SliceScenario {

    /** Fixed fake dates of the synthetic two-day window. */
    val PREV_DAY: LocalDate = LocalDate.of(2026, 1, 1)
    val TODAY: LocalDate = LocalDate.of(2026, 1, 2)

    val SCENARIO_NAMES: List<String> = listOf(
        "start", "suez_before", "suez_after", "jump_two_chapters", "rollback", "finish",
    )

    /** Resolved scenario: the ledger snapshot plus a human-readable description for the UI. */
    data class Resolved(val ledger: CreditLedger, val description: String)

    /** Real meters that CreditLedger (k = 20) turns into [bookKm] book km: km * 1000 / 20. */
    private fun metersForBookKm(bookKm: Double): Long = Math.round(bookKm * 1000.0 / 20.0)

    /** Book km of the synthetic day sequence, derived from the ledger (never re-multiplied). */
    fun bookKmOf(ledger: CreditLedger): Double = ledger.totalBookMeters / 1000.0

    /**
     * Resolves intent extras into a scenario. [scenario] (extra "scenario") picks a preset
     * computed from the measured route; without it, [metersPrev]/[metersToday] (extras
     * "meters_prev"/"meters_today") are taken verbatim as the two daily aggregates.
     */
    fun resolve(scenario: String?, metersPrev: Long, metersToday: Long): Resolved {
        if (scenario != null) return preset(scenario)
        val ledger = CreditLedger.of(mapOf(PREV_DAY to metersPrev, TODAY to metersToday))
        return Resolved(ledger, "свободный ввод: $PREV_DAY = $metersPrev м, $TODAY = $metersToday м")
    }

    private fun preset(name: String): Resolved {
        val suezKm = RoutePosition.endKmOfNode("suez")
        return when (name) {
            "start" -> Resolved(CreditLedger.of(), "старт: подтверждённых метров нет")

            "suez_before" -> {
                val today = metersForBookKm(suezKm * 0.9)
                Resolved(
                    CreditLedger.of(mapOf(TODAY to today)),
                    "до Суэца: $TODAY = $today м (90% книжных км до Суэца)",
                )
            }

            "suez_after" -> {
                val today = metersForBookKm(suezKm + 500.0)
                Resolved(
                    CreditLedger.of(mapOf(TODAY to today)),
                    "после Суэца: $TODAY = $today м (Суэц + 500 книжных км)",
                )
            }

            "jump_two_chapters" -> {
                // Один большой скачок: от Суэца сразу до Гонконга (главы IX и XI–XVIII).
                val prev = metersForBookKm(suezKm)
                val today = metersForBookKm(RoutePosition.endKmOfNode("hong-kong") - suezKm)
                Resolved(
                    CreditLedger.of(mapOf(PREV_DAY to prev, TODAY to today)),
                    "скачок через две главы: $PREV_DAY = $prev м (Суэц), $TODAY = $today м (→ Гонконг)",
                )
            }

            "rollback" -> {
                // Откат — замена дневного агрегата через reconcile (не отрицательное приращение):
                // день сначала подтверждён как "Суэц + 500 книжных км", затем заменён на 90% до Суэца.
                val before = metersForBookKm(suezKm + 500.0)
                val after = metersForBookKm(suezKm * 0.9)
                val ledger = CreditLedger.of(mapOf(TODAY to before))
                    .reconcile(mapOf(TODAY to after))
                Resolved(
                    ledger,
                    "откат: $TODAY $before → $after м (reconcile-замена агрегата)",
                )
            }

            "finish" -> {
                // ceil: итог чуть выше D, чтобы показать clamp ровно на финишном Лондоне.
                val today = Math.ceil(RoutePosition.totalKm * 1000.0 / 20.0).toLong()
                Resolved(
                    CreditLedger.of(mapOf(TODAY to today)),
                    "финиш: $TODAY = $today м (книжные км ≥ D, позиция зажата в Лондоне)",
                )
            }

            else -> Resolved(
                CreditLedger.of(),
                "неизвестный сценарий «$name» — показан старт (0 м); доступны: ${SCENARIO_NAMES.joinToString()}",
            )
        }
    }
}
