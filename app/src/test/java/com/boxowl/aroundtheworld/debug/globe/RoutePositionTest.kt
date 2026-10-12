package com.boxowl.aroundtheworld.debug.globe

import com.boxowl.aroundtheworld.expedition.route.EARTH_RADIUS_KM
import com.boxowl.aroundtheworld.expedition.route.RouteDatasetV1
import com.boxowl.aroundtheworld.expedition.route.RouteMeasure
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM tests of the V2.0d slice positioning (RoutePosition over RouteDatasetV1).
 * Run with :app:testDebugUnitTest.
 */
class RoutePositionTest {

    private val legs = RouteDatasetV1.legs
    private val measures = RouteMeasure.measureAll()
    private val cumulative = RouteMeasure.cumulativeKm()
    private val totalKm = RoutePosition.totalKm

    private fun nodeVec(id: String): Vec3 {
        val n = RouteDatasetV1.node(id)
        return latLonToVec(n.lat, n.lon)
    }

    private fun assertSamePlace(expected: Vec3, actual: Vec3, tolDeg: Double, what: String) {
        val d = angularDistanceDeg(expected, actual)
        assertTrue("$what: $d deg apart (tol $tolDeg)", d <= tolDeg)
    }

    @Test
    fun `total km matches RouteMeasure and legs count`() {
        assertEquals(26, legs.size) // 24 литературных плеча, два расщеплены на a/b
        assertEquals(RouteMeasure.totalKm(), totalKm, 1e-9)
        assertEquals(RouteMeasure.totalKm(), cumulative.last(), 1e-9)
    }

    @Test
    fun `start clamps to london and finish lands on final london`() {
        val london = nodeVec("london")
        assertSamePlace(london, RoutePosition.positionAt(0.0), 1e-6, "start")
        assertSamePlace(london, RoutePosition.positionAt(-100.0), 1e-6, "clamped negative")
        assertSamePlace(london, RoutePosition.positionAt(totalKm), 1e-4, "finish")
        assertSamePlace(london, RoutePosition.positionAt(totalKm + 1000.0), 1e-4, "clamped beyond")
    }

    @Test
    fun `leg boundary equals end of previous and start of next leg`() {
        for (i in 0 until legs.size - 1) {
            val boundary = cumulative[i]
            val node = nodeVec(legs[i].toId)
            // 1e-4 deg ~ 11 mm: covers float summation drift along the polyline walk.
            assertSamePlace(node, RoutePosition.positionAt(boundary), 1e-4, "end of ${legs[i].id}")
            assertSamePlace(
                nodeVec(legs[i + 1].fromId),
                RoutePosition.positionAt(boundary + 1e-6),
                1e-3,
                "start of ${legs[i + 1].id}",
            )
            // Consecutive legs share the node id at the seam.
            assertEquals(legs[i].toId, legs[i + 1].fromId)
        }
    }

    @Test
    fun `one book meter around a leg boundary stays within metres of the seam`() {
        // Boundary between leg-07 (suez->aden) and leg-08 (aden->bombay): the Aden node.
        val idx = legs.indexOfFirst { it.toId == "aden" }
        val boundary = cumulative[idx]
        val aden = nodeVec("aden")
        val oneBookMeterKm = 0.001
        // 1 book meter = 1 geometric metre or less (pathMultiplier >= 1) ~ 9e-6 deg.
        val tolDeg = Math.toDegrees(oneBookMeterKm / EARTH_RADIUS_KM) * 2.0
        assertSamePlace(aden, RoutePosition.positionAt(boundary - oneBookMeterKm), tolDeg, "1 bm before aden")
        assertSamePlace(aden, RoutePosition.positionAt(boundary + oneBookMeterKm), tolDeg, "1 bm after aden")
    }

    @Test
    fun `antimeridian leg 16 interpolates across 180 not through 0`() {
        val idx = legs.indexOfFirst { it.id == "leg-16-yokohama-san-francisco" }
        val start = if (idx == 0) 0.0 else cumulative[idx - 1]
        val len = measures[idx].measuredKm
        var prevLon: Double? = null
        for (s in 0..100) {
            val (lat, lon) = vecToLatLon(RoutePosition.positionAt(start + len * s / 100.0))
            // Northern Pacific arc: sane latitudes, never equator-ward detours.
            assertTrue("lat sane at step $s: $lat", lat in 25.0..55.0)
            // The leg stays in the Pacific longitudes: never crosses lon 0.
            assertTrue("lon stays pacific at step $s: $lon", abs(lon) > 100.0)
            prevLon?.let { p ->
                // Shortest longitude delta between consecutive samples is small:
                // interpolation never sweeps the long way through 0 deg.
                var d = abs(lon - p) % 360.0
                if (d > 180.0) d = 360.0 - d
                assertTrue("lon jump at step $s: $d", d < 5.0)
            }
            prevLon = lon
        }
    }

