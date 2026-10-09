package com.boxowl.aroundtheworld.debug.globe

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import kotlin.math.min

/**
 * Builds the frame-independent geometry of one globe frame as Compose paths.
 * Pure layout step: the Activity recomputes it on every state change and the
 * Canvas just paints it, which also gives hit testing the same projections.
 */
class GlobeScene(
    private val rings: List<CoastlineData.Ring>,
    private val route: RouteData.Route,
) {

    data class StopMark(val stop: RouteData.Stop, val center: Offset)

    class Layout(
        val center: Offset,
        val radius: Float,
        val graticule: Path,
        val land: Path,
        val coast: Path,
        val futureRoute: Path,
        val pastRoute: List<Triple<Offset, Offset, RouteData.Transport>>,
        val stopMarks: List<StopMark>,
        val hero: Offset?,
    )

    private val graticuleLines: List<List<Vec3>> = buildGraticule()

    fun layout(
        width: Float,
        height: Float,
        centerLat: Double,
        centerLon: Double,
        scale: Float,
    ): Layout {
        val cx = width / 2f
        val cy = height / 2f
        val r = min(width, height) * 0.40f * scale
        val frame = GlobeFrame(centerLat, centerLon)

        fun P2.toOffset() = Offset(cx + (x * r).toFloat(), cy - (y * r).toFloat())

        // --- graticule ---
        val graticule = Path()
        for (line in graticuleLines) {
            for (frag in clipToFront(line, frame)) {
                if (frag.size < 2) continue
                addPolyline(graticule, frag.map { frame.project(it).toOffset() })
            }
        }

        // --- land ---
        val land = Path()
        land.fillType = PathFillType.EvenOdd
        val coast = Path()
        val diskFragments = mutableListOf<DiskFragment>()
        for (ring in rings) {
            val pts = ring.points
            var anyVisible = false
            var allVisible = true
            for (p in pts) {
                val v = frame.isVisible(p)
                anyVisible = anyVisible or v
                allVisible = allVisible and v
            }
            if (!anyVisible) continue
            if (allVisible) {
                val proj = pts.map { frame.project(it) }
                addDiskSubpath(land, proj, r, cx, cy)
                addPolyline(coast, proj.map { it.toOffset() }, close = true)
            } else {
                // Rings are closed: append the first point so the wrap segment is clipped too.
                val closed = pts + pts.first()
                for (frag in clipToFront(closed, frame)) {
                    if (frag.size < 2) continue
                    val proj = frag.map { frame.project(it) }
                    addPolyline(coast, proj.map { it.toOffset() })
                    if (ring.isHole) {
                        // Holes are not stitched with the rim; chord-closed (rim artifact
                        // possible for the Caspian exactly on the horizon, documented).
                        addDiskSubpath(land, proj, r, cx, cy)
                    } else {
                        diskFragments.add(DiskFragment(proj))
                    }
                }
            }
        }
        for (loop in stitchFragments(diskFragments)) {
            addDiskSubpath(land, loop, r, cx, cy)
        }

        // --- route (test data) ---
        val future = Path()
        val past = mutableListOf<Triple<Offset, Offset, RouteData.Transport>>()
        var futureOpen = false
        val rp = route.points
        val heroDist = route.heroDistDeg
        for (i in 0 until rp.size - 1) {
            val a = rp[i]
            val b = rp[i + 1]
            val va = frame.isVisible(a)
            val vb = frame.isVisible(b)
            if (!va && !vb) {
                futureOpen = false
                continue
            }
            var p0 = a
            var p1 = b
            var d0 = route.distDeg[i]
            var d1 = route.distDeg[i + 1]
            if (!va || !vb) {
                val h = horizonPoint(a, b, frame.center)
                val da = a dot frame.center
                val db = b dot frame.center
                val t = da / (da - db)
                if (!va) {
                    p0 = h
                    d0 = d0 + (d1 - d0) * t
                    futureOpen = false
                } else {
                    p1 = h
                    d1 = d0 + (d1 - d0) * t
                }
            }
            val mode = route.segMode[i]
            // Split at the hero distance: color boundary may fall inside a segment.
            val parts = mutableListOf<Triple<Vec3, Vec3, Boolean>>()
            when {
                d1 <= heroDist -> parts.add(Triple(p0, p1, true))
                d0 >= heroDist -> parts.add(Triple(p0, p1, false))
                else -> {
                    val t = (heroDist - d0) / (d1 - d0)
                    val mid = slerp(p0, p1, t)
                    parts.add(Triple(p0, mid, true))
                    parts.add(Triple(mid, p1, false))
                }
            }
            for ((q0, q1, isPast) in parts) {
                val o0 = frame.project(q0).toOffset()
                val o1 = frame.project(q1).toOffset()
                if (isPast) {
                    past.add(Triple(o0, o1, mode))
                    futureOpen = false
                } else {
                    if (!futureOpen) {
                        future.moveTo(o0.x, o0.y)
                        futureOpen = true
                    }
                    future.lineTo(o1.x, o1.y)
                }
            }
        }

        // --- stops & hero ---
        val marks = RouteData.stops.mapNotNull { stop ->
            val v = latLonToVec(stop.lat, stop.lon)
            if (!frame.isVisible(v)) return@mapNotNull null
            StopMark(stop, frame.project(v).toOffset())
        }
        val hero = if (frame.isVisible(route.heroPos)) frame.project(route.heroPos).toOffset() else null

        return Layout(
            center = Offset(cx, cy),
            radius = r,
            graticule = graticule,
            land = land,
            coast = coast,
            futureRoute = future,
            pastRoute = past,
            stopMarks = marks,
            hero = hero,
        )
    }

    private fun addPolyline(path: Path, pts: List<Offset>, close: Boolean = false) {
        if (pts.size < 2) return
        path.moveTo(pts[0].x, pts[0].y)
        for (i in 1 until pts.size) path.lineTo(pts[i].x, pts[i].y)
        if (close) path.close()
    }

    /** Adds a closed subpath given in unit-disk coords (scaled to screen here). */
    private fun addDiskSubpath(path: Path, pts: List<P2>, r: Float, cx: Float, cy: Float) {
        if (pts.size < 2) return
        path.moveTo(cx + (pts[0].x * r).toFloat(), cy - (pts[0].y * r).toFloat())
        for (i in 1 until pts.size) {
            path.lineTo(cx + (pts[i].x * r).toFloat(), cy - (pts[i].y * r).toFloat())
        }
        path.close()
    }

    private fun buildGraticule(): List<List<Vec3>> {
        val lines = mutableListOf<List<Vec3>>()
        var lon = -180.0
        while (lon < 180.0) {
            val meridian = mutableListOf<Vec3>()
            var lat = -90.0
            while (lat <= 90.0) {
                meridian.add(latLonToVec(lat, lon))
                lat += 3.0
            }
            lines.add(meridian)
            lon += 30.0
        }
        var lat = -60.0
        while (lat <= 60.0) {
            val parallel = mutableListOf<Vec3>()
            var lonP = -180.0
            while (lonP <= 180.0) {
                parallel.add(latLonToVec(lat, lonP))
                lonP += 3.0
            }
            lines.add(parallel)
            lat += 30.0
        }
        return lines
    }
}
