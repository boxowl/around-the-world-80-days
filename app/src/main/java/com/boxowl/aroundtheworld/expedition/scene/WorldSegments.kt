package com.boxowl.aroundtheworld.expedition.scene

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import com.boxowl.aroundtheworld.expedition.ScenePalette
import com.boxowl.aroundtheworld.expedition.arcade
import com.boxowl.aroundtheworld.expedition.bales
import com.boxowl.aroundtheworld.expedition.bigArch
import com.boxowl.aroundtheworld.expedition.clockTower
import com.boxowl.aroundtheworld.expedition.crane
import com.boxowl.aroundtheworld.expedition.cypress
import com.boxowl.aroundtheworld.expedition.dome
import com.boxowl.aroundtheworld.expedition.embankment
import com.boxowl.aroundtheworld.expedition.fir
import com.boxowl.aroundtheworld.expedition.frac
import com.boxowl.aroundtheworld.expedition.glow
import com.boxowl.aroundtheworld.expedition.horizonHaze
import com.boxowl.aroundtheworld.expedition.lamppost
import com.boxowl.aroundtheworld.expedition.lighthouse
import com.boxowl.aroundtheworld.expedition.mast
import com.boxowl.aroundtheworld.expedition.minaret
import com.boxowl.aroundtheworld.expedition.mix
import com.boxowl.aroundtheworld.expedition.palm
import com.boxowl.aroundtheworld.expedition.smoke
import com.boxowl.aroundtheworld.expedition.spire
import com.boxowl.aroundtheworld.expedition.steamer
import com.boxowl.aroundtheworld.expedition.train
import com.boxowl.aroundtheworld.expedition.tree
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

