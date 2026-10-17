package com.boxowl.aroundtheworld.expedition.scene

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.lerp
import com.boxowl.aroundtheworld.expedition.ScenePalette
import com.boxowl.aroundtheworld.expedition.arcade
import com.boxowl.aroundtheworld.expedition.bales
import com.boxowl.aroundtheworld.expedition.bigArch
import com.boxowl.aroundtheworld.expedition.clockTower
import com.boxowl.aroundtheworld.expedition.crane
import com.boxowl.aroundtheworld.expedition.dome
import com.boxowl.aroundtheworld.expedition.embankment
import com.boxowl.aroundtheworld.expedition.frac
import com.boxowl.aroundtheworld.expedition.glow
import com.boxowl.aroundtheworld.expedition.horizonHaze
import com.boxowl.aroundtheworld.expedition.lamppost
import com.boxowl.aroundtheworld.expedition.lighthouse
import com.boxowl.aroundtheworld.expedition.mast
import com.boxowl.aroundtheworld.expedition.minaret
import com.boxowl.aroundtheworld.expedition.mix
import com.boxowl.aroundtheworld.expedition.smoke
import com.boxowl.aroundtheworld.expedition.spire
import com.boxowl.aroundtheworld.expedition.train
import com.boxowl.aroundtheworld.expedition.tunnel
import kotlin.math.floor
import kotlin.math.sin

/**
 * The ten world segments of chapter 1 (P07). Every decorative object lives at a
 * fixed world X with a stable procedural seed, so nothing rebuilds or shifts when
 * the camera moves; neighbours share anchor edges (ground profile, sea bands
 * butt-jointed at anchors), which keeps joints constructive instead of blended.
 */

/** Draw context: converts world coordinates to screen pixels for one camera frame. */
private class Ctx(
    val d: DrawScope,
    val layout: WorldLayout,
    val cam: Float,
    val viewW: Float,
    val p: ScenePalette,
) {
    val h: Float = d.size.height
    fun sx(wx: Float, layer: SceneLayer): Float =
        (JourneyCamera.HERO_FRACTION * viewW + (wx - cam) * layer.parallax) * h
    fun gy(wx: Float): Float = TerrainProfile.groundYAt(layout, wx) * h
    fun anchor(i: Int): Float = layout.anchorX(i)
}

private fun hash(seed: Int, index: Int, salt: Double): Float =
    frac(sin((index + seed * 37) * salt) * 43758.5453)

private const val S = SEGMENT_LENGTH