    @Test
    fun `pathMultiplier of elephant leg slows geometric progress`() {
        val idx = legs.indexOfFirst { it.id == "leg-10-kholby-allahabad" }
        assertEquals(1.25, legs[idx].pathMultiplier, 1e-12)
        val start = cumulative[idx - 1]
        val legBookKm = measures[idx].measuredKm
        // Leg-10 is a single chord (no waypoints). rawKm book km inside the leg
        // move the position by only rawKm/1.25 geometric km: positionAt at
        // start + rawKm (the full geometric length counted as book km) must
        // stop at 1/1.25 = 80% of the chord, not at Allahabad.
        val rawKm = legBookKm / legs[idx].pathMultiplier
        val pos = RoutePosition.positionAt(start + rawKm)
        val kholby = nodeVec("kholby")
        val allahabad = nodeVec("allahabad")
        val fullDeg = angularDistanceDeg(kholby, allahabad)
        assertEquals(fullDeg * 0.8, angularDistanceDeg(kholby, pos), fullDeg * 0.01)
        // Full book length of the leg lands exactly on Allahabad.
        assertSamePlace(allahabad, RoutePosition.positionAt(start + legBookKm), 1e-6, "allahabad")
    }

    @Test
    fun `increasing book km moves the position forward along the route`() {
        // Along-track monotonicity: every 250 book km step advances the position
        // by a positive geometric step bounded by the book step (multiplier >= 1).
        val stepKm = 250.0
        var prev = RoutePosition.positionAt(0.0)
        var traveledDeg = 0.0
        var km = stepKm
        while (km < totalKm) {
            val cur = RoutePosition.positionAt(km)
            val stepDeg = angularDistanceDeg(prev, cur)
            val stepKmGeom = Math.toRadians(stepDeg) * EARTH_RADIUS_KM
            assertTrue("positive step at $km km: $stepKmGeom", stepKmGeom > 1.0)
            assertTrue("step bounded by book km at $km: $stepKmGeom", stepKmGeom <= stepKm + 1.0)
            traveledDeg += stepDeg
            prev = cur
            km += stepKm
        }
        assertTrue("accumulated traveled arc is meaningful", traveledDeg > 300.0)
    }

    @Test
    fun `chapter marks sit on their nodes at measured cumulative km`() {
        val marks = RoutePosition.chapterMarks
        assertEquals(8, marks.size)
        assertEquals(
            listOf("suez", "bombay", "calcutta", "hong-kong", "yokohama", "san-francisco", "new-york", "london"),
            marks.map { it.nodeId },
        )
        // Strictly increasing cumulative book km, ending exactly at D.
        var prev = -1.0
        for (mark in marks) {
            assertTrue("increasing at ${mark.nodeId}", mark.bookKm > prev)
            prev = mark.bookKm
            assertSamePlace(nodeVec(mark.nodeId), mark.pos, 1e-4, "mark ${mark.nodeId}")
            assertEquals(RouteDatasetV1.node(mark.nodeId).nameRu, mark.nameRu)
        }
        assertEquals(totalKm, marks.last().bookKm, 1e-9)
    }

    @Test
    fun `scene route distDeg is book-consistent with positionAt`() {
        val route = RoutePosition.buildSceneRoute(0.0)
        // Total arc of the dense polyline equals D converted to degrees.
        assertEquals(RoutePosition.bookKmToDeg(totalKm), route.totalDeg, 1e-6)
        // distDeg is strictly increasing and segMode covers every segment.
        assertEquals(route.points.size, route.distDeg.size)
        assertEquals(route.points.size - 1, route.segMode.size)
        for (i in 1 until route.distDeg.size) {
            assertTrue("distDeg increasing at $i", route.distDeg[i] > route.distDeg[i - 1])
        }
    }
}