/** Lighter road ribbon following the terrain profile. */
private fun Ctx.roadRibbon(x0: Float, x1: Float, color: Color) {
    val path = Path()
    val steps = 28
    for (s in 0..steps) {
        val wx = x0 + (x1 - x0) * s / steps
        val x = sx(wx, SceneLayer.NEAR)
        val y = gy(wx)
        if (s == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    for (s in steps downTo 0) {
        val wx = x0 + (x1 - x0) * s / steps
        path.lineTo(sx(wx, SceneLayer.NEAR), gy(wx) + h * 0.022f)
    }
    path.close()
    d.drawPath(path, color)
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

/** Rolling silhouette hills spanning a world interval, ending at the baseline. */
private fun Ctx.humpsW(layer: SceneLayer, x0: Float, x1: Float, gyFrac: Float, ampFrac: Float, seed: Double, color: Color) {
    val gyPx = gyFrac * h
    val amp = ampFrac * h
    val path = Path()
    var wx = x0
    var k = 0
    path.moveTo(sx(wx, layer), gyPx + 2f)
    while (wx < x1) {
        val segW = 0.4f * (0.7f + 0.6f * frac(sin(seed + k * 5.7) * 43.1))
        val peak = gyPx - amp * (0.5f + 0.5f * frac(sin(seed + k * 9.3) * 71.7))
        path.quadraticTo(sx(wx + segW * 0.5f, layer), peak - amp * 0.4f, sx(wx + segW, layer), gyPx)
        wx += segW
        k++
    }
    path.lineTo(sx(x1, layer), h)
    path.lineTo(sx(x0, layer), h)
    path.close()
    d.drawPath(path, color)
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

/** Telegraph poles with sagging wires, planted on the terrain profile (NEAR). */
private fun Ctx.telegraphW(x0: Float, x1: Float, color: Color) {
    val sw = h * 0.007f
    val tops = mutableListOf<Offset>()
    var wx = x0
    var i = 0
    while (wx < x1) {
        val x = sx(wx, SceneLayer.NEAR)
        val g = gy(wx)
        val hgt = h * (0.17f + 0.04f * hash(97, i, 3.7))
        d.drawLine(color, Offset(x, g), Offset(x, g - hgt), strokeWidth = sw)
        d.drawLine(color, Offset(x - hgt * 0.12f, g - hgt * 0.85f), Offset(x + hgt * 0.12f, g - hgt * 0.85f),
            strokeWidth = sw)
        tops += Offset(x, g - hgt * 0.85f)
        wx += 0.3f
        i++
    }
    for (j in 0 until tops.size - 1) {
        val a0 = tops[j]
        val b0 = tops[j + 1]
        d.drawPath(Path().apply {
            moveTo(a0.x, a0.y)
            quadraticTo((a0.x + b0.x) / 2, maxOf(a0.y, b0.y) + h * 0.03f, b0.x, b0.y)
        }, color, style = Stroke(width = sw * 0.6f))
    }
}

/** Low fence following the terrain profile (NEAR). */
private fun Ctx.fenceW(x0: Float, x1: Float, color: Color) {
    val sw = h * 0.005f
    var wx = x0
    while (wx <= x1) {
        val x = sx(wx, SceneLayer.NEAR)
        val g = gy(wx)
        d.drawLine(color, Offset(x, g), Offset(x, g - h * 0.035f), strokeWidth = sw)
        wx += 0.07f
    }
    d.drawLine(color, Offset(sx(x0, SceneLayer.NEAR), gy(x0) - h * 0.03f),
        Offset(sx(x1, SceneLayer.NEAR), gy(x1) - h * 0.03f), strokeWidth = sw)
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

/** Soft cloud puffs drifting with the far layer, seeded by world position. */
private fun Ctx.cloudsW(layer: SceneLayer, x0: Float, x1: Float, color: Color) {
    var wx = x0
    var i = 0
    while (wx < x1) {
        val cx = sx(wx, layer)
        val cy = h * (0.1f + 0.14f * hash(71, i, 3.3))
        val cr = h * (0.045f + 0.03f * hash(71, i, 5.9))
        d.drawCircle(color, cr, Offset(cx, cy), alpha = 0.35f)
        d.drawCircle(color, cr * 0.7f, Offset(cx - cr, cy + cr * 0.3f), alpha = 0.3f)
        d.drawCircle(color, cr * 0.75f, Offset(cx + cr * 1.1f, cy + cr * 0.25f), alpha = 0.3f)
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
            d.tree(sx(a1 - 0.2f, layer), gy(a1 - 0.2f), 0.12f * h, near)
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
            humpsW(layer, a1 - 0.5f, a2 + 0.7f, 0.66f, 0.10f, 2.7, far)
            roofsW(layer, a1 - 0.3f, a1 + 0.6f, 0.65f, 0.07f, far, seed = 5, chimneys = false, sparse = true)
        }
        SceneLayer.MID -> {
            humpsW(layer, a1 - 0.3f, a2 + 0.4f, 0.672f, 0.05f, 5.1, mid)
            roofsW(layer, a1 - 0.1f, a1 + 0.5f, 0.668f, 0.11f, mid, seed = 9, chimneys = true, sparse = true)
            d.tree(sx(a1 + 0.7f, layer), 0.672f * h, 0.10f * h, mid)
            d.tree(sx(a1 + 1.05f, layer), 0.672f * h, 0.08f * h, mid)
        }
        SceneLayer.NEAR -> {
            groundStrip(a1 - 0.05f, a2 + 0.1f, near)
            roadRibbon(a1 - 0.05f, a2 + 0.1f, lerp(near, far, 0.5f))
            telegraphW(a1 + 0.05f, a2 + 0.25f, near)
            fenceW(a1 + 0.35f, a1 + 0.95f, near)
            d.tree(sx(a1 + 0.25f, layer), gy(a1 + 0.25f), 0.12f * h, near)
            d.tree(sx(a2 - 0.3f, layer), gy(a2 - 0.3f), 0.15f * h, near)
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
            // White cliffs: stepped flat-top massif standing on the shore at Dover;
            // it ends mid-crossing and the open Channel takes over.
            val gyPx = 0.68f * h
            val cliff = Path().apply {
                moveTo(sx(a2 + 0.1f, layer), gyPx)
                lineTo(sx(a2 + 0.2f, layer), gyPx - h * 0.2f)
                lineTo(sx(a2 + 0.4f, layer), gyPx - h * 0.22f)
                lineTo(sx(a2 + 0.5f, layer), gyPx - h * 0.24f)
                lineTo(sx(a2 + 0.8f, layer), gyPx - h * 0.25f)
                lineTo(sx(a2 + 1.0f, layer), gyPx - h * 0.08f)
                lineTo(sx(a2 + 1.0f, layer), gyPx)
                close()
            }
            d.drawPath(cliff, lerp(lerp(far, Color(0xFFF0EBDD), 0.6f), far, p.starAlpha * 0.85f))
        }
        SceneLayer.NEAR -> {
            groundStrip(a2 - 0.1f, a3 + 0.05f, near)
            railingW(a2 - 0.05f, a2 + 0.45f, 0.06f, near)
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
            d.steamer(sx(a3 + 0.1f, layer), sx(a3 + 0.78f, layer), 0.69f * h, near, p, 1f)
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
            d.tree(sx(a5 - 0.12f, layer), gy(a5 - 0.12f), 0.13f * h, near)
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
            d.fir(sx(a5 + 0.2f, layer), 0.676f * h, 0.13f * h, mid)
            d.fir(sx(a5 + 0.45f, layer), 0.676f * h, 0.1f * h, mid)
            d.fir(sx(a5 + 0.9f, layer), 0.676f * h, 0.14f * h, mid)
            d.fir(sx(a5 + 1.15f, layer), 0.676f * h, 0.1f * h, mid)
        }
        SceneLayer.NEAR -> {
            groundStrip(a5 - 0.1f, a6 + 0.1f, near)
            // Snow edge along the path.
            edgeLine(a5 - 0.1f, a6 + 0.1f, lerp(near, Color.White, 0.5f), 0.6f)
            d.fir(sx(a5 + 0.05f, layer), gy(a5 + 0.05f), 0.2f * h, near)
            d.fir(sx(a5 + 0.35f, layer), gy(a5 + 0.35f), 0.15f * h, near)
            d.fir(sx(a6 - 0.35f, layer), gy(a6 - 0.35f), 0.22f * h, near)
            d.fir(sx(a6 - 0.1f, layer), gy(a6 - 0.1f), 0.16f * h, near)
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
            d.cypress(sx(a6 + 0.15f, layer), 0.668f * h, 0.16f * h, mid)
            d.cypress(sx(a6 + 0.35f, layer), 0.668f * h, 0.12f * h, mid)
            d.cypress(sx(a6 + 0.7f, layer), 0.668f * h, 0.15f * h, mid)
            d.cypress(sx(a6 + 0.95f, layer), 0.668f * h, 0.11f * h, mid)
            d.cypress(sx(a6 + 1.2f, layer), 0.668f * h, 0.14f * h, mid)
        }
        SceneLayer.NEAR -> {
            groundStrip(a6 - 0.1f, a7 + 0.1f, near)
            d.arcade(sx(a6 + 0.05f, layer), sx(a6 + 0.5f, layer), gy(a6 + 0.28f), 0.13f * h, near)
            d.embankment(sx(a6 + 0.6f, layer), sx(a7 + 0.2f, layer), gy(a6 + 0.9f), 0.07f * h,
                lerp(near, mid, 0.25f), near)
            d.cypress(sx(a6 + 0.85f, layer), gy(a6 + 0.85f), 0.18f * h, near)
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
            // The steamer waiting at the pier; its deck line meets the boarding ramp at a8.
            d.steamer(sx(a7 + 0.45f, layer), sx(a7 + 1.05f, layer), 0.78f * h, mid, p, 1f)
        }
        SceneLayer.NEAR -> {
            // Stone pier rising in a long gangway ramp to deck level at a8.
            groundStrip(a7 - 0.1f, a8 + 0.001f, lerp(near, mid, 0.12f))
            edgeLine(a7 - 0.1f, a8, lerp(near, p.light, 0.3f), 0.3f * p.lightAlpha.coerceAtLeast(0.2f))
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
            // The steamer deck is the walking surface: it begins exactly where the
            // Brindisi ramp ends (a8) and ends flush with the Suez bank (a9).
            groundStrip(a8, a9 + 0.001f, near)
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
            d.palm(sx(a9 + 0.15f, layer), 0.672f * h, 0.22f * h, mid)
            d.palm(sx(a9 + 0.4f, layer), 0.672f * h, 0.17f * h, mid)
            d.palm(sx(a9 + 0.85f, layer), 0.672f * h, 0.19f * h, mid)
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
            d.steamer(sx(a9 + 0.3f, layer), sx(a9 + 0.62f, layer), 0.745f * h, near, p, 1f)
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
