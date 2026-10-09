package com.boxowl.aroundtheworld.expedition.scene

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import com.boxowl.aroundtheworld.expedition.ScenePalette
import com.boxowl.aroundtheworld.expedition.frac
import com.boxowl.aroundtheworld.expedition.mast
import com.boxowl.aroundtheworld.expedition.smoke
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Hand-worked seeded silhouettes (P08), replacing the identical geometric
 * placeholders of P07. Every primitive takes a [seed] (call sites pass the
 * object's world X), so each instance has its own trunk lean, crown massing,
 * frond spread or superstructure layout — while staying stable across frames.
 */
private fun sh(seed: Float, i: Int): Float =
    frac(sin(seed * 12.9898 + i * 78.233) * 43758.5453)

/** Smooth closed blob through [points] jittered points of an ellipse. */
private fun Path.blob(cx: Float, cy: Float, rx: Float, ry: Float, seed: Float, points: Int) {
    val pts = (0 until points).map { i ->
        val ang = (2 * PI * i / points + (sh(seed, i) - 0.5f) * 0.6).toFloat()
        val rj = 0.72f + 0.55f * sh(seed, i + 97)
        Offset(cx + cos(ang.toDouble()).toFloat() * rx * rj, cy + sin(ang.toDouble()).toFloat() * ry * rj)
    }
    val firstMid = Offset((pts.last().x + pts[0].x) / 2, (pts.last().y + pts[0].y) / 2)
    moveTo(firstMid.x, firstMid.y)
    for (i in pts.indices) {
        val p = pts[i]
        val n = pts[(i + 1) % pts.size]
        quadraticTo(p.x, p.y, (p.x + n.x) / 2, (p.y + n.y) / 2)
    }
    close()
}

/** Deciduous tree: broken leaning trunk, one side branch, 2–3 crown submasses. */
internal fun DrawScope.tree(x: Float, gy: Float, hgt: Float, color: Color, seed: Float = 0f) {
    val lean = (sh(seed, 1) - 0.5f) * 0.22f * hgt
    val kink = Offset(x + lean * 0.4f + (sh(seed, 2) - 0.5f) * 0.08f * hgt, gy - hgt * 0.34f)
    val top = Offset(x + lean, gy - hgt * 0.6f)
    drawLine(color, Offset(x, gy), kink, strokeWidth = hgt * 0.055f, cap = StrokeCap.Round)
    drawLine(color, kink, top, strokeWidth = hgt * 0.038f, cap = StrokeCap.Round)
    // Side branch reaching into a lower submass.
    val branchEnd = Offset(kink.x + (sh(seed, 3) - 0.25f) * 0.5f * hgt, kink.y - hgt * 0.16f)
    drawLine(color, kink, branchEnd, strokeWidth = hgt * 0.022f, cap = StrokeCap.Round)
    val crown = Path()
    val masses = 2 + (sh(seed, 4) * 1.9f).toInt()
    for (m in 0 until masses) {
        val mx = top.x + (sh(seed, 10 + m) - 0.5f) * 0.42f * hgt
        val my = top.y - hgt * (0.06f + 0.16f * sh(seed, 20 + m))
        val mrx = hgt * (0.2f + 0.12f * sh(seed, 30 + m))
        val mry = mrx * (0.68f + 0.25f * sh(seed, 40 + m))
        crown.blob(mx, my, mrx, mry, seed + m * 13.7f, 6 + (sh(seed, 50 + m) * 3.9f).toInt())
    }
    drawPath(crown, color)
}

/** Fir: stacked drooping curved tiers over a short trunk; tier count/widths vary. */
internal fun DrawScope.fir(x: Float, gy: Float, hgt: Float, color: Color, seed: Float = 0f) {
    drawRect(color, topLeft = Offset(x - hgt * 0.025f, gy - hgt * 0.14f), size = Size(hgt * 0.05f, hgt * 0.14f))
    val tiers = 4 + (sh(seed, 5) * 2.9f).toInt()
    val path = Path()
    for (i in 0 until tiers) {
        val f = i.toFloat() / tiers
        val yTop = gy - hgt * (1f - f * 0.86f)
        val yBase = gy - hgt * (1f - (i + 1.35f) / (tiers + 0.6f) * 0.86f)
        val w = hgt * (0.09f + 0.24f * f) * (0.85f + 0.3f * sh(seed, 10 + i))
        val sag = hgt * 0.05f * (0.5f + sh(seed, 20 + i))
        path.moveTo(x, yTop)
        path.lineTo(x - w, yBase)
        path.quadraticTo(x, yBase - sag, x + w, yBase)
        path.close()
    }
    drawPath(path, color)
}

/** Cypress: slim flame with an independent wobble per side and a leaning tip. */
internal fun DrawScope.cypress(x: Float, gy: Float, hgt: Float, color: Color, seed: Float = 0f) {
    val lean = (sh(seed, 1) - 0.5f) * 0.14f * hgt
    val wb = hgt * (0.09f + 0.05f * sh(seed, 2))
    val path = Path().apply {
        moveTo(x - wb * 0.7f, gy)
        quadraticTo(x - wb * (1.1f + 0.4f * sh(seed, 3)), gy - hgt * 0.35f,
            x - wb * (0.75f + 0.3f * sh(seed, 4)), gy - hgt * 0.66f)
        quadraticTo(x - wb * 0.55f, gy - hgt * 0.87f, x + lean, gy - hgt)
        quadraticTo(x + wb * 0.6f, gy - hgt * 0.8f,
            x + wb * (0.8f + 0.3f * sh(seed, 5)), gy - hgt * 0.6f)
        quadraticTo(x + wb * (1.05f + 0.35f * sh(seed, 6)), gy - hgt * 0.3f, x + wb * 0.7f, gy)
        close()
    }
    drawPath(path, color)
}

/** Palm: curved tapering trunk and feathered fronds (spine + leaflet strokes). */
internal fun DrawScope.palm(x: Float, gy: Float, hgt: Float, color: Color, seed: Float = 0f) {
    val lean = hgt * (0.08f + 0.14f * sh(seed, 1)) * (if (sh(seed, 2) > 0.5f) 1f else -1f)
    val top = Offset(x + lean, gy - hgt)
    val trunkCtrl = Offset(x - lean * 0.6f, gy - hgt * 0.5f)
    // Tapered trunk: wide base stroke, thinner upper stroke.
    drawPath(Path().apply {
        moveTo(x, gy)
        quadraticTo(trunkCtrl.x, trunkCtrl.y, top.x, top.y)
    }, color, style = Stroke(width = hgt * 0.055f, cap = StrokeCap.Round))
    drawPath(Path().apply {
        moveTo((x + trunkCtrl.x) / 2, (gy + trunkCtrl.y) / 2)
        quadraticTo(trunkCtrl.x * 0.4f + top.x * 0.6f, trunkCtrl.y * 0.4f + top.y * 0.6f, top.x, top.y)
    }, color, style = Stroke(width = hgt * 0.032f, cap = StrokeCap.Round))
    // Crown of feathered fronds fanning around the top.
    val fronds = 6 + (sh(seed, 3) * 3.9f).toInt()
    for (f in 0 until fronds) {
        val spread = -0.95f + 1.9f * (f + 0.5f) / fronds + (sh(seed, 10 + f) - 0.5f) * 0.22f
        val len = hgt * (0.42f + 0.2f * sh(seed, 20 + f))
        val droop = hgt * (0.18f + 0.16f * sh(seed, 30 + f))
        val tip = Offset(top.x + spread * len, top.y + droop - len * 0.16f * (1f - spread * spread))
        val ctrl = Offset(top.x + spread * len * 0.55f, top.y - len * 0.22f)
        val spine = Path().apply {
            moveTo(top.x, top.y)
            quadraticTo(ctrl.x, ctrl.y, tip.x, tip.y)
        }
        drawPath(spine, color, style = Stroke(width = hgt * 0.016f, cap = StrokeCap.Round))
        // Leaflets: short strokes hanging off the spine, shrinking to the tip.
        val leaflets = 5
        for (l in 1..leaflets) {
            val t = l / (leaflets + 1f)
            val bx = (1 - t) * (1 - t) * top.x + 2 * (1 - t) * t * ctrl.x + t * t * tip.x
            val by = (1 - t) * (1 - t) * top.y + 2 * (1 - t) * t * ctrl.y + t * t * tip.y
            val ll = hgt * 0.075f * (1f - t * 0.55f)
            drawLine(color, Offset(bx, by), Offset(bx - ll * 0.35f, by + ll),
                strokeWidth = hgt * 0.011f, cap = StrokeCap.Round)
            drawLine(color, Offset(bx, by), Offset(bx + ll * 0.35f, by + ll * 0.9f),
                strokeWidth = hgt * 0.011f, cap = StrokeCap.Round)
        }
    }
}

/** Layered elongated cloud bank: 2–3 flattened overlapping contours. */
internal fun DrawScope.cloudBank(cx: Float, cy: Float, wd: Float, color: Color, seed: Float = 0f) {
    val layers = 2 + (sh(seed, 1) * 1.9f).toInt()
    for (l in 0 until layers) {
        val lw = wd * (1f - l * 0.24f) * (0.85f + 0.3f * sh(seed, 10 + l))
        val lh = lw * (0.2f + 0.08f * sh(seed, 20 + l))
        val lx = cx + (sh(seed, 30 + l) - 0.5f) * wd * 0.3f
        val ly = cy + l * lh * 0.55f
        val path = Path().apply { blob(lx, ly, lw / 2, lh / 2, seed + l * 7.3f, 7) }
        drawPath(path, color, alpha = 0.34f - l * 0.08f)
    }
}

/**
 * XIX-century steamer with a bow sheer, two superstructure tiers, raked funnel,
 * ventilator cowls, masts with rigging and a porthole row (lit at dusk/night).
 * [waterY] is the waterline; the hull top sits ~0.07 scene heights above it.
 */
internal fun DrawScope.steamer(
    x0: Float, x1: Float, waterY: Float, color: Color, p: ScenePalette, a: Float, seed: Float = 0f,
) {
    val h = size.height
    val len = x1 - x0
    val hullH = h * 0.07f
    val deckY = waterY - hullH
    val sheer = hullH * 0.28f
    // Hull: raised bow on the right, rounded stern on the left.
    val hull = Path().apply {
        moveTo(x1, deckY - sheer)
        lineTo(x0 + len * 0.06f, deckY)
        quadraticTo(x0 - len * 0.015f, deckY + hullH * 0.25f, x0 + len * 0.015f, waterY - hullH * 0.2f)
        lineTo(x0 + len * 0.08f, waterY)
        lineTo(x1 - len * 0.1f, waterY)
        quadraticTo(x1 + len * 0.01f, waterY - hullH * 0.45f, x1, deckY - sheer)
        close()
    }
    drawPath(hull, color)
    // Boot-top stripe along the waterline.
    drawLine(lerp(color, Color.White, 0.25f), Offset(x0 + len * 0.05f, waterY - hullH * 0.18f),
        Offset(x1 - len * 0.06f, waterY - hullH * 0.18f), strokeWidth = h * 0.004f, alpha = 0.6f)
    // Portholes.
    val porthole = if (p.lightAlpha > 0f) p.light else lerp(color, Color.White, 0.3f)
    val portholeAlpha = if (p.lightAlpha > 0f) 0.7f * p.lightAlpha * a else 0.35f
    val count = (len / (h * 0.055f)).toInt().coerceIn(3, 9)
    for (i in 0 until count) {
        val px = x0 + len * 0.14f + (len * 0.74f) * i / (count - 1).coerceAtLeast(1)
        drawCircle(porthole, h * 0.005f, Offset(px, deckY + hullH * 0.45f), alpha = portholeAlpha)
    }
    // Two superstructure tiers with a bridge on top.
    drawRect(color, topLeft = Offset(x0 + len * 0.22f, deckY - sheer - h * 0.045f),
        size = Size(len * 0.56f, h * 0.045f + sheer))
    drawRect(color, topLeft = Offset(x0 + len * 0.3f, deckY - sheer - h * 0.085f),
        size = Size(len * 0.4f, h * 0.04f))
    drawRect(color, topLeft = Offset(x0 + len * 0.42f, deckY - sheer - h * 0.115f),
        size = Size(len * 0.14f, h * 0.03f))
    // Raked funnel with a pale band.
    val funnelX = x0 + len * (0.38f + 0.14f * sh(seed, 7))
    val funnelTop = deckY - sheer - h * (0.16f + 0.03f * sh(seed, 8))
    val rake = h * 0.018f
    drawPath(Path().apply {
        moveTo(funnelX, deckY - sheer - h * 0.085f)
        lineTo(funnelX + rake, funnelTop)
        lineTo(funnelX + h * 0.032f + rake, funnelTop)
        lineTo(funnelX + h * 0.032f, deckY - sheer - h * 0.085f)
        close()
    }, color)
    drawLine(lerp(color, Color.White, 0.5f), Offset(funnelX + rake * 0.7f, funnelTop + h * 0.012f),
        Offset(funnelX + h * 0.032f + rake * 0.7f, funnelTop + h * 0.012f), strokeWidth = h * 0.008f)
    smoke(funnelX + h * 0.016f + rake, funnelTop - h * 0.02f, lerp(p.skyBottom, Color.White, 0.3f), a)
    // Ventilator cowls between funnel and masts.
    for (v in 0..1) {
        val vx = x0 + len * (0.3f + 0.34f * v + 0.05f * sh(seed, 20 + v))
        val vy = deckY - sheer - h * 0.085f
        drawLine(color, Offset(vx, vy), Offset(vx, vy - h * 0.03f), strokeWidth = h * 0.008f)
        drawArc(color, 180f, 180f, useCenter = true,
            topLeft = Offset(vx - h * 0.013f, vy - h * 0.042f), size = Size(h * 0.026f, h * 0.026f))
    }
    // Masts with rigging down to bow and stern.
    val foreX = x0 + len * 0.87f
    val aftX = x0 + len * 0.1f
    mast(foreX, deckY - sheer, h * 0.13f, color)
    mast(aftX, deckY, h * 0.11f, color)
    val rig = Stroke(width = h * 0.0028f)
    drawPath(Path().apply {
        moveTo(foreX, deckY - sheer - h * 0.125f)
        lineTo(x1, deckY - sheer)
    }, color, style = rig, alpha = 0.8f)
    drawPath(Path().apply {
        moveTo(aftX, deckY - h * 0.105f)
        lineTo(x0 + len * 0.03f, deckY)
    }, color, style = rig, alpha = 0.8f)
}