/** Solid ground strip whose top edge follows the terrain profile (NEAR layer). */
private fun Ctx.groundStrip(x0: Float, x1: Float, color: Color) {
    val path = Path()
    val steps = 28
    for (s in 0..steps) {
        val wx = x0 + (x1 - x0) * s / steps
        val x = sx(wx, SceneLayer.NEAR)
        val y = gy(wx)
        if (s == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.lineTo(sx(x1, SceneLayer.NEAR), h)
    path.lineTo(sx(x0, SceneLayer.NEAR), h)
    path.close()
    d.drawPath(path, color)
}

/** Accent line along the terrain profile (platform edge, snow line, deck edge). */
private fun Ctx.edgeLine(x0: Float, x1: Float, color: Color, alpha: Float) {
    if (alpha <= 0f) return
    val path = Path()
    val steps = 28
    for (s in 0..steps) {
        val wx = x0 + (x1 - x0) * s / steps
        val x = sx(wx, SceneLayer.NEAR)
        val y = gy(wx) + h * 0.008f
        if (s == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    d.drawPath(path, color, alpha = alpha, style = Stroke(width = h * 0.006f))
}

/** Lighter winding road ribbon weaving along the ground's top edge (P08). */
private fun Ctx.roadRibbon(x0: Float, x1: Float, color: Color) {
    fun inset(wx: Float) = h * (0.004f + 0.004f * (1f + sin(wx * 3.7f + 0.9f)))
    fun wd(wx: Float) = h * (0.014f + 0.0035f * (1f + sin(wx * 2.3f + 2.1f)))
    val path = Path()
    val steps = 40
    for (s in 0..steps) {
        val wx = x0 + (x1 - x0) * s / steps
        val x = sx(wx, SceneLayer.NEAR)
        val y = gy(wx) + inset(wx)
        if (s == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    for (s in steps downTo 0) {
        val wx = x0 + (x1 - x0) * s / steps
        path.lineTo(sx(wx, SceneLayer.NEAR), gy(wx) + inset(wx) + wd(wx))
    }
    path.close()
    d.drawPath(path, color)
}

/** Haycocks, bushes and rocks scattered on the terrain profile, seeded (P08). */
private fun Ctx.scatterW(x0: Float, x1: Float, color: Color, seed: Int) {
    var wx = x0 + 0.05f * hash(seed, 0, 3.3)
    var i = 0
    while (wx < x1) {
        val x = sx(wx, SceneLayer.NEAR)
        val g = gy(wx)
        when ((hash(seed, i, 5.1) * 3f).toInt()) {
            0 -> {
                // Haycock: a small dome with a pointed top.
                val r = h * (0.02f + 0.012f * hash(seed, i, 7.7))
                d.drawPath(Path().apply {
                    moveTo(x - r, g)
                    quadraticTo(x - r * 0.7f, g - r * 1.5f, x, g - r * 1.7f)
                    quadraticTo(x + r * 0.7f, g - r * 1.5f, x + r, g)
                    close()
                }, color)
            }
            1 -> {
                // Bush: a low two-lobed clump.
                val r = h * (0.014f + 0.01f * hash(seed, i, 9.3))
                d.drawCircle(color, r, Offset(x - r * 0.5f, g - r * 0.6f))
                d.drawCircle(color, r * 0.8f, Offset(x + r * 0.6f, g - r * 0.5f))
            }
            else -> {
                // Rock: a small angular shard.
                val r = h * (0.012f + 0.01f * hash(seed, i, 11.9))
                d.drawPath(Path().apply {
                    moveTo(x - r, g)
                    lineTo(x - r * 0.4f, g - r * (0.8f + 0.5f * hash(seed, i, 13.1)))
                    lineTo(x + r * 0.3f, g - r * 0.6f)
                    lineTo(x + r, g)
                    close()
                }, color)
            }
        }
        wx += 0.13f + 0.14f * hash(seed, i, 4.4)
        i++
    }
}

/**
 * Roofline strip anchored in world coordinates: gable, mansard and parapet
 * houses of varying heights, optional chimneys and an occasional spire.
 */
private fun Ctx.roofsW(
    layer: SceneLayer, x0: Float, x1: Float, gyFrac: Float, baseHFrac: Float,
    color: Color, seed: Int, chimneys: Boolean, taperEnd: Boolean = false, sparse: Boolean = false,
) {
    val gyPx = gyFrac * h
    var wx = x0
    var i = 0
    while (wx < x1) {
        val wd = 0.11f * (0.75f + 0.3f * hash(seed, i, 3.31)) * h * layer.parallax
        if (!sparse || hash(seed, i, 4.77) > 0.4f) {
            val taper = if (taperEnd) 1f - 0.55f * ((wx - x0) / (x1 - x0)).coerceIn(0f, 1f) else 1f
            val hh = baseHFrac * h * taper * (0.7f + 0.5f * hash(seed, i, 7.13))
            val x = sx(wx, layer)
            d.drawRect(color, topLeft = Offset(x, gyPx - hh), size = Size(wd, hh))
            when ((hash(seed, i, 9.17) * 3f).toInt()) {
                0 -> d.drawPath(Path().apply {
                    moveTo(x - wd * 0.06f, gyPx - hh)
                    lineTo(x + wd * 0.5f, gyPx - hh - wd * 0.28f)
                    lineTo(x + wd * 1.06f, gyPx - hh)
                    close()
                }, color)
                1 -> d.drawPath(Path().apply {
                    moveTo(x - wd * 0.02f, gyPx - hh)
                    lineTo(x + wd * 0.22f, gyPx - hh - wd * 0.18f)
                    lineTo(x + wd * 0.78f, gyPx - hh - wd * 0.18f)
                    lineTo(x + wd * 1.02f, gyPx - hh)
                    close()
                }, color)
                else -> d.drawRect(color, topLeft = Offset(x - wd * 0.02f, gyPx - hh - wd * 0.05f),
                    size = Size(wd * 1.04f, wd * 0.06f))
            }
            if (chimneys) {
                val count = (hash(seed, i, 6.47) * 3f).toInt()
                for (ch in 0 until count) {
                    val cx = x + wd * (0.2f + 0.5f * hash(seed + ch, i, 4.53))
                    d.drawRect(color, topLeft = Offset(cx, gyPx - hh - wd * 0.28f - hh * 0.1f),
                        size = Size(wd * 0.09f, hh * 0.14f + wd * 0.2f))
                }
                if (hash(seed, i, 11.31) > 0.9f) {
                    d.drawRect(color, topLeft = Offset(x + wd * 0.46f, gyPx - hh - hh * 0.5f),
                        size = Size(wd * 0.06f, hh * 0.5f))
                    d.drawPath(Path().apply {
                        moveTo(x + wd * 0.42f, gyPx - hh - hh * 0.5f)
                        lineTo(x + wd * 0.49f, gyPx - hh - hh * 0.66f)
                        lineTo(x + wd * 0.56f, gyPx - hh - hh * 0.5f)
                        close()
                    }, color)
                }
            }
        }
        wx += 0.11f * (0.75f + 0.3f * hash(seed, i, 3.31)) * (if (sparse) 2.1f else 1.02f)
        i++
    }
}

/**
 * Rolling ridge spanning a world interval (P08): a main crest line of varying
 * peaks, a lower foothill spur in front of it, and small conifer teeth along
 * the crests — instead of the plain repeated humps of P07. Ends at the baseline.
 */
private fun Ctx.humpsW(layer: SceneLayer, x0: Float, x1: Float, gyFrac: Float, ampFrac: Float, seed: Double, color: Color) {
    val gyPx = gyFrac * h
    val amp = ampFrac * h
    val peaks = mutableListOf<Offset>()
    val path = Path()
    var wx = x0
    var k = 0
    path.moveTo(sx(wx, layer), gyPx + 2f)
    while (wx < x1) {
        val segW = 0.4f * (0.7f + 0.6f * frac(sin(seed + k * 5.7) * 43.1))
        val peakY = gyPx - amp * (0.5f + 0.5f * frac(sin(seed + k * 9.3) * 71.7))
        val peakX = sx(wx + segW * (0.35f + 0.3f * frac(sin(seed + k * 3.9) * 19.3)), layer)
        path.quadraticTo(peakX, peakY - amp * 0.35f, sx(wx + segW, layer), gyPx)
        peaks += Offset(peakX, peakY)
        wx += segW
        k++
    }
    path.lineTo(sx(x1, layer), h)
    path.lineTo(sx(x0, layer), h)
    path.close()
    d.drawPath(path, color)
    // Foothill spur: a second, lower ridge with its own rhythm.
    val spur = Path()
    var swx = x0
    var sk = 0
    spur.moveTo(sx(swx, layer), gyPx + 2f)
    while (swx < x1) {
        val segW = 0.27f * (0.7f + 0.6f * frac(sin(seed * 1.7 + sk * 7.1) * 29.7))
        val peakY = gyPx - amp * 0.45f * (0.5f + 0.5f * frac(sin(seed * 1.7 + sk * 11.3) * 53.9))
        spur.quadraticTo(sx(swx + segW * 0.5f, layer), peakY - amp * 0.2f, sx(swx + segW, layer), gyPx)
        swx += segW
        sk++
    }
    spur.lineTo(sx(x1, layer), h)
    spur.lineTo(sx(x0, layer), h)
    spur.close()
    d.drawPath(spur, color)
    // Conifer teeth along the main crest: tiny seeded triangles.
    val teeth = Path()
    peaks.forEachIndexed { pi, peak ->
        val n = 2 + (frac(sin(seed + pi * 13.7) * 91.3) * 2.9).toInt()
        for (ti in 0 until n) {
            val tx = peak.x + (frac(sin(seed + pi * 7.3 + ti * 3.1) * 61.7) - 0.5f) * amp * 0.9f
            val th = amp * (0.10f + 0.10f * frac(sin(seed + pi * 5.9 + ti * 7.7) * 37.1))
            val ty = peak.y + amp * 0.18f
            teeth.moveTo(tx - th * 0.45f, ty)
            teeth.lineTo(tx, ty - th)
            teeth.lineTo(tx + th * 0.45f, ty)
            teeth.close()
        }
    }
    d.drawPath(teeth, color)
}

/** Jagged mountain ridge with snow caps, anchored in world coordinates. */
private fun Ctx.peaksW(layer: SceneLayer, x0: Float, x1: Float, gyFrac: Float, ampFrac: Float, seed: Double, color: Color, snow: Color) {
    val gyPx = gyFrac * h
    val amp = ampFrac * h
    val tops = mutableListOf<Pair<Offset, Float>>()
    val path = Path()
    var wx = x0
    var k = 0
    path.moveTo(sx(wx, layer), gyPx)
    while (wx < x1) {
        val peakH = amp * (0.55f + 0.45f * frac(sin(seed + k * 7.9) * 51.3))
        val peakWx = wx + 0.18f * (0.45f + 0.55f * frac(sin(seed + k * 3.3) * 77.1))
        path.lineTo(sx(peakWx, layer), gyPx - peakH)
        tops += Offset(sx(peakWx, layer), gyPx - peakH) to peakH
        val valleyY = gyPx - amp * 0.3f * (0.4f + 0.6f * frac(sin(seed + k * 5.1) * 23.7))
        wx = peakWx + 0.14f * (0.45f + 0.55f * frac(sin(seed + k * 9.7) * 41.3))
        path.lineTo(sx(wx, layer), valleyY)
        k++
    }
    path.lineTo(sx(x1, layer), gyPx)
    path.lineTo(sx(x1, layer), h)
    path.lineTo(sx(x0, layer), h)
    path.close()
    d.drawPath(path, color)
    for ((top, ph) in tops) {
        d.drawPath(Path().apply {
            moveTo(top.x - ph * 0.26f, top.y + ph * 0.3f)
            lineTo(top.x, top.y)
            lineTo(top.x + ph * 0.26f, top.y + ph * 0.3f)
            lineTo(top.x + ph * 0.14f, top.y + ph * 0.36f)
            lineTo(top.x, top.y + ph * 0.27f)
            lineTo(top.x - ph * 0.17f, top.y + ph * 0.38f)
            close()
        }, snow)
    }
}

/**
 * Water band with a dissolving top edge. Ends can taper horizontally into the
 * horizon haze, or butt-joint exactly (taper 0) with a neighbouring water band.
 */
private fun Ctx.seaW(
    layer: SceneLayer, x0: Float, x1: Float, topFrac: Float, bottomFrac: Float,
    color: Color, taperStart: Float = 0.25f, taperEnd: Float = 0.25f,
) {
    val xa = sx(x0, layer)
    val xb = sx(x1, layer)
    val top = topFrac * h
    val bottom = bottomFrac * h
    val ts = taperStart * h * layer.parallax
    val te = taperEnd * h * layer.parallax
    d.drawRect(
        Brush.verticalGradient(0f to color.copy(alpha = 0f), 0.45f to color, startY = top, endY = bottom),
        topLeft = Offset(xa + ts, top),
        size = Size((xb - te) - (xa + ts), bottom - top),
    )
    val taperTop = top + (bottom - top) * 0.2f
    if (ts > 0f) {
        d.drawRect(
            Brush.horizontalGradient(0f to color.copy(alpha = 0f), 1f to color, startX = xa, endX = xa + ts),
            topLeft = Offset(xa, taperTop),
            size = Size(ts, bottom - taperTop),
        )
    }
    if (te > 0f) {
        d.drawRect(
            Brush.horizontalGradient(0f to color, 1f to color.copy(alpha = 0f), startX = xb - te, endX = xb),
            topLeft = Offset(xb - te, taperTop),
            size = Size(te, bottom - taperTop),
        )
    }
}

/** Telegraph poles with sagging wires; broken rhythm, lean and gaps (P08). */
private fun Ctx.telegraphW(x0: Float, x1: Float, color: Color) {
    val sw = h * 0.007f
    val tops = mutableListOf<Offset>()
    var wx = x0
    var i = 0
    while (wx < x1) {
        if (hash(97, i, 6.1) > 0.12f) { // an occasional missing pole
            val x = sx(wx, SceneLayer.NEAR)
            val g = gy(wx)
            val hgt = h * (0.15f + 0.07f * hash(97, i, 3.7))
            val lean = (hash(97, i, 8.9) - 0.5f) * hgt * 0.12f
            d.drawLine(color, Offset(x, g), Offset(x + lean, g - hgt), strokeWidth = sw)
            d.drawLine(color, Offset(x + lean - hgt * 0.12f, g - hgt * 0.85f),
                Offset(x + lean + hgt * 0.12f, g - hgt * 0.85f), strokeWidth = sw)
            tops += Offset(x + lean, g - hgt * 0.85f)
        }
        wx += 0.22f + 0.16f * hash(97, i, 5.3)
        i++
    }
    for (j in 0 until tops.size - 1) {
        val a0 = tops[j]
        val b0 = tops[j + 1]
        d.drawPath(Path().apply {
            moveTo(a0.x, a0.y)
            quadraticTo((a0.x + b0.x) / 2, maxOf(a0.y, b0.y) + h * 0.035f, b0.x, b0.y)
        }, color, style = Stroke(width = sw * 0.6f))
    }
}

/** Low fence following the terrain profile; uneven posts and an occasional gap. */
private fun Ctx.fenceW(x0: Float, x1: Float, color: Color) {
    val sw = h * 0.005f
    var wx = x0
    var i = 0
    val rail = Path()
    var railOpen = false
    while (wx <= x1) {
        val gap = hash(71, i, 4.9) < 0.1f
        if (!gap) {
            val x = sx(wx, SceneLayer.NEAR)
            val g = gy(wx)
            val hgt = h * (0.03f + 0.012f * hash(71, i, 7.3))
            d.drawLine(color, Offset(x, g), Offset(x, g - hgt), strokeWidth = sw)
            if (railOpen) rail.lineTo(x, g - hgt * 0.9f) else rail.moveTo(x, g - hgt * 0.9f)
            railOpen = true
        } else {
            railOpen = false
        }
        wx += 0.055f + 0.03f * hash(71, i, 3.1)
        i++
    }
    d.drawPath(rail, color, style = Stroke(width = sw))
}

/** Railing following a surface profile (promenade, deck) in the NEAR layer. */
private fun Ctx.railingW(x0: Float, x1: Float, hgtFrac: Float, color: Color) {
    val sw = h * 0.006f
    val steps = 28
    for (rail in listOf(1f, 0.5f)) {
        val path = Path()
        for (s in 0..steps) {
            val wx = x0 + (x1 - x0) * s / steps
            val x = sx(wx, SceneLayer.NEAR)
            val y = gy(wx) - hgtFrac * h * rail
            if (s == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        d.drawPath(path, color, style = Stroke(width = sw))
    }
    var wx = x0
    while (wx <= x1) {
        val x = sx(wx, SceneLayer.NEAR)
        val g = gy(wx)
        d.drawLine(color, Offset(x, g), Offset(x, g - hgtFrac * h), strokeWidth = sw)
        wx += 0.07f
    }
}

/** Sparse warm windows over a world interval; lit only at dusk and night. */
private fun Ctx.windowsW(layer: SceneLayer, x0: Float, x1: Float, bandTopFrac: Float, bandBottomFrac: Float, seed: Int) {
    if (p.lightAlpha <= 0f) return
    var wx = x0
    var i = 0
    while (wx < x1) {
        if (hash(seed, i, 5.77) >= 0.45f) {
            val x = sx(wx, layer)
            val y = (bandTopFrac + (bandBottomFrac - bandTopFrac) * hash(seed, i, 8.41)) * h
            d.drawRect(p.light, topLeft = Offset(x, y), size = Size(h * 0.008f, h * 0.012f),
                alpha = 0.5f * p.lightAlpha)
        }
        wx += 0.035f
        i++
    }
}

/** A row of warm port lights along a pier, lit at dusk and night. */
private fun Ctx.pierLightsW(layer: SceneLayer, x0: Float, x1: Float, yFrac: Float) {
    if (p.lightAlpha <= 0f) return
    var wx = x0
    while (wx <= x1) {
        d.glow(sx(wx, layer), yFrac * h, h * 0.007f, p, 1f)
        wx += (x1 - x0) / 7f
    }
}

/** Two or three distant gulls per world interval, only by day and dusk. */
private fun Ctx.birdsW(layer: SceneLayer, x0: Float, x1: Float, seed: Int) {
    if (p.starAlpha > 0.4f) return
    val col = lerp(p.far, p.skyTop, 0.3f)
    for (i in 0..2) {
        val wx = x0 + (x1 - x0) * (0.2f + 0.6f * hash(seed, i, 7.7))
        val bx = sx(wx, layer)
        val by = h * (0.16f + 0.12f * hash(seed, i, 3.1))
        val wing = Stroke(width = h * 0.004f, cap = StrokeCap.Round)
        d.drawArc(col, 205f, 55f, useCenter = false, topLeft = Offset(bx - h * 0.024f, by),
            size = Size(h * 0.024f, h * 0.02f), alpha = 0.55f, style = wing)
        d.drawArc(col, 280f, 55f, useCenter = false, topLeft = Offset(bx, by),
            size = Size(h * 0.024f, h * 0.02f), alpha = 0.55f, style = wing)
    }
}

/**
 * Near water surface (P08): a gradient fill to the bottom of the screen, two
 * seeded crest rows with varying scallops, and world-seeded glints. The surface
 * moves only with the camera — in a still world it is frozen.
 */
private fun Ctx.waterFrontW(x0: Float, x1: Float, topFrac: Float, color: Color) {
    val y0 = topFrac * h
    val xa = sx(x0, SceneLayer.NEAR)
    val xb = sx(x1, SceneLayer.NEAR)
    d.drawRect(
        Brush.verticalGradient(0f to color.copy(alpha = 0.55f), 0.25f to color, startY = y0, endY = h),
        topLeft = Offset(xa, y0), size = Size(xb - xa, h - y0),
    )
    for (row in 0..1) {
        val y = y0 + h * (0.004f + 0.02f * row)
        val path = Path()
        var wx = x0
        var i = row * 31
        path.moveTo(sx(wx, SceneLayer.NEAR), y)
        while (wx < x1) {
            val w = 0.07f + 0.06f * hash(23 + row, i, 3.7)
            val amp = h * (0.008f + 0.008f * hash(23 + row, i, 5.3))
            path.quadraticTo(sx(wx + w / 2, SceneLayer.NEAR), y - amp, sx(wx + w, SceneLayer.NEAR), y)
            wx += w
            i++
        }
        d.drawPath(path, lerp(color, Color.White, 0.25f), alpha = 0.5f - row * 0.15f,
            style = Stroke(width = h * 0.005f, cap = StrokeCap.Round))
    }
    glintsW(SceneLayer.NEAR, x0, x1, topFrac + 0.012f, p.celestial, 0.25f + p.lightAlpha * 0.5f)
}

/** Boarding gangway (P08): plank walkway on piles over open water, with rails. */
private fun Ctx.gangwayW(x0: Float, x1: Float, color: Color) {
    val wood = lerp(color, Color(0xFF8A6B4A), 0.22f)
    // Piles reaching from the walkway underside down into the water.
    var wx = x0 + 0.04f
    var i = 0
    while (wx < x1) {
        val x = sx(wx, SceneLayer.NEAR)
        d.drawLine(color, Offset(x, gy(wx) + h * 0.02f), Offset(x, 0.97f * h), strokeWidth = h * 0.009f)
        wx += 0.09f + 0.03f * hash(61, i, 3.3)
        i++
    }
    // Plank walkway following the terrain profile.
    val path = Path()
    val steps = 24
    for (s in 0..steps) {
        val wx2 = x0 + (x1 - x0) * s / steps
        val x = sx(wx2, SceneLayer.NEAR)
        val y = gy(wx2)
        if (s == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    for (s in steps downTo 0) {
        val wx2 = x0 + (x1 - x0) * s / steps
        path.lineTo(sx(wx2, SceneLayer.NEAR), gy(wx2) + h * 0.02f)
    }
    path.close()
    d.drawPath(path, wood)
    // Cross planks.
    var wx3 = x0 + 0.02f
    var j = 0
    while (wx3 < x1 - 0.01f) {
        val x = sx(wx3, SceneLayer.NEAR)
        val g = gy(wx3)
        d.drawLine(lerp(wood, Color.Black, 0.35f), Offset(x, g + h * 0.002f), Offset(x, g + h * 0.02f),
            strokeWidth = h * 0.003f)
        wx3 += 0.045f + 0.015f * hash(67, j, 4.1)
        j++
    }
    railingW(x0 + 0.01f, x1 - 0.01f, 0.05f, color)
}

/**
 * The near side of the hero's own ship (P08): deck lip along the terrain
 * profile, a rounded bow at [x0] and a rounded stern at [x1], porthole row and
 * a boot stripe at the waterline. Open water shows below the hull.
 */
private fun Ctx.shipHullW(x0: Float, x1: Float, color: Color) {
    val bottom = 0.95f * h
    val waterline = 0.93f * h
    val hull = Path()
    val steps = 24
    for (s in 0..steps) {
        val wx = x0 + (x1 - x0) * s / steps
        val x = sx(wx, SceneLayer.NEAR)
        val y = gy(wx)
        if (s == 0) hull.moveTo(x, y) else hull.lineTo(x, y)
    }
    hull.quadraticTo(sx(x1 + 0.04f, SceneLayer.NEAR), gy(x1) + h * 0.07f, sx(x1 - 0.05f, SceneLayer.NEAR), bottom)
    hull.lineTo(sx(x0 + 0.07f, SceneLayer.NEAR), bottom)
    hull.quadraticTo(sx(x0 - 0.03f, SceneLayer.NEAR), gy(x0) + h * 0.06f, sx(x0, SceneLayer.NEAR), gy(x0))
    hull.close()
    d.drawPath(hull, color)
    d.drawLine(lerp(color, Color.White, 0.3f), Offset(sx(x0 + 0.05f, SceneLayer.NEAR), waterline),
        Offset(sx(x1 - 0.03f, SceneLayer.NEAR), waterline), strokeWidth = h * 0.005f, alpha = 0.5f)
    val port = if (p.lightAlpha > 0f) p.light else lerp(color, Color.White, 0.35f)
    val pa = if (p.lightAlpha > 0f) 0.8f * p.lightAlpha else 0.4f
    var wx = x0 + 0.1f
    while (wx < x1 - 0.08f) {
        d.drawCircle(port, h * 0.006f, Offset(sx(wx, SceneLayer.NEAR), 0.865f * h), alpha = pa)
        wx += 0.09f
    }
}

/** Wave scallop rows on open water, anchored in world coordinates. */
private fun Ctx.wavesW(layer: SceneLayer, x0: Float, x1: Float, yFrac: Float, color: Color) {
    val y = yFrac * h
    val stepPx = 0.1f * h * layer.parallax
    val path = Path()
    var wx = x0
    path.moveTo(sx(wx, layer), y)
    while (wx < x1) {
        path.quadraticTo(sx(wx + 0.05f, layer), y - h * 0.02f, sx(wx + 0.1f, layer), y)
        wx += 0.1f
    }
    d.drawPath(path, color, style = Stroke(width = h * 0.006f, cap = StrokeCap.Round))
    if (stepPx <= 0f) return
}

/** Sun/moon glints on water, seeded by world position. */
private fun Ctx.glintsW(layer: SceneLayer, x0: Float, x1: Float, yTopFrac: Float, color: Color, a: Float) {
    if (a <= 0f) return
    var wx = x0
    var i = 0
    while (wx < x1) {
        if (hash(53, i, 4.77) > 0.35f) {
            val x = sx(wx, layer)
            val y = (yTopFrac + 0.09f * hash(53, i, 9.13)) * h
            d.drawLine(color, Offset(x, y), Offset(x + h * 0.025f, y),
                strokeWidth = h * 0.004f, cap = StrokeCap.Round, alpha = 0.5f * a)
        }
        wx += 0.11f
        i++
    }
}

/** Layered cloud banks drifting with the far layer, seeded by world position. */
private fun Ctx.cloudsW(layer: SceneLayer, x0: Float, x1: Float, color: Color) {
    var wx = x0
    var i = 0
    while (wx < x1) {
        val cx = sx(wx, layer)
        val cy = h * (0.09f + 0.15f * hash(71, i, 3.3))
        val wd = h * (0.14f + 0.09f * hash(71, i, 5.9))
        d.cloudBank(cx, cy, wd, color, seed = wx)
        wx += 1.1f + 0.5f * hash(71, i, 7.7)
        i++
    }
}

// --- Segments ---------------------------------------------------------------

private fun Ctx.seg0London(layer: SceneLayer) {
    val a0 = anchor(0)
    val a1 = anchor(1)
    val far = p.far
    val mid = p.mid
    val near = p.near
    when (layer) {
        SceneLayer.FAR -> {
            cloudsW(layer, a0 - 2.2f, a1 + 1.6f, lerp(p.skyBottom, Color.White, 0.35f))
            roofsW(layer, a0 - 3.2f, a1 + 0.4f, 0.64f, 0.14f, far, seed = 3, chimneys = true, taperEnd = true)
            d.spire(sx(a0 - 0.7f, layer), 0.64f * h, 0.32f * h, far)
            d.clockTower(sx(a0 + 0.42f, layer), 0.64f * h, 0.26f * h, far, p, 1f)
        }
        SceneLayer.MID -> {
            roofsW(layer, a0 - 1.2f, a1 + 0.1f, 0.665f, 0.17f, mid, seed = 11, chimneys = true, taperEnd = true)
            d.bigArch(sx(a0 + 0.12f, layer), 0.665f * h, 0.42f * h, 0.2f * h, mid)
            windowsW(layer, a0 - 1.0f, a1, 0.55f, 0.65f, seed = 5)
        }
        SceneLayer.NEAR -> {
            groundStrip(a0 - 0.6f, a1 + 0.05f, near)
            // Platform edge catching the lamplight.
            edgeLine(a0 - 0.6f, a0 + 1.0f, lerp(near, p.light, 0.35f), 0.35f * p.lightAlpha.coerceAtLeast(0.25f))
            d.train(sx(a0 - 0.15f, layer), gy(a0 - 0.15f), 0.26f, near, p, 1f)
            d.lamppost(sx(a0 - 0.35f, layer), gy(a0 - 0.35f), 0.26f * h, near, p, 1f)
            d.lamppost(sx(a0 + 0.3f, layer), gy(a0 + 0.3f), 0.21f * h, near, p, 1f)
            d.lamppost(sx(a0 + 0.85f, layer), gy(a0 + 0.85f), 0.19f * h, near, p, 1f)
            d.tree(sx(a1 - 0.2f, layer), gy(a1 - 0.2f), 0.12f * h, near, seed = a1 - 0.2f)
        }
    }
}

private fun Ctx.seg1Departure(layer: SceneLayer) {
    val a1 = anchor(1)
    val a2 = anchor(2)
    val far = p.far
    val mid = p.mid
    val near = p.near
    when (layer) {
        SceneLayer.FAR -> {
            cloudsW(layer, a1 - 0.6f, a2 + 1.4f, lerp(p.skyBottom, Color.White, 0.35f))
            humpsW(layer, a1 - 0.5f, a2 + 0.7f, 0.66f, 0.10f, 2.7, far)
            roofsW(layer, a1 - 0.3f, a1 + 0.6f, 0.65f, 0.07f, far, seed = 5, chimneys = false, sparse = true)
        }
        SceneLayer.MID -> {
            humpsW(layer, a1 - 0.3f, a2 + 0.4f, 0.672f, 0.05f, 5.1, mid)
            roofsW(layer, a1 - 0.1f, a1 + 0.5f, 0.668f, 0.11f, mid, seed = 9, chimneys = true, sparse = true)
            d.tree(sx(a1 + 0.7f, layer), 0.672f * h, 0.10f * h, mid, seed = a1 + 0.7f)
            d.tree(sx(a1 + 1.05f, layer), 0.672f * h, 0.08f * h, mid, seed = a1 + 1.05f)
        }
        SceneLayer.NEAR -> {
            groundStrip(a1 - 0.05f, a2 + 0.1f, near)
            roadRibbon(a1 - 0.05f, a2 + 0.1f, lerp(near, far, 0.5f))
            telegraphW(a1 + 0.05f, a2 + 0.25f, near)
            fenceW(a1 + 0.35f, a1 + 0.95f, near)
            scatterW(a1 + 0.1f, a2 + 0.05f, near, seed = 13)
            d.tree(sx(a1 + 0.25f, layer), gy(a1 + 0.25f), 0.12f * h, near, seed = a1 + 0.25f)
            d.tree(sx(a2 - 0.3f, layer), gy(a2 - 0.3f), 0.15f * h, near, seed = a2 - 0.3f)
        }
    }
}

private fun Ctx.seg2Dover(layer: SceneLayer) {
    val a2 = anchor(2)
    val a3 = anchor(3)
    val far = p.far
    val near = p.near
    when (layer) {
        SceneLayer.FAR -> {
            cloudsW(layer, a2 - 0.4f, a3 + 1.2f, lerp(p.skyBottom, Color.White, 0.35f))
            humpsW(layer, a2 - 0.5f, a2 + 0.35f, 0.66f, 0.08f, 8.2, far)
            // The Channel: tucked behind the cliffs on the left, open water
            // mid-crossing, dissolving into the Calais haze on the right.
            seaW(layer, a2 + 0.25f, a3 + 0.55f, 0.50f, 0.68f, lerp(p.skyBottom, far, 0.55f),
                taperStart = 0f, taperEnd = 0.3f)
            val boatX = sx(a2 + 1.15f, layer)
            val boatY = 0.54f * h
            d.drawRect(far, topLeft = Offset(boatX, boatY - h * 0.015f), size = Size(h * 0.05f, h * 0.015f))
            d.drawLine(far, Offset(boatX + h * 0.025f, boatY - h * 0.015f),
                Offset(boatX + h * 0.025f, boatY - h * 0.045f), strokeWidth = h * 0.006f)
            birdsW(layer, a2 + 0.2f, a3 + 0.3f, seed = 2)
        }
        SceneLayer.MID -> {
            // White cliffs of Dover (P08): a composite chalk massif — jagged
            // crest, stratified face and shadowed foot — standing on the shore
            // between a2+0.1 and a2+1.0; the open Channel takes over beyond.
            val gyPx = 0.68f * h
            val chalk = lerp(lerp(far, Color(0xFFF2EFE8), 0.55f), far, p.starAlpha * 0.85f)
            val x0c = a2 + 0.1f
            val x1c = a2 + 1.0f
            fun crestH(f: Float): Float {
                val up = (f / 0.16f).coerceIn(0f, 1f).let { it * it * (3f - 2f * it) }
                val down = ((1f - f) / 0.16f).coerceIn(0f, 1f).let { it * it * (3f - 2f * it) }
                val plateau = up * down
                val cell = (f * 40f).toInt()
                val jag = (hash(41, cell, 5.7) - 0.5f) * 0.05f
                val notch = if (hash(41, cell, 9.1) > 0.9f) 0.04f else 0f
                return (0.06f + 0.21f * plateau) * (1f + jag) - notch * plateau
            }
            val stepsC = 18
            val cliff = Path()
            cliff.moveTo(sx(x0c, layer), gyPx)
            for (s in 0..stepsC) {
                val f = s / stepsC.toFloat()
                cliff.lineTo(sx(x0c + (x1c - x0c) * f, layer), gyPx - crestH(f) * h)
            }
            cliff.lineTo(sx(x1c, layer), gyPx)
            cliff.close()
            d.drawPath(cliff, chalk)
            d.clipPath(cliff) {
                // Chalk strata following the crest, slightly undulating.
                val strataCol = lerp(chalk, far, 0.4f)
                for (level in listOf(0.32f, 0.56f, 0.8f)) {
                    val strata = Path()
                    for (s in 0..stepsC) {
                        val f = s / stepsC.toFloat()
                        val x = sx(x0c + (x1c - x0c) * f, layer)
                        val y = gyPx - crestH(f) * h * level - h * 0.004f * sin(f * 21f + level * 9f)
                        if (s == 0) strata.moveTo(x, y) else strata.lineTo(x, y)
                    }
                    d.drawPath(strata, strataCol, alpha = 0.55f, style = Stroke(width = h * 0.0035f))
                }
                // Shadowed talus foot.
                d.drawRect(lerp(chalk, Color.Black, 0.2f),
                    topLeft = Offset(sx(x0c, layer), gyPx - h * 0.018f),
                    size = Size(sx(x1c, layer) - sx(x0c, layer), h * 0.018f), alpha = 0.5f)
            }
        }
        SceneLayer.NEAR -> {
            groundStrip(a2 - 0.1f, a3 + 0.05f, near)
            railingW(a2 - 0.05f, a2 + 0.45f, 0.06f, near)
            scatterW(a2 + 0.15f, a3 - 0.1f, near, seed = 29)
            d.lighthouse(sx(a2 + 0.55f, layer), gy(a2 + 0.55f), 0.3f * h, near, p, 1f)
        }
    }
}

private fun Ctx.seg3Calais(layer: SceneLayer) {
    val a3 = anchor(3)
    val a4 = anchor(4)
    val far = p.far
    val mid = p.mid
    val near = p.near
    when (layer) {
        SceneLayer.FAR -> {
            roofsW(layer, a3 + 0.3f, a4 + 0.3f, 0.63f, 0.05f, far, seed = 21, chimneys = false)
            // Belfry over the rooftops; it also covers the Channel's right end.
            d.drawRect(far, topLeft = Offset(sx(a3 + 0.52f, layer) - h * 0.015f, 0.46f * h),
                size = Size(h * 0.03f, 0.17f * h))
            d.drawPath(Path().apply {
                moveTo(sx(a3 + 0.52f, layer) - h * 0.02f, 0.46f * h)
                lineTo(sx(a3 + 0.52f, layer), 0.42f * h)
                lineTo(sx(a3 + 0.52f, layer) + h * 0.02f, 0.46f * h)
                close()
            }, far)
            birdsW(layer, a3 + 0.1f, a3 + 0.6f, seed = 7)
        }
        SceneLayer.MID -> {
            d.crane(sx(a3 + 0.15f, layer), 0.66f * h, 0.16f * h, mid)
            d.crane(sx(a3 + 0.5f, layer), 0.66f * h, 0.13f * h, mid)
            d.drawRect(mid, topLeft = Offset(sx(a3 + 0.6f, layer), 0.6f * h),
                size = Size((a3 + 1.0f - (a3 + 0.6f)) * h * layer.parallax, 0.06f * h))
            pierLightsW(layer, a3 + 0.62f, a3 + 0.98f, 0.585f)
        }
        SceneLayer.NEAR -> {
            groundStrip(a3 - 0.05f, a4 + 0.1f, near)
            d.steamer(sx(a3 + 0.1f, layer), sx(a3 + 0.78f, layer), 0.69f * h, near, p, 1f, seed = a3 + 0.1f)
            d.lamppost(sx(a3 + 0.14f, layer), gy(a3 + 0.14f), 0.12f * h, near, p, 1f)
            d.lamppost(sx(a3 + 0.9f, layer), gy(a3 + 0.9f), 0.12f * h, near, p, 1f)
            d.bales(sx(a3 + 0.32f, layer), gy(a3 + 0.32f), near)
        }
    }
}

private fun Ctx.seg4Paris(layer: SceneLayer) {
    val a4 = anchor(4)
    val a5 = anchor(5)
    val far = p.far
    val mid = p.mid
    val near = p.near
    when (layer) {
        SceneLayer.FAR -> {
            roofsW(layer, a4 - 0.4f, a5 + 0.4f, 0.65f, 0.12f, far, seed = 17, chimneys = true, taperEnd = true)
            d.dome(sx(a4 + 0.3f, layer), 0.65f * h, 0.11f * h, far)
            d.spire(sx(a4 + 0.7f, layer), 0.65f * h, 0.28f * h, far)
        }
        SceneLayer.MID -> {
            roofsW(layer, a4 - 0.2f, a5 + 0.1f, 0.668f, 0.16f, mid, seed = 23, chimneys = true, taperEnd = true)
            windowsW(layer, a4 - 0.15f, a5, 0.57f, 0.655f, seed = 8)
        }
        SceneLayer.NEAR -> {
            groundStrip(a4 - 0.1f, a5 + 0.05f, near)
            d.bigArch(sx(a4 + 0.15f, layer), gy(a4 + 0.15f), 0.35f * h, 0.22f * h, near)
            d.train(sx(a4 + 0.55f, layer), gy(a4 + 0.55f), 0.2f, near, p, 1f)
            d.lamppost(sx(a4 + 0.02f, layer), gy(a4 + 0.02f), 0.2f * h, near, p, 1f)
            d.lamppost(sx(a4 + 1.0f, layer), gy(a4 + 1.0f), 0.19f * h, near, p, 1f)
            d.tree(sx(a5 - 0.12f, layer), gy(a5 - 0.12f), 0.13f * h, near, seed = a5 - 0.12f)
        }
    }
}

private fun Ctx.seg5Alps(layer: SceneLayer) {
    val a5 = anchor(5)
    val a6 = anchor(6)
    val far = p.far
    val mid = p.mid
    val near = p.near
    when (layer) {
        SceneLayer.FAR -> peaksW(layer, a5 - 0.6f, a6 + 0.8f, 0.66f, 0.34f, 3.7, far,
            lerp(lerp(far, Color.White, 0.75f), far, p.starAlpha * 0.7f))
        SceneLayer.MID -> {
            humpsW(layer, a5 - 0.3f, a6 + 0.4f, 0.68f, 0.2f, 7.3, mid)
            d.fir(sx(a5 + 0.2f, layer), 0.676f * h, 0.13f * h, mid, seed = a5 + 0.2f)
            d.fir(sx(a5 + 0.45f, layer), 0.676f * h, 0.1f * h, mid, seed = a5 + 0.45f)
            d.fir(sx(a5 + 0.9f, layer), 0.676f * h, 0.14f * h, mid, seed = a5 + 0.9f)
            d.fir(sx(a5 + 1.15f, layer), 0.676f * h, 0.1f * h, mid, seed = a5 + 1.15f)
        }
        SceneLayer.NEAR -> {
            groundStrip(a5 - 0.1f, a6 + 0.1f, near)
            // Snow edge along the path.
            edgeLine(a5 - 0.1f, a6 + 0.1f, lerp(near, Color.White, 0.5f), 0.6f)
            d.fir(sx(a5 + 0.05f, layer), gy(a5 + 0.05f), 0.2f * h, near, seed = a5 + 0.05f)
            d.fir(sx(a5 + 0.35f, layer), gy(a5 + 0.35f), 0.15f * h, near, seed = a5 + 0.35f)
            d.fir(sx(a6 - 0.35f, layer), gy(a6 - 0.35f), 0.22f * h, near, seed = a6 - 0.35f)
            d.fir(sx(a6 - 0.1f, layer), gy(a6 - 0.1f), 0.16f * h, near, seed = a6 - 0.1f)
            d.tunnel(sx(a5 + 0.82f, layer), gy(a5 + 0.82f), 0.2f * h, 0.11f * h, lerp(near, Color.Black, 0.55f))
            d.embankment(sx(a5 + 0.8f, layer), sx(a6 + 0.35f, layer), gy(a5 + 1.05f), 0.05f * h,
                lerp(near, mid, 0.2f), near)
        }
    }
}

private fun Ctx.seg6Turin(layer: SceneLayer) {
    val a6 = anchor(6)
    val a7 = anchor(7)
    val far = p.far
    val mid = p.mid
    val near = p.near
    when (layer) {
        SceneLayer.FAR -> {
            humpsW(layer, a6 - 0.4f, a7 + 0.5f, 0.66f, 0.12f, 4.1, far)
            d.dome(sx(a6 + 0.3f, layer), 0.66f * h, 0.11f * h, far)
            d.spire(sx(a6 + 0.62f, layer), 0.66f * h, 0.3f * h, far)
        }
        SceneLayer.MID -> {
            roofsW(layer, a6 - 0.15f, a7 + 0.2f, 0.668f, 0.08f, mid, seed = 31, chimneys = false)
            windowsW(layer, a6 - 0.1f, a7 + 0.1f, 0.6f, 0.655f, seed = 12)
            d.cypress(sx(a6 + 0.15f, layer), 0.668f * h, 0.16f * h, mid, seed = a6 + 0.15f)
            d.cypress(sx(a6 + 0.35f, layer), 0.668f * h, 0.12f * h, mid, seed = a6 + 0.35f)
            d.cypress(sx(a6 + 0.7f, layer), 0.668f * h, 0.15f * h, mid, seed = a6 + 0.7f)
            d.cypress(sx(a6 + 0.95f, layer), 0.668f * h, 0.11f * h, mid, seed = a6 + 0.95f)
            d.cypress(sx(a6 + 1.2f, layer), 0.668f * h, 0.14f * h, mid, seed = a6 + 1.2f)
        }
        SceneLayer.NEAR -> {
            groundStrip(a6 - 0.1f, a7 + 0.1f, near)
            d.arcade(sx(a6 + 0.05f, layer), sx(a6 + 0.5f, layer), gy(a6 + 0.28f), 0.13f * h, near)
            d.embankment(sx(a6 + 0.6f, layer), sx(a7 + 0.2f, layer), gy(a6 + 0.9f), 0.07f * h,
                lerp(near, mid, 0.25f), near)
            d.cypress(sx(a6 + 0.85f, layer), gy(a6 + 0.85f), 0.18f * h, near, seed = a6 + 0.85f)
        }
    }
}

private fun Ctx.seg7Brindisi(layer: SceneLayer) {
    val a7 = anchor(7)
    val a8 = anchor(8)
    val far = p.far
    val mid = p.mid
    val near = p.near
    when (layer) {
        SceneLayer.FAR -> {
            // Port roadstead; butt-joints at a8 with the open-sea band of segment 8
            // (same top edge and colour, so the joint is invisible).
            seaW(layer, a7 + 0.2f, a8, 0.52f, 0.78f, lerp(p.skyBottom, far, 0.5f),
                taperStart = 0.3f, taperEnd = 0f)
            d.crane(sx(a7 + 0.55f, layer), 0.52f * h, 0.08f * h, far)
            d.crane(sx(a7 + 0.8f, layer), 0.52f * h, 0.06f * h, far)
        }
        SceneLayer.MID -> {
            d.drawRect(mid, topLeft = Offset(sx(a7 + 0.05f, layer), 0.61f * h),
                size = Size(0.37f * h * layer.parallax, 0.05f * h))
            pierLightsW(layer, a7 + 0.07f, a7 + 0.4f, 0.6f)
            d.mast(sx(a7 + 0.55f, layer), 0.66f * h, 0.16f * h, mid)
            d.mast(sx(a7 + 0.7f, layer), 0.66f * h, 0.19f * h, mid)
            d.mast(sx(a7 + 0.85f, layer), 0.66f * h, 0.14f * h, mid)
            // A second steamer outfitting at the roadstead quay, behind the pier.
            d.steamer(sx(a7 + 0.45f, layer), sx(a8, layer), 0.78f * h, mid, p, 1f, seed = a7 + 0.45f)
        }
        SceneLayer.NEAR -> {
            val rampStart = TerrainProfile.rampStartX(layout)
            // Boarding basin: open water in front of the quay, visible under
            // the gangway piles — the ramp is a pier structure, not filled earth.
            waterFrontW(rampStart - 0.15f, a8 + 0.02f, 0.70f, lerp(p.skyBottom, near, 0.45f))
            // Stone quay, flat to the gangway foot.
            groundStrip(a7 - 0.1f, rampStart - 0.08f, lerp(near, mid, 0.12f))
            edgeLine(a7 - 0.1f, rampStart - 0.08f, lerp(near, p.light, 0.3f),
                0.3f * p.lightAlpha.coerceAtLeast(0.2f))
            // The gangway itself: planks on piles, meeting the deck at a8.
            gangwayW(rampStart, a8, near)
            d.bales(sx(a7 + 0.15f, layer), gy(a7 + 0.15f), near)
            d.lamppost(sx(a7 + 0.25f, layer), gy(a7 + 0.25f), 0.13f * h, near, p, 1f)
            d.lamppost(sx(a7 + 0.6f, layer), gy(a7 + 0.6f), 0.12f * h, near, p, 1f)
        }
    }
}

private fun Ctx.seg8Mediterranean(layer: SceneLayer) {
    val a8 = anchor(8)
    val a9 = anchor(9)
    val far = p.far
    val mid = p.mid
    val near = p.near
    when (layer) {
        SceneLayer.FAR -> {
            cloudsW(layer, a8 - 0.6f, a9 + 0.8f, lerp(p.skyBottom, Color.White, 0.4f))
            // Open sea: butt-jointed with the Brindisi roadstead at a8.
            seaW(layer, a8, a9 + 0.3f, 0.52f, 1.0f, lerp(p.skyBottom, far, 0.5f),
                taperStart = 0f, taperEnd = 0.3f)
            glintsW(layer, a8 + 0.05f, a9 + 0.2f, 0.60f, p.celestial, 0.25f + p.lightAlpha * 0.5f)
        }
        SceneLayer.MID -> {
            wavesW(layer, a8, a9 + 0.3f, 0.66f, mid)
            wavesW(layer, a8 + 0.07f, a9 + 0.3f, 0.72f, mid)
        }
        SceneLayer.NEAR -> {
            // The sea leg (P08): the hero stands on the steamer's deck. Below
            // the deck lip is the ship's own side — portholes, waterline — and
            // open water underneath; the bow meets the Brindisi gangway at a8,
            // the stern rounds off flush with the Suez bank (0.78) at a9.
            waterFrontW(a8 - 0.02f, a9 + 0.3f, 0.93f, lerp(p.skyBottom, near, 0.5f))
            shipHullW(a8, a9, near)
            edgeLine(a8, a9, lerp(near, p.light, 0.3f), 0.3f * p.lightAlpha.coerceAtLeast(0.2f))
            railingW(a8 + 0.03f, a9 - 0.03f, 0.055f, near)
            // Funnel with its smoke, standing on the deck.
            val funnelX = sx(a8 + 0.45f, layer)
            val deckY = gy(a8 + 0.45f)
            d.drawRect(near, topLeft = Offset(funnelX, deckY - 0.24f * h), size = Size(0.045f * h, 0.24f * h))
            d.drawRect(lerp(near, Color.White, 0.45f),
                topLeft = Offset(funnelX, deckY - 0.2f * h), size = Size(0.045f * h, 0.02f * h))
            d.smoke(funnelX + 0.022f * h, deckY - 0.26f * h, lerp(p.skyBottom, Color.White, 0.3f), 1f)
            d.mast(sx(a8 + 0.95f, layer), gy(a8 + 0.95f), 0.3f * h, near)
        }
    }
}

private fun Ctx.seg9Suez(layer: SceneLayer) {
    val a9 = anchor(9)
    val end = layout.end
    val far = p.far
    val mid = p.mid
    val near = p.near
    when (layer) {
        SceneLayer.FAR -> {
            humpsW(layer, a9 - 0.35f, end + 3.2f, 0.66f, 0.10f, 6.9, far)
            d.dome(sx(a9 + 0.35f, layer), 0.64f * h, 0.1f * h, far)
            d.minaret(sx(a9 + 0.5f, layer), 0.64f * h, 0.2f * h, far)
            d.minaret(sx(a9 + 0.75f, layer), 0.64f * h, 0.24f * h, far)
        }
        SceneLayer.MID -> {
            humpsW(layer, a9 - 0.2f, end + 1.5f, 0.672f, 0.06f, 9.4, mid)
            d.palm(sx(a9 + 0.15f, layer), 0.672f * h, 0.22f * h, mid, seed = a9 + 0.15f)
            d.palm(sx(a9 + 0.4f, layer), 0.672f * h, 0.17f * h, mid, seed = a9 + 0.4f)
            d.palm(sx(a9 + 0.85f, layer), 0.672f * h, 0.19f * h, mid, seed = a9 + 0.85f)
        }
        SceneLayer.NEAR -> {
            // Suez canal: flat water behind the near bank the hero walks on.
            val water = lerp(p.skyBottom, far, 0.4f)
            val path = Path()
            val steps = 16
            for (s in 0..steps) {
                val wx = a9 + 0.04f + (end - a9 - 0.04f) * s / steps
                val x = sx(wx, SceneLayer.NEAR)
                if (s == 0) path.moveTo(x, 0.7f * h) else path.lineTo(x, 0.7f * h)
            }
            for (s in steps downTo 0) {
                val wx = a9 + 0.04f + (end - a9 - 0.04f) * s / steps
                path.lineTo(sx(wx, SceneLayer.NEAR), gy(wx))
            }
            path.close()
            d.drawPath(path, Brush.verticalGradient(
                0f to water.copy(alpha = 0f), 0.45f to water,
                startY = 0.7f * h, endY = 0.78f * h,
            ))
            d.steamer(sx(a9 + 0.3f, layer), sx(a9 + 0.62f, layer), 0.745f * h, near, p, 1f, seed = a9 + 0.3f)
            d.mast(sx(a9 + 0.8f, layer), 0.75f * h, 0.2f * h, near)
            groundStrip(a9, end, near)
        }
    }
}

private fun drawSegment(ctx: Ctx, segment: Int, layer: SceneLayer) {
    when (segment) {
        0 -> ctx.seg0London(layer)
        1 -> ctx.seg1Departure(layer)
        2 -> ctx.seg2Dover(layer)
        3 -> ctx.seg3Calais(layer)
        4 -> ctx.seg4Paris(layer)
        5 -> ctx.seg5Alps(layer)
        6 -> ctx.seg6Turin(layer)
        7 -> ctx.seg7Brindisi(layer)
        8 -> ctx.seg8Mediterranean(layer)
        9 -> ctx.seg9Suez(layer)
    }
}

/** World range visible on screen, including the parallax margin of the far layer. */
internal fun visibleWorldRange(cameraX: Float, viewWidth: Float): ClosedFloatingPointRange<Float> {
    val pad = 0.9f
    val left = cameraX - JourneyCamera.HERO_FRACTION * viewWidth / SceneLayer.FAR.parallax - pad
    val right = cameraX + (1f - JourneyCamera.HERO_FRACTION) * viewWidth / SceneLayer.FAR.parallax + pad
    return left..right
}

/**
 * Draws the continuous world for one frame. Only segments intersecting the
 * visible window (plus the far-parallax margin) are painted. All coordinates
 * are world-anchored, so frames differ only by camera position.
 */
internal fun DrawScope.drawWorldSegments(
    layout: WorldLayout,
    cameraX: Float,
    viewWidth: Float,
    palette: ScenePalette,
) {
    val ctx = Ctx(this, layout, cameraX, viewWidth, palette)
    val range = visibleWorldRange(cameraX, viewWidth)
    val first = floor((range.start - WORLD_MARGIN_START) / SEGMENT_LENGTH).toInt()
        .coerceIn(0, layout.stopCount - 1)
    val last = floor((range.endInclusive - WORLD_MARGIN_START) / SEGMENT_LENGTH).toInt()
        .coerceIn(0, layout.stopCount - 1)
    for (layer in SceneLayer.entries) {
        for (segment in first..last) {
            drawSegment(ctx, segment, layer)
        }
        if (layer == SceneLayer.FAR) horizonHaze(palette)
    }
}
