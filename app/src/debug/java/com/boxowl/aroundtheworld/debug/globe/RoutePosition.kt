package com.boxowl.aroundtheworld.debug.globe

import com.boxowl.aroundtheworld.expedition.route.EARTH_RADIUS_KM
import com.boxowl.aroundtheworld.expedition.route.RouteDatasetV1
import com.boxowl.aroundtheworld.expedition.route.RouteLeg
import com.boxowl.aroundtheworld.expedition.route.RouteMeasure
import com.boxowl.aroundtheworld.expedition.route.RouteNode
import com.boxowl.aroundtheworld.expedition.route.distanceKm
import kotlin.math.PI
import kotlin.math.ceil

/**
 * V2.0d debug slice: positioning on the versioned route (RouteDatasetV1) by
 * BOOK kilometres. Pure JVM math, no Android dependencies — unit-tested in
 * app/src/test (RoutePositionTest).
 *
 * Distance model: RouteMeasure measures every leg in book km (polyline length
 * x pathMultiplier). [positionAt] converts the in-leg remainder of book km
 * back to geometric km by DIVIDING by the leg's pathMultiplier, then walks the
 * leg polyline interpolating strictly with slerp on unit vectors (never linear
 * in lat/lon — leg-16 Yokohama -> San-Francisco crosses the antimeridian).
 *
 * All measurements are computed once (legs/cumulative/polylines are vals of
 * this object); positionAt never re-measures.
 */
object RoutePosition {

    private val legs: List<RouteLeg> = RouteDatasetV1.legs

    /** Cumulative BOOK km after each leg, in route order (RouteMeasure.cumulativeKm). */
    private val cumulativeKm: List<Double> = RouteMeasure.cumulativeKm()

    /** Node polylines per leg; segment lengths measured with the same distanceKm as RouteMeasure. */
    private val polylines: List<List<RouteNode>> = legs.map(RouteDatasetV1::polylineOf)

    /** Total book length of the route D, km (== RouteMeasure.totalKm()). */
    val totalKm: Double = cumulativeKm.last()

    /** Book km accumulated after the leg that ENDS at [nodeId] (e.g. "suez"). */
    fun endKmOfNode(nodeId: String): Double {
        val idx = legs.indexOfFirst { it.toId == nodeId }
        require(idx >= 0) { "no leg ends at node: $nodeId" }
        return cumulativeKm[idx]
    }

    /**
     * Position on the route at [bookKmRaw] book kilometres from the start.
     * Clamped to [0, [totalKm]]; book km == totalKm lands exactly on the final
     * London node.
     */
    fun positionAt(bookKmRaw: Double): Vec3 {
        val bookKm = bookKmRaw.coerceIn(0.0, totalKm)
        var idx = 0
        while (idx < legs.size - 1 && cumulativeKm[idx] < bookKm) idx++
        val cumBefore = if (idx == 0) 0.0 else cumulativeKm[idx - 1]
        // Book km inside the leg -> geometric km: the leg's book length is its
        // polyline length x pathMultiplier, so divide the remainder back.
        var remainingKm = (bookKm - cumBefore) / legs[idx].pathMultiplier
        val poly = polylines[idx]
        for (i in 0 until poly.size - 1) {
            val a = poly[i]
            val b = poly[i + 1]
            val segKm = distanceKm(a.lat, a.lon, b.lat, b.lon)
            if (remainingKm <= segKm || i == poly.size - 2) {
                val t = if (segKm < 1e-12) 0.0 else (remainingKm / segKm).coerceIn(0.0, 1.0)
                return slerp(latLonToVec(a.lat, a.lon), latLonToVec(b.lat, b.lon), t)
            }
            remainingKm -= segKm
        }
        val last = poly.last()
        return latLonToVec(last.lat, last.lon)
    }

    /** Book km -> degrees of arc on the RouteGeometry sphere (km / R * 180/pi). */
    fun bookKmToDeg(bookKm: Double): Double = bookKm / EARTH_RADIUS_KM * 180.0 / PI

    /**
     * Chapter-finish markers of the novel: end nodes of the 8 chapter spans.
     * Positions and distances come from the measured geometry (cumulative book
     * km of the leg ending at each node), never from coordinate literals.
     */
    val chapterFinishNodeIds: List<String> = listOf(
        "suez", "bombay", "calcutta", "hong-kong",
        "yokohama", "san-francisco", "new-york", "london",
    )

    data class ChapterMark(
        val nodeId: String,
        val nameRu: String,
        /** Cumulative book km at the marker (end of the chapter span). */
        val bookKm: Double,
        val pos: Vec3,
    )

    val chapterMarks: List<ChapterMark> by lazy {
        chapterFinishNodeIds.map { id ->
            val km = endKmOfNode(id)
            ChapterMark(id, RouteDatasetV1.node(id).nameRu, km, positionAt(km))
        }
    }

    /**
     * Dense route polyline for GlobeScene built from RouteDatasetV1 (NOT from
     * RouteData TEST DATA): nodes of every leg are slerp-densified with
     * [stepDeg] steps; the cumulative [RouteData.Route.distDeg] accumulates
     * BOOK degrees of arc (geometric degrees x pathMultiplier), so cutting
     * past/future at heroDistDeg matches [positionAt] of the same book km.
     */
    fun buildSceneRoute(bookKm: Double, stepDeg: Double = 1.5): RouteData.Route {
        val pts = ArrayList<Vec3>()
        val dist = ArrayList<Double>()
        val modes = ArrayList<RouteData.Transport>()
        var acc = 0.0
        for (leg in legs) {
            val mode = RouteData.Transport.valueOf(leg.transport.name)
            val mult = leg.pathMultiplier
            val poly = RouteDatasetV1.polylineOf(leg)
            for (i in 0 until poly.size - 1) {
                val a = latLonToVec(poly[i].lat, poly[i].lon)
                val b = latLonToVec(poly[i + 1].lat, poly[i + 1].lon)
                val segDeg = angularDistanceDeg(a, b)
                val steps = maxOf(1, ceil(segDeg / stepDeg).toInt())
                if (pts.isEmpty()) {
                    pts.add(a)
                    dist.add(acc)
                }
                for (s in 1..steps) {
                    pts.add(slerp(a, b, s.toDouble() / steps))
                    acc += segDeg * mult / steps
                    dist.add(acc)
                    modes.add(mode)
                }
            }
        }
        val clamped = bookKm.coerceIn(0.0, totalKm)
        return RouteData.Route(
            points = pts,
            distDeg = dist.toDoubleArray(),
            segMode = modes,
            totalDeg = acc,
            heroDistDeg = bookKmToDeg(clamped),
            heroPos = positionAt(clamped),
        )
    }
}
