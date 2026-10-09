package com.boxowl.aroundtheworld.expedition.route

import kotlin.math.cos
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression tests for RouteDatasetV1: per-leg lengths and the total are
 * pinned to the measured values (tolerance 1%), plus structural invariants
 * (monotonic cumulative distance, antimeridian sanity, edge cases).
 * Numbers come from RouteReportTest's generated report — do not hand-tune.
 */
class RouteGeometryTest {

    private val measures: List<RouteMeasure.LegMeasure> = RouteMeasure.measureAll()
    private val byId: Map<String, RouteMeasure.LegMeasure> = measures.associateBy { it.leg.id }

    /** Measured km per leg, from the v1 dataset report (see build/reports/v21-route-report.md). */
    private val expectedKm: Map<String, Double> = mapOf(
        "leg-01-london-dover" to 108.3,
        "leg-02-dover-calais" to 43.2,
        "leg-03-calais-paris" to 237.0,
        "leg-04-paris-turin" to 615.7,
        "leg-05-turin-brindisi" to 1007.5,
        "leg-06a-brindisi-port-said" to 2177.8,
        "leg-06b-port-said-suez" to 149.6,
        "leg-07-suez-aden" to 2437.1,
        "leg-08-aden-bombay" to 3142.2,
        "leg-09-bombay-kholby" to 1154.1,
        "leg-10-kholby-allahabad" to 95.6,
        "leg-11-allahabad-calcutta" to 754.7,
        "leg-12a-calcutta-hooghly" to 115.8,
        "leg-12b-hooghly-singapore" to 3129.1,
        "leg-13-singapore-hong-kong" to 2665.3,
        "leg-14-hong-kong-shanghai-mouth" to 1437.9,
        "leg-15-shanghai-mouth-yokohama" to 2056.3,
        "leg-16-yokohama-san-francisco" to 8589.6,
        "leg-17-san-francisco-ogden" to 1047.6,
        "leg-18-ogden-fort-kearney" to 1128.2,
        "leg-19-fort-kearney-omaha" to 266.7,
        "leg-20-omaha-new-york" to 2072.7,
        "leg-21-new-york-queenstown" to 5309.2,
        "leg-22-queenstown-dublin" to 250.7,
        "leg-23-dublin-liverpool" to 220.0,
        "leg-24-liverpool-london" to 290.0,
    )

    @Test
    fun `dataset version is pinned`() {
        assertEquals(1, RouteDatasetV1.version)
    }

    @Test
    fun `every leg length matches the measured regression value`() {
        assertEquals(expectedKm.size, measures.size)
        for (m in measures) {
            val expected = expectedKm.getValue(m.leg.id)
            assertEquals(
                "leg ${m.leg.id}: expected $expected km, got ${m.measuredKm}",
                expected, m.measuredKm, expected * 0.01,
            )
        }
    }

    @Test
    fun `total length matches the measured regression value`() {
        assertEquals(40501.8, RouteMeasure.totalKm(), 40501.8 * 0.01)
    }

    @Test
    fun `cumulative distance is strictly increasing`() {
        val cumulative = RouteMeasure.cumulativeKm()
        assertEquals(measures.size, cumulative.size)
        for (i in 1 until cumulative.size) {
            assertTrue(
                "cumulative[$i]=${cumulative[i]} must exceed cumulative[${i - 1}]=${cumulative[i - 1]}",
                cumulative[i] > cumulative[i - 1],
            )
        }
        assertEquals(RouteMeasure.totalKm(), cumulative.last(), 1e-6)
    }

    @Test
    fun `all legs have positive measured length and detour ratio at least 1`() {
        for (m in measures) {
            assertTrue("leg ${m.leg.id} has zero length", m.measuredKm > 0.0)
            assertTrue(
                "leg ${m.leg.id} detour ratio ${m.detourRatio} < 1",
                m.detourRatio >= 1.0 - 1e-9,
            )
        }
    }

    @Test
    fun `sea legs detour at most 40 percent`() {
        for (m in measures) {
            if (m.leg.transport == Transport.SHIP) {
                assertTrue(
                    "leg ${m.leg.id} detour ratio ${m.detourRatio} looks broken",
                    m.detourRatio <= 1.40,
                )
            }
        }
    }

