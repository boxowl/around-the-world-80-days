package com.boxowl.aroundtheworld.expedition.route

/**
 * Measurements over [RouteDatasetV1]: per-leg lengths, totals by transport and
 * by draft-table chapter rows, and the weighted uncertainty of the total.
 *
 * Metric definition (docs/design/v2-route-geometry.md): a leg's measured length
 * is the polyline length over its dataset nodes, times the leg's explicit
 * [RouteLeg.pathMultiplier]. The systematic underestimation of rail polylines
 * is NOT compensated by a coefficient; it is documented in
 * [RouteLeg.uncertaintyPct].
 */
object RouteMeasure {

    /** Measurement of one leg. */
    data class LegMeasure(
        val leg: RouteLeg,
        /** Polyline length over the leg's nodes, times pathMultiplier. */
        val measuredKm: Double,
        /** Direct great-circle distance between the leg endpoints. */
        val orthodromeKm: Double,
    ) {
        /** measuredKm / orthodromeKm; 1.0 for straight legs, > 1 for detours. */
        val detourRatio: Double
            get() = if (orthodromeKm < 1e-9) 1.0 else measuredKm / orthodromeKm
    }

    fun measure(leg: RouteLeg): LegMeasure {
        val polyline = RouteDatasetV1.polylineOf(leg)
        val raw = polylineLengthKm(polyline.map { it.lat to it.lon })
        val from = RouteDatasetV1.node(leg.fromId)
        val to = RouteDatasetV1.node(leg.toId)
        return LegMeasure(
            leg = leg,
            measuredKm = raw * leg.pathMultiplier,
            orthodromeKm = distanceKm(from.lat, from.lon, to.lat, to.lon),
        )
    }

    fun measureAll(): List<LegMeasure> = RouteDatasetV1.legs.map(::measure)

    fun totalKm(): Double = measureAll().sumOf { it.measuredKm }

    fun totalsByTransport(): Map<Transport, Double> =
        measureAll().groupBy { it.leg.transport }.mapValues { e -> e.value.sumOf { it.measuredKm } }

    /** Sum per draft-table chapter row, in row order. */
    fun totalsByDraftRow(): List<Triple<DraftRow, Double, List<LegMeasure>>> =
        DraftRow.entries.map { row ->
            val measures = measureAll().filter { it.leg.draftRow == row }
            Triple(row, measures.sumOf { it.measuredKm }, measures)
        }

    /**
     * Weighted average of per-leg uncertaintyPct, weighted by measured length.
     * (Simple linear blend — errors of different legs are not independent, so
     * a root-sum-square would understate the band.)
     */
    fun weightedUncertaintyPct(): Double {
        val all = measureAll()
        val total = all.sumOf { it.measuredKm }
        return all.sumOf { it.measuredKm * it.leg.uncertaintyPct } / total
    }

    /** Cumulative distance after each leg, in route order. */
    fun cumulativeKm(): List<Double> {
        var acc = 0.0
        return measureAll().map { acc += it.measuredKm; acc }
    }
}
