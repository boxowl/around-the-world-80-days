package com.boxowl.aroundtheworld.debug.globe

import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Pure spherical math for the V21 debug globe prototype.
 * No Android dependencies: everything here is JVM-unit-testable.
 */

data class Vec3(val x: Double, val y: Double, val z: Double) {
    operator fun plus(o: Vec3) = Vec3(x + o.x, y + o.y, z + o.z)
    operator fun minus(o: Vec3) = Vec3(x - o.x, y - o.y, z - o.z)
    operator fun times(s: Double) = Vec3(x * s, y * s, z * s)
    infix fun dot(o: Vec3): Double = x * o.x + y * o.y + z * o.z
    fun norm(): Double = sqrt(this dot this)
    fun normalized(): Vec3 {
        val n = norm()
        return Vec3(x / n, y / n, z / n)
    }
}

/** 2D point in math coords (x right, y up) on the unit projection disk. */
data class P2(val x: Double, val y: Double)

/** View-center latitude is clamped to +-60 deg so polar distortion stays sane. */
const val MAX_CENTER_LAT = 60.0

fun clampCenterLat(latDeg: Double): Double = latDeg.coerceIn(-MAX_CENTER_LAT, MAX_CENTER_LAT)

fun latLonToVec(latDeg: Double, lonDeg: Double): Vec3 {
    val lat = Math.toRadians(latDeg)
    val lon = Math.toRadians(lonDeg)
    val c = cos(lat)
    return Vec3(c * cos(lon), c * sin(lon), sin(lat))
}

/** Returns (latDeg, lonDeg), lon in (-180, 180]. */
fun vecToLatLon(v: Vec3): Pair<Double, Double> {
    val n = v.normalized()
    return Math.toDegrees(asin(n.z)) to Math.toDegrees(atan2(n.y, n.x))
}

/** Orthographic view frame for a given view center. */
class GlobeFrame(centerLatDeg: Double, centerLonDeg: Double) {
    val center: Vec3 = latLonToVec(centerLatDeg, centerLonDeg)
    val east: Vec3 = run {
        val lon = Math.toRadians(centerLonDeg)
        Vec3(-sin(lon), cos(lon), 0.0)
    }
    val north: Vec3 = run {
        val lat = Math.toRadians(centerLatDeg)
        val lon = Math.toRadians(centerLonDeg)
        Vec3(-sin(lat) * cos(lon), -sin(lat) * sin(lon), cos(lat))
    }

    /** A point is on the visible hemisphere when the central angle c satisfies cos c > 0. */
    fun isVisible(p: Vec3): Boolean = (p dot center) > 0.0

    /** Orthographic projection onto the unit disk, math coords (y up). */
    fun project(p: Vec3): P2 = P2(p dot east, p dot north)
}

/** Great-circle interpolation; handles the antimeridian naturally via 3D vectors. */
fun slerp(a: Vec3, b: Vec3, t: Double): Vec3 {
    val d = (a dot b).coerceIn(-1.0, 1.0)
    val omega = acos(d)
    if (omega < 1e-9) return a
    val so = sin(omega)
    return a * (sin((1.0 - t) * omega) / so) + b * (sin(t * omega) / so)
}

/** Point where segment a(front)/b(back) crosses the horizon (dot with center == 0). */
fun horizonPoint(a: Vec3, b: Vec3, center: Vec3): Vec3 {
    val da = a dot center
    val db = b dot center
    val t = da / (da - db)
    return (a + (b - a) * t).normalized()
}

/**
 * Splits an open polyline of unit vectors into the fragments lying on the front
 * hemisphere. Back-side runs are dropped; fragment ends land exactly on the horizon.
 * Short segments assumed (both-invisible segments never re-enter the front).
 */
fun clipToFront(points: List<Vec3>, frame: GlobeFrame): List<List<Vec3>> {
    val fragments = mutableListOf<List<Vec3>>()
    var current: MutableList<Vec3>? = null
    for (i in points.indices) {
        val p = points[i]
        if (frame.isVisible(p)) {
            if (current == null) {
                current = mutableListOf()
                if (i > 0) current.add(horizonPoint(points[i - 1], p, frame.center))
                fragments.add(current)
            }
            current.add(p)
        } else if (current != null) {
            current.add(horizonPoint(points[i - 1], p, frame.center))
            current = null
        }
    }
    return fragments
}

