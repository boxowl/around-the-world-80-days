package com.boxowl.aroundtheworld.expedition.route

import java.io.File
import java.util.Locale
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Generates the measurement report (Markdown table) for
 * docs/design/v2-route-geometry.md. The document's numbers come from this
 * report, not from hand arithmetic. Output: app/build/reports/v21-route-report.md
 */
class RouteReportTest {

    @Test
    fun `write measurement report`() {
        val measures = RouteMeasure.measureAll()
        val sb = StringBuilder()
        sb.appendLine("# Отчёт измерений RouteDatasetV1 (сгенерировано кодом, не редактировать вручную)")
        sb.appendLine()
        sb.appendLine("Версия набора: ${RouteDatasetV1.version}; сфера R = $EARTH_RADIUS_KM км")
        sb.appendLine()

        val byRow = RouteMeasure.totalsByDraftRow()
        for ((row, rowKm, rowLegs) in byRow) {
            sb.appendLine("## ${row.label} — ${row.titleRu}")
            sb.appendLine()
            sb.appendLine("| Плечо | Транспорт | Измерено, км | Ортодромия, км | Доля обхода | Погрешность |")
            sb.appendLine("|---|---|---:|---:|---:|---:|")
            for (m in rowLegs) {
                val from = RouteDatasetV1.node(m.leg.fromId).nameRu
                val to = RouteDatasetV1.node(m.leg.toId).nameRu
                sb.appendLine(
                    "| $from → $to | ${m.leg.transport} | ${f(m.measuredKm)} | ${f(m.orthodromeKm)} | " +
                        "${ratio(m.detourRatio)} | ±${m.leg.uncertaintyPct}% |",
                )
            }
            sb.appendLine("| **Итого по строке** | | **${f(rowKm)}** | | | |")
            sb.appendLine()
        }

        sb.appendLine("## Итоги")
        sb.appendLine()
        sb.appendLine("| Транспорт | км |")
        sb.appendLine("|---|---:|")
        for ((transport, km) in RouteMeasure.totalsByTransport()) {
            sb.appendLine("| $transport | ${f(km)} |")
        }
        val total = RouteMeasure.totalKm()
        sb.appendLine()
        sb.appendLine("**D = ${f(total)} км**; взвешенная погрешность ±${f1(RouteMeasure.weightedUncertaintyPct())}%")
        sb.appendLine()
        sb.appendLine("## Точные значения для регрессионного теста")
        sb.appendLine()
        for (m in measures) {
            sb.appendLine("\"${m.leg.id}\" to ${String.format(Locale.US, "%.1f", m.measuredKm)},")
        }
        sb.appendLine("total = ${String.format(Locale.US, "%.1f", total)}")
        sb.appendLine()

        val file = File("build/reports/v21-route-report.md")
        file.parentFile?.mkdirs()
        file.writeText(sb.toString())
        assertTrue(file.exists())
        assertTrue("report must mention all 10 draft rows", byRow.size == 10)
        assertTrue(measures.isNotEmpty())
    }

    private fun f(km: Double): String = String.format(Locale.US, "%,.0f", km).replace(',', ' ')

    private fun f1(v: Double): String = String.format(Locale.US, "%.1f", v)

    private fun ratio(r: Double): String = String.format(Locale.US, "%.3f", r)
}
