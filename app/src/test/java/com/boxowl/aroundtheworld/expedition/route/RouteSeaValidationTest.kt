package com.boxowl.aroundtheworld.expedition.route

import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Sea-leg validation: no SHIP/FERRY leg of RouteDatasetV1 may cross land.
 *
 * Land data: app/src/debug/assets/ne_land.bin (Natural Earth ne_50m_land,
 * public domain; 16-bit quantized, zigzag-varint delta rings — same format as
 * the debug-globe CoastlineData, decoded here by a minimal independent
 * decoder because tests must not depend on the debug source set).
 *
 * Point-in-polygon on the sphere: meridian ray casting. For a query point P we
 * walk the meridian of P to the north pole and count crossings with each ring
 * edge (great-circle arcs); even-odd parity over ALL rings (exteriors and
 * holes alike) decides land vs water. This handles the Caspian (a hole) and
 * avoids the winding-number pitfalls near antipodes. Degenerate configurations
 * (edge endpoint exactly on the test meridian) are dissolved by shifting the
 * test longitude by EPSILON_LON.
 *
 * Sampling: each sea leg is slerp-sampled at <= SAMPLE_STEP_DEG. Samples within
 * NEAR_NODE_DEG of a STOP/PORT/CALL node are not checked (ports sit on land by
 * definition; CALL is exempt too — Nagasaki is a port call whose bay is closed
 * by the 50 m simplification). WATERWAY legs (Suez canal, Hooghly river) are
 * not validated: those waterways are below map resolution by definition.
 *
 * Exceptions: only the explicit list below, each with a reason.
 */
class RouteSeaValidationTest {

    // ---------- minimal ne_land.bin decoder (test-local, lat/lon output) ----------

    private class Ring(val isHole: Boolean, val lat: DoubleArray, val lon: DoubleArray) {
        val size: Int get() = lat.size
    }

    private fun decodeRings(input: InputStream): List<Ring> {
        val buf = input.readBytes()
        require(buf.size >= 5) { "asset too small" }
        require(
            buf[0] == 'N'.code.toByte() && buf[1] == 'E'.code.toByte() &&
                buf[2] == 'L'.code.toByte() && buf[3] == '1'.code.toByte(),
        ) { "bad magic" }
        var pos = 4

        fun uv(): Int {
            var r = 0
            var s = 0
            while (true) {
                val b = buf[pos++].toInt() and 0xFF
                r = r or ((b and 0x7F) shl s)
                if (b and 0x80 == 0) return r
                s += 7
            }
        }

        fun sv(): Int {
            val u = uv()
            return if (u and 1 == 0) u ushr 1 else -((u ushr 1) + 1)
        }

        val rings = ArrayList<Ring>()
        val polygonCount = uv()
        repeat(polygonCount) {
            val ringCount = uv()
            repeat(ringCount) {
                val isHole = uv() and 1 != 0
                val count = uv()
                require(count >= 3) { "degenerate ring" }
                var qLon = sv().let { if (it < 0) it + 65536 else it }
                var qLat = sv() + 32768
                val lat = DoubleArray(count)
                val lon = DoubleArray(count)
                fun dequant(i: Int) {
                    lon[i] = qLon / 65535.0 * 360.0 - 180.0
                    lat[i] = qLat.coerceIn(0, 65535) / 65535.0 * 180.0 - 90.0
                }
                dequant(0)
                for (i in 1 until count) {
                    qLon = (qLon + sv()) and 0xFFFF
                    qLat += sv()
                    dequant(i)
                }
                rings.add(Ring(isHole, lat, lon))
            }
        }
        return rings
    }

    // ---------- spherical point-in-polygon via meridian ray casting ----------

    private data class V3(val x: Double, val y: Double, val z: Double) {
        infix fun dot(o: V3) = x * o.x + y * o.y + z * o.z
    }

    private fun v3(latDeg: Double, lonDeg: Double): V3 {
        val lat = Math.toRadians(latDeg)
        val lon = Math.toRadians(lonDeg)
        val c = cos(lat)
        return V3(c * cos(lon), c * sin(lon), sin(lat))
    }

    /** Decoded ring: unit vectors plus the source lat/lon (needed for exact degeneracy checks). */
    private class RingPts(val isHole: Boolean, val lat: DoubleArray, val lon: DoubleArray, val vecs: List<V3>)

    /** Rings as unit vectors, decoded once. */
    private val rings: List<RingPts> by lazy {
        val file = File("src/debug/assets/ne_land.bin")
        assertTrue("asset missing: ${file.absolutePath}", file.exists())
        FileInputStream(file).use { stream ->
            decodeRings(stream).map { ring ->
                RingPts(
                    ring.isHole, ring.lat, ring.lon,
                    (0 until ring.size).map { v3(ring.lat[it], ring.lon[it]) },
                )
            }
        }
    }

    private var lastShiftDeg = 0.0

    /** True when (latDeg, lonDeg) is on land per ne_50m_land. */
    private fun isLand(latDeg: Double, lonDeg: Double): Boolean {
        var lon = lonDeg
        for (attempt in 0..2) {
            lastShiftDeg = lon - lonDeg
            rayCast(latDeg, lon)?.let { return it }
            lon += EPSILON_LON
        }
        error("degenerate ray even after longitude shifts at ($latDeg, $lonDeg)")
    }

