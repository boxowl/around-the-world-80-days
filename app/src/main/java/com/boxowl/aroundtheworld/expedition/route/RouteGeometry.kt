package com.boxowl.aroundtheworld.expedition.route

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Pure spherical math for the versioned route geometry dataset (V2.1b).
 * No Android dependencies: everything here is JVM-unit-testable.
 *
 * Earth model: sphere with [EARTH_RADIUS_KM]. Leg lengths are polyline sums of
 * great-circle (orthodrome) segments between dataset nodes — see
 * docs/design/v2-route-geometry.md for the methodology.
 */

/** Mean Earth radius in kilometres; the only fixed physical constant of the dataset. */
const val EARTH_RADIUS_KM = 6371.0

/** Internal unit 3D vector (ECEF-style: z toward the north pole). */
internal data class SVec(val x: Double, val y: Double, val z: Double) {
    infix fun dot(o: SVec): Double = x * o.x + y * o.y + z * o.z
}

internal fun sVecOf(latDeg: Double, lonDeg: Double): SVec {
    val lat = Math.toRadians(latDeg)
    val lon = Math.toRadians(lonDeg)
    val c = cos(lat)
    return SVec(c * cos(lon), c * sin(lon), sin(lat))
}

internal fun crossOf(a: SVec, b: SVec) = SVec(
    a.y * b.z - a.z * b.y,
    a.z * b.x - a.x * b.z,
    a.x * b.y - a.y * b.x,
)

/**
 * Central angle in degrees between two surface points, via the numerically
 * stable atan2(|a x b|, a . b) form. Handles the antimeridian naturally.
 */
fun centralAngleDeg(aLat: Double, aLon: Double, bLat: Double, bLon: Double): Double {
    val a = sVecOf(aLat, aLon)
    val b = sVecOf(bLat, bLon)
    val cx = crossOf(a, b)
    val crossNorm = sqrt(cx.x * cx.x + cx.y * cx.y + cx.z * cx.z)
    return Math.toDegrees(atan2(crossNorm, a dot b))
}

/** Great-circle distance in kilometres between two surface points. */
fun distanceKm(aLat: Double, aLon: Double, bLat: Double, bLon: Double): Double =
    Math.toRadians(centralAngleDeg(aLat, aLon, bLat, bLon)) * EARTH_RADIUS_KM

/** Length of a polyline of (lat, lon) points as the sum of great-circle segments. */
fun polylineLengthKm(points: List<Pair<Double, Double>>): Double {
    var acc = 0.0
    for (i in 0 until points.size - 1) {
        val a = points[i]
        val b = points[i + 1]
        if (abs(a.first - b.first) < 1e-12 && abs(a.second - b.second) < 1e-12) continue
        acc += distanceKm(a.first, a.second, b.first, b.second)
    }
    return acc
}
