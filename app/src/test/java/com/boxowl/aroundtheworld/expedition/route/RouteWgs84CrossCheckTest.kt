package com.boxowl.aroundtheworld.expedition.route

import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Cross-check of the spherical metric against WGS84 geodesy: Vincenty's
 * inverse formula (test-local implementation) applied to every segment of
 * every leg polyline. The ellipsoidal total must agree with the spherical
 * total within 1% — the sphere is our documented metric, this test guards
 * against a systematic model error.
 */
class RouteWgs84CrossCheckTest {

    // WGS84 ellipsoid.
    private val a = 6378137.0
    private val f = 1.0 / 298.257223563
    private val b = a * (1.0 - f)

    /** Vincenty inverse: geodesic distance in km. Segments here are short; no antipodal case. */
    private fun vincentyKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        if (abs(lat1 - lat2) < 1e-15 && abs(lon1 - lon2) < 1e-15) return 0.0
        val l = Math.toRadians(lon2 - lon1)
        val u1 = atan((1 - f) * tan(Math.toRadians(lat1)))
        val u2 = atan((1 - f) * tan(Math.toRadians(lat2)))
        val sinU1 = sin(u1)
        val cosU1 = cos(u1)
        val sinU2 = sin(u2)
        val cosU2 = cos(u2)

        var lambda = l
        var sinSigma = 0.0
        var cosSigma = 0.0
        var sigma = 0.0
        var cosSqAlpha = 0.0
        var cos2SigmaM = 0.0
        repeat(100) {
            val sinL = sin(lambda)
            val cosL = cos(lambda)
            sinSigma = sqrt(
                (cosU2 * sinL) * (cosU2 * sinL) +
                    (cosU1 * sinU2 - sinU1 * cosU2 * cosL) * (cosU1 * sinU2 - sinU1 * cosU2 * cosL),
            )
            if (sinSigma == 0.0) return 0.0
            cosSigma = sinU1 * sinU2 + cosU1 * cosU2 * cosL
            sigma = atan2(sinSigma, cosSigma)
            val sinAlpha = cosU1 * cosU2 * sinL / sinSigma
            cosSqAlpha = 1 - sinAlpha * sinAlpha
            cos2SigmaM = if (cosSqAlpha != 0.0) cosSigma - 2 * sinU1 * sinU2 / cosSqAlpha else 0.0
            val c = f / 16 * cosSqAlpha * (4 + f * (4 - 3 * cosSqAlpha))
            val prev = lambda
            lambda = l + (1 - c) * f * sinAlpha * (
                sigma + c * sinSigma * (cos2SigmaM + c * cosSigma * (-1 + 2 * cos2SigmaM * cos2SigmaM))
                )
            if (abs(lambda - prev) < 1e-12) return@repeat
        }
        val uSq = cosSqAlpha * (a * a - b * b) / (b * b)
        val bigA = 1 + uSq / 16384 * (4096 + uSq * (-768 + uSq * (320 - 175 * uSq)))
        val bigB = uSq / 1024 * (256 + uSq * (-128 + uSq * (74 - 47 * uSq)))
        val deltaSigma = bigB * sinSigma * (
            cos2SigmaM + bigB / 4 * (
                cosSigma * (-1 + 2 * cos2SigmaM * cos2SigmaM) -
                    bigB / 6 * cos2SigmaM * (-3 + 4 * sinSigma * sinSigma) *
                    (-3 + 4 * cos2SigmaM * cos2SigmaM)
                )
            )
        return b * bigA * (sigma - deltaSigma) / 1000.0
    }

    @Test
    fun `vincenty self-check on reference distances`() {
        // Paris -> London WGS84 geodesic is ~343.9 km.
        assertEquals(343.9, vincentyKm(48.8566, 2.3522, 51.5074, -0.1278), 1.5)
        // Equator quarter is ~10018.8 km on WGS84.
        assertEquals(10018.8, vincentyKm(0.0, 0.0, 0.0, 90.0), 1.0)
    }

    @Test
    fun `wgs84 total agrees with spherical total within 1 percent`() {
        var wgs84 = 0.0
        for (leg in RouteDatasetV1.legs) {
            val nodes = RouteDatasetV1.polylineOf(leg)
            var legKm = 0.0
            for (i in 0 until nodes.size - 1) {
                legKm += vincentyKm(nodes[i].lat, nodes[i].lon, nodes[i + 1].lat, nodes[i + 1].lon)
            }
            wgs84 += legKm * leg.pathMultiplier
        }
        val sphere = RouteMeasure.totalKm()
        val diff = abs(wgs84 - sphere) / sphere
        println("WGS84 total: ${"%.1f".format(wgs84)} km; sphere: ${"%.1f".format(sphere)} km; diff: ${"%.4f".format(diff * 100)}%")
        assertTrue(
            "WGS84 total ${"%.1f".format(wgs84)} km vs sphere ${"%.1f".format(sphere)} km: $diff",
            diff < 0.01,
        )
    }
}