    /**
     * Casts the meridian ray north from (lat, lon). Returns null when an edge
     * touches the test meridian exactly (degenerate — caller retries shifted).
     */
    private fun rayCast(latDeg: Double, lonDeg: Double): Boolean? {
        val lonRad = Math.toRadians(lonDeg)
        val m = V3(-sin(lonRad), cos(lonRad), 0.0) // meridian plane normal
        var inside = false
        for (ring in rings) {
            val vecs = ring.vecs
            val n = vecs.size
            for (i in 0 until n) {
                // Degenerate: a vertex exactly on the test meridian (checked by the
                // source longitude — the dot product alone misfires at the poles,
                // where cos(lat) ~ 6e-17 puts every meridian "on" the vertex).
                val vLon = ring.lon[i]
                if (abs(lonDiffDeg(vLon, lonDeg)) < 1e-9 ||
                    abs(lonDiffDeg(vLon + 180.0, lonDeg)) < 1e-9
                ) {
                    return null
                }
                val a = vecs[i]
                val b = vecs[(i + 1) % n]
                val da = a dot m
                val db = b dot m
                if (da > 0.0 && db > 0.0) continue // arc stays off the plane
                if (da < 0.0 && db < 0.0) continue
                // Sign change: the arc crosses the meridian plane once.
                val t = da / (da - db)
                val cx = a.x + (b.x - a.x) * t
                val cy = a.y + (b.y - a.y) * t
                val cz = a.z + (b.z - a.z) * t
                val cLon = Math.toDegrees(atan2(cy, cx))
                // Only the intersection on our own meridian counts, not lon+180.
                if (abs(lonDiffDeg(cLon, lonDeg)) > 90.0) continue
                val cLat = Math.toDegrees(atan2(cz, sqrt(cx * cx + cy * cy)))
                if (cLat > latDeg) inside = !inside
            }
        }
        return inside
    }

    private fun lonDiffDeg(a: Double, b: Double): Double {
        var d = (a - b) % 360.0
        if (d > 180.0) d -= 360.0
        if (d < -180.0) d += 360.0
        return d
    }

    // ---------- slerp sampling of legs ----------

    private fun slerp(a: V3, b: V3, t: Double): V3 {
        val d = (a dot b).coerceIn(-1.0, 1.0)
        val omega = acos(d)
        if (omega < 1e-9) return a
        val so = sin(omega)
        val ka = sin((1.0 - t) * omega) / so
        val kb = sin(t * omega) / so
        return V3(a.x * ka + b.x * kb, a.y * ka + b.y * kb, a.z * ka + b.z * kb)
    }

    private fun angularDeg(a: V3, b: V3): Double =
        Math.toDegrees(acos((a dot b).coerceIn(-1.0, 1.0)))

    private data class ValidationException(
        val lat: Double,
        val lon: Double,
        val radiusDeg: Double,
        val reason: String,
    )

    /**
     * Explicit exceptions. Empty unless a simplification artifact cannot be
     * fixed by a waypoint; each entry needs a literary/technical reason.
     */
    private val exceptions: List<ValidationException> = listOf()

    private val nearNodes: List<V3> by lazy {
        RouteDatasetV1.nodes
            .filter { it.role == NodeRole.STOP || it.role == NodeRole.PORT || it.role == NodeRole.CALL }
            .map { v3(it.lat, it.lon) }
    }

    private fun isExempt(v: V3): Boolean {
        if (nearNodes.any { angularDeg(v, it) < NEAR_NODE_DEG }) return true
        return exceptions.any { angularDeg(v, v3(it.lat, it.lon)) < it.radiusDeg }
    }

    // ---------- tests ----------

    @Test
    fun `validator anchors — land and water reference points`() {
        // Land anchors.
        assertTrue("Paris must be land", isLand(48.8566, 2.3522))
        assertTrue("Delhi must be land", isLand(28.6139, 77.2090))
        assertTrue("Sydney must be land", isLand(-33.8688, 151.2093))
        // Ocean anchors.
        assertTrue("mid-Pacific must be water", !isLand(0.0, -150.0))
        assertTrue("south Atlantic must be water", !isLand(-30.0, -45.0))
        assertTrue("Indian Ocean must be water", !isLand(-20.0, 80.0))
        // The Caspian is a hole in ne_50m_land: must read as water.
        assertTrue("Caspian must be water (hole in the data)", !isLand(42.5, 50.5))
    }

    @Test
    fun `sea legs never cross land`() {
        val failures = StringBuilder()
        var checked = 0
        for (leg in RouteDatasetV1.legs) {
            if (leg.transport != Transport.SHIP && leg.transport != Transport.FERRY) continue
            val nodes = RouteDatasetV1.polylineOf(leg)
            for (i in 0 until nodes.size - 1) {
                val a = v3(nodes[i].lat, nodes[i].lon)
                val b = v3(nodes[i + 1].lat, nodes[i + 1].lon)
                val segDeg = angularDeg(a, b)
                val steps = maxOf(1, ceil(segDeg / SAMPLE_STEP_DEG).toInt())
                for (s in 0..steps) {
                    val p = slerp(a, b, s.toDouble() / steps)
                    if (isExempt(p)) continue
                    val lat = Math.toDegrees(atan2(p.z, sqrt(p.x * p.x + p.y * p.y)))
                    val lon = Math.toDegrees(atan2(p.y, p.x))
                    checked++
                    if (isLand(lat, lon)) {
                        failures.appendLine(
                            "${leg.id}: land at (%.3f, %.3f) between %s and %s".format(
                                lat, lon, nodes[i].id, nodes[i + 1].id,
                            ),
                        )
                    }
                }
            }
        }
        assertTrue("checked $checked sea samples, land hits:\n$failures", failures.isEmpty())
    }

    private companion object {
        const val SAMPLE_STEP_DEG = 0.1
        const val NEAR_NODE_DEG = 0.4
        const val EPSILON_LON = 0.0023
    }
}