private const val TWO_PI = 2.0 * PI

private fun angleOf(p: P2): Double {
    val a = atan2(p.y, p.x)
    return if (a < 0.0) a + TWO_PI else a
}

/** A clipped ring fragment projected onto the unit disk; endpoints sit on the rim. */
class DiskFragment(val points: List<P2>) {
    val startAngle: Double = angleOf(points.first())
    val endAngle: Double = angleOf(points.last())
}

/**
 * Stitches clipped exterior-ring fragments into closed loops by walking the rim
 * counter-clockwise from each fragment end to the next fragment start (the d3-style
 * pairing for consistently oriented rings; Natural Earth exteriors are CCW).
 * Rim arcs are sampled with [arcStepDeg] chords.
 */
fun stitchFragments(fragments: List<DiskFragment>, arcStepDeg: Double = 3.0): List<List<P2>> {
    if (fragments.isEmpty()) return emptyList()
    val sorted = fragments.sortedBy { it.startAngle }
    val n = sorted.size
    val visited = BooleanArray(n)
    val loops = mutableListOf<List<P2>>()
    for (i in 0 until n) {
        if (visited[i]) continue
        val loop = mutableListOf<P2>()
        var cur = i
        while (cur >= 0 && !visited[cur]) {
            visited[cur] = true
            val f = sorted[cur]
            loop.addAll(f.points)
            var best = -1
            var bestGap = Double.MAX_VALUE
            for (g in 0 until n) {
                if (visited[g] && g != i) continue
                val gap = (sorted[g].startAngle - f.endAngle).mod(TWO_PI)
                if (gap < bestGap - 1e-12) {
                    bestGap = gap
                    best = g
                }
            }
            if (best < 0) break
            val steps = max(1, ceil(Math.toDegrees(bestGap) / arcStepDeg).toInt())
            for (s in 1..steps) {
                val a = f.endAngle + bestGap * s / steps
                loop.add(P2(cos(a), sin(a)))
            }
            cur = best
        }
        loops.add(loop)
    }
    return loops
}

/** Even-odd point-in-polygon over stitched 2D loops (unit-disk coords). Used by tests. */
fun isInsideLoops(loops: List<List<P2>>, p: P2): Boolean {
    var inside = false
    for (loop in loops) {
        for (i in loop.indices) {
            val a = loop[i]
            val b = loop[(i + 1) % loop.size]
            if ((a.y > p.y) != (b.y > p.y)) {
                val xCross = a.x + (p.y - a.y) / (b.y - a.y) * (b.x - a.x)
                if (p.x < xCross) inside = !inside
            }
        }
    }
    return inside
}

/** Convenience: angular distance in degrees between two unit vectors. */
fun angularDistanceDeg(a: Vec3, b: Vec3): Double =
    Math.toDegrees(acos((a dot b).coerceIn(-1.0, 1.0)))

fun cross(a: Vec3, b: Vec3) = Vec3(
    a.y * b.z - a.z * b.y,
    a.z * b.x - a.x * b.z,
    a.x * b.y - a.y * b.x,
)

/** Samples a circle of [radiusDeg] angular radius around ([latDeg], [lonDeg]).
 *  Ring comes out CCW in the lon/lat plane, like Natural Earth exteriors. Used by tests. */
fun syntheticRing(latDeg: Double, lonDeg: Double, radiusDeg: Double, stepDeg: Double = 5.0): List<Vec3> {
    val center = latLonToVec(latDeg, lonDeg)
    val east = Vec3(-center.y, center.x, 0.0).let { if (it.norm() < 1e-9) Vec3(1.0, 0.0, 0.0) else it.normalized() }
    val north = cross(center, east).normalized()
    val r = Math.toRadians(radiusDeg)
    val pts = mutableListOf<Vec3>()
    var bearing = 0.0
    while (bearing < 360.0 - 1e-9) {
        val br = Math.toRadians(bearing)
        val p = center * cos(r) + (north * cos(br) + east * sin(br)) * sin(r)
        pts.add(p.normalized())
        bearing += stepDeg
    }
    pts.reverse() // bearings sample clockwise on the map; exteriors must be CCW
    return pts
}
