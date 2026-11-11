package com.boxowl.aroundtheworld.expedition.globe

import com.boxowl.aroundtheworld.expedition.route.Transport

/** Render-only route; all progress and geometry come from the versioned route dataset. */
class GlobeRoute(
    val points: List<Vec3>,
    val distDeg: DoubleArray,
    val segMode: List<Transport>,
    val totalDeg: Double,
    val heroDistDeg: Double,
    val heroPos: Vec3,
)

data class GlobeStop(val name: String, val lat: Double, val lon: Double)
