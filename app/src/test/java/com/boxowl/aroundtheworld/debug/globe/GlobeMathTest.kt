package com.boxowl.aroundtheworld.debug.globe

import java.io.File
import java.io.FileInputStream
import kotlin.math.abs
import kotlin.math.sqrt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM tests of the pure globe math (app/src/debug has no Android deps in
 * GlobeMath/CoastlineData). Run with :app:testDebugUnitTest.
 */
class GlobeMathTest {

    @Test
    fun `center projects to origin and is visible`() {
        val frame = GlobeFrame(30.0, 40.0)
        val p = latLonToVec(30.0, 40.0)
        assertTrue(frame.isVisible(p))
        val proj = frame.project(p)
        assertEquals(0.0, proj.x, 1e-9)
        assertEquals(0.0, proj.y, 1e-9)
    }

    @Test
    fun `antipode is not visible`() {
        val frame = GlobeFrame(30.0, 40.0)
        val antipode = latLonToVec(-30.0, 40.0 + 180.0)
        assertFalse(frame.isVisible(antipode))
        // Point 45 deg away is visible, 135 deg away is not.
        assertTrue(frame.isVisible(latLonToVec(30.0, 40.0 + 45.0)))
        assertFalse(frame.isVisible(latLonToVec(30.0, 40.0 + 135.0)))
    }

    @Test
    fun `center latitude is clamped to +-60`() {
        assertEquals(60.0, clampCenterLat(80.0), 1e-9)
        assertEquals(-60.0, clampCenterLat(-80.0), 1e-9)
        assertEquals(12.5, clampCenterLat(12.5), 1e-9)
    }

    @Test
    fun `slerp across antimeridian passes through 180 not through 0`() {
        val a = latLonToVec(40.0, 170.0)
        val b = latLonToVec(40.0, -170.0)
        val mid = slerp(a, b, 0.5)
        val (lat, lon) = vecToLatLon(mid)
        assertTrue("mid lon must be near +-180, was $lon", abs(lon) > 175.0)
        // Great circle between equal-latitude points bows toward the pole.
        assertTrue("mid lat $lat should be >= 40", lat >= 40.0 - 1e-6)
        // No point along the arc may sit near lon 0 (the "long way" across the map).
        for (i in 0..20) {
            val (_, l) = vecToLatLon(slerp(a, b, i / 20.0))
            assertTrue("arc left the antimeridian region: lon=$l at t=${i / 20.0}", abs(l) > 160.0)
        }
    }

    @Test
    fun `clip ends fragments exactly on the horizon`() {
        val frame = GlobeFrame(0.0, 0.0)
        val visible = latLonToVec(0.0, 45.0)
        val hidden = latLonToVec(0.0, 135.0)
        val fragments = clipToFront(listOf(visible, hidden), frame)
        assertEquals(1, fragments.size)
        val frag = fragments[0]
        assertEquals(2, frag.size)
        val horizon = frag.last()
        assertEquals(0.0, horizon dot frame.center, 1e-9)
        // The horizon point lies on the rim of the unit projection disk.
        val p = frame.project(horizon)
        assertEquals(1.0, sqrt(p.x * p.x + p.y * p.y), 1e-9)
    }

    @Test
    fun `hidden runs are dropped from the middle of a polyline`() {
        val frame = GlobeFrame(0.0, 0.0)
        val line = listOf(
            latLonToVec(0.0, 30.0),
            latLonToVec(0.0, 60.0),
            latLonToVec(0.0, 120.0), // back side
            latLonToVec(0.0, 150.0), // back side
            latLonToVec(0.0, -60.0),
            latLonToVec(0.0, -30.0),
        )
        val fragments = clipToFront(line, frame)
        assertEquals(2, fragments.size)
        assertTrue(fragments.all { f -> f.all { frame.isVisible(it) || abs(it dot frame.center) < 1e-9 } })
    }

    @Test
    fun `stitched fill matches the synthetic continent`() {
        // Continent: circle of 60 deg around (0N, 0E); view center (0N, 70E) puts
        // part of the ring on the back side, forcing horizon stitching.
        val ring = syntheticRing(0.0, 0.0, 60.0, stepDeg = 2.0)
        val frame = GlobeFrame(0.0, 70.0)
        val closed = ring + ring.first()
        val fragments = clipToFront(closed, frame)
            .filter { it.size >= 2 }
            .map { frag -> DiskFragment(frag.map { frame.project(it) }) }
        assertTrue("continent must cross the horizon", fragments.isNotEmpty())
        val loops = stitchFragments(fragments)
        assertTrue(loops.isNotEmpty())

        fun inside(lat: Double, lon: Double): Boolean {
            val v = latLonToVec(lat, lon)
            assertTrue("test point must be on the front side", frame.isVisible(v))
            return isInsideLoops(loops, frame.project(v))
        }

        // Continent center is visible and must be filled.
        assertTrue(inside(0.0, 0.0))
        assertTrue(inside(40.0, 30.0))
        assertTrue(inside(-40.0, 30.0))
        // Visible points far outside the continent must stay ocean.
        assertFalse(inside(0.0, 120.0))
        assertFalse(inside(-30.0, 120.0))
        assertFalse(inside(50.0, 110.0))
        assertFalse(inside(0.0, 150.0))
    }

    @Test
    fun `two separate islands stitch independently`() {
        val frame = GlobeFrame(0.0, 0.0)
        // Two islands both crossing the horizon on opposite sides of the disk.
        val islandA = syntheticRing(0.0, 60.0, 50.0, stepDeg = 2.0)
        val islandB = syntheticRing(0.0, -60.0, 50.0, stepDeg = 2.0)
        val fragments = mutableListOf<DiskFragment>()
        for (ring in listOf(islandA, islandB)) {
            val closed = ring + ring.first()
            clipToFront(closed, frame)
                .filter { it.size >= 2 }
                .mapTo(fragments) { frag -> DiskFragment(frag.map { frame.project(it) }) }
        }
        assertTrue(fragments.size >= 2)
        val loops = stitchFragments(fragments)
        assertTrue(inside(loops, frame, 0.0, 60.0))
        assertTrue(inside(loops, frame, 0.0, -60.0))
        assertFalse(inside(loops, frame, 0.0, 0.0))
        assertFalse(inside(loops, frame, 60.0, 0.0))
        assertFalse(inside(loops, frame, -60.0, 0.0))
    }

    private fun inside(loops: List<List<P2>>, frame: GlobeFrame, lat: Double, lon: Double): Boolean {
        val v = latLonToVec(lat, lon)
        if (!frame.isVisible(v)) return false
        return isInsideLoops(loops, frame.project(v))
    }

    @Test
    fun `asset decodes into unit vectors within valid lat range`() {
        val file = File("src/debug/assets/ne_land.bin")
        assertTrue("asset missing: ${file.absolutePath}", file.exists())
        val rings = FileInputStream(file).use { CoastlineData.decode(it) }
        assertTrue(rings.size > 100)
        assertTrue(rings.count { it.isHole } >= 1)
        for (ring in rings) {
            assertTrue(ring.points.size >= 3)
            for (p in ring.points) {
                assertEquals(1.0, p.norm(), 1e-3)
                val (lat, _) = vecToLatLon(p)
                assertTrue(lat in -90.1..90.1)
            }
        }
    }
}
