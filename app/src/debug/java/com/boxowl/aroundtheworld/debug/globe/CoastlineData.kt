package com.boxowl.aroundtheworld.debug.globe

import java.io.InputStream

/**
 * Decoder for the ne_land.bin asset produced by app/src/debug/tools/prepare_land.py
 * (Natural Earth ne_50m_land, public domain — see assets/ne_land.README.md).
 * Rings are decoded straight into unit 3D vectors, so the antimeridian needs no
 * special handling downstream.
 */
object CoastlineData {

    data class Ring(val isHole: Boolean, val points: List<Vec3>)

    fun decode(input: InputStream): List<Ring> {
        val buf = input.readBytes()
        require(buf.size >= 5) { "asset too small" }
        require(buf[0] == 'N'.code.toByte() && buf[1] == 'E'.code.toByte() &&
            buf[2] == 'L'.code.toByte() && buf[3] == '1'.code.toByte()) { "bad magic" }
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
                val pts = ArrayList<Vec3>(count)
                pts.add(dequant(qLon, qLat))
                for (i in 1 until count) {
                    qLon = (qLon + sv()) and 0xFFFF
                    qLat += sv()
                    pts.add(dequant(qLon, qLat))
                }
                rings.add(Ring(isHole, pts))
            }
        }
        return rings
    }

    private fun dequant(qLon: Int, qLat: Int): Vec3 {
        val lon = qLon / 65535.0 * 360.0 - 180.0
        val lat = qLat.coerceIn(0, 65535) / 65535.0 * 180.0 - 90.0
        return latLonToVec(lat, lon)
    }
}