    @Test
    fun `pacific crossing handles the antimeridian without anomalies`() {
        val m = byId.getValue("leg-16-yokohama-san-francisco")
        assertTrue(m.measuredKm.isFinite())
        assertEquals(8589.6, m.measuredKm, 8589.6 * 0.02)
        // The polyline crosses +-180 between pacific-3 (172E) and pacific-4 (176W).
        val polyline = RouteDatasetV1.polylineOf(m.leg)
        val i3 = polyline.indexOfFirst { it.id == "pacific-3" }
        val i4 = polyline.indexOfFirst { it.id == "pacific-4" }
        assertTrue(i3 >= 0 && i4 == i3 + 1)
        val a = polyline[i3]
        val b = polyline[i4]
        assertTrue(a.lon > 170.0 && b.lon < -170.0)
        // The crossing segment is a normal ~1000 km arc, not a map-wide artifact.
        val seg = distanceKm(a.lat, a.lon, b.lat, b.lon)
        assertTrue("antimeridian segment $seg km", seg in 500.0..1500.0)
        // No segment of the leg may be a "long way around" artifact (> 60 deg).
        for (i in 0 until polyline.size - 1) {
            val from = polyline[i]
            val to = polyline[i + 1]
            assertTrue(
                "segment ${from.id} -> ${to.id} too long",
                centralAngleDeg(from.lat, from.lon, to.lat, to.lon) < 60.0,
            )
        }
    }

    @Test
    fun `distance edge cases`() {
        // Zero-length leg: same point twice.
        assertEquals(0.0, distanceKm(51.5, 0.0, 51.5, 0.0), 1e-9)
        // Duplicate consecutive nodes contribute nothing.
        val withDup = listOf(50.0 to 0.0, 50.0 to 0.0, 51.0 to 0.0, 51.0 to 0.0)
        val withoutDup = listOf(50.0 to 0.0, 51.0 to 0.0)
        assertEquals(polylineLengthKm(withoutDup), polylineLengthKm(withDup), 1e-9)
        // Empty and single-point polylines are zero.
        assertEquals(0.0, polylineLengthKm(emptyList()), 1e-12)
        assertEquals(0.0, polylineLengthKm(listOf(10.0 to 20.0)), 1e-12)
        // Antipodal-ish: half the equator is ~20015 km regardless of direction.
        assertEquals(20015.0, distanceKm(0.0, 0.0, 0.0, 180.0), 1.0)
    }

    @Test
    fun `sledge leg is a chord and elephant leg carries the explicit multiplier`() {
        val sledge = byId.getValue("leg-19-fort-kearney-omaha")
        assertEquals(1.0, sledge.detourRatio, 1e-9)
        assertEquals(Transport.SLEDGE, sledge.leg.transport)
        val elephant = byId.getValue("leg-10-kholby-allahabad")
        assertEquals(Transport.ELEPHANT, elephant.leg.transport)
        assertEquals(1.25, elephant.leg.pathMultiplier, 1e-12)
        assertEquals(1.25, elephant.detourRatio, 1e-9)
    }

    @Test
    fun `draft row grouping covers all legs and matches draft order of magnitude`() {
        val rows = RouteMeasure.totalsByDraftRow()
        assertEquals(10, rows.size)
        assertTrue(rows.all { (_, km, legs) -> legs.isNotEmpty() && km > 0.0 })
        val sum = rows.sumOf { it.second }
        assertEquals(RouteMeasure.totalKm(), sum, 1e-6)
    }

    @Test
    fun `known reference distance paris london is sane`() {
        // Paris-London great circle is ~344 km.
        val d = distanceKm(48.8566, 2.3522, 51.5074, -0.1278)
        assertEquals(344.0, d, 5.0)
        // Latitude degrees are ~111.2 km on the sphere at the equator meridian.
        assertEquals(111.19, distanceKm(0.0, 0.0, 1.0, 0.0), 0.01)
        // Longitude degree at lat 60 is half of that.
        assertEquals(111.19 * cos(Math.toRadians(60.0)), distanceKm(60.0, 0.0, 60.0, 1.0), 0.5)
    }
}
