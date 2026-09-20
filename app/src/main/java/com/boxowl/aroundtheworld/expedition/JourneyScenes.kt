package com.boxowl.aroundtheworld.expedition

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import java.time.LocalTime
import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.sin

/** Fixed local-time boundaries of the expedition zone, see docs/design/p04-scenes.md. */
internal enum class DayPhase { NIGHT, DAWN, DAY, SUNSET }

internal fun dayPhaseAt(time: LocalTime): DayPhase = when (time.hour) {
    in 5..7 -> DayPhase.DAWN
    in 8..16 -> DayPhase.DAY
    in 17..21 -> DayPhase.SUNSET
    else -> DayPhase.NIGHT
}

/** Scene descriptors for the ten chapter-1 thresholds, in route order (see p04-scenes.md table). */
internal enum class SceneKind(
    val stopId: String,
    val label: String,
    val details: String,
    val haze: Float = 0f,
    val warmth: Float = 0f,
) {
    LONDON("london", "Лондон", "вокзал, паровоз с дымом и тёплый фонарь на платформе"),
    DEPARTURE("departure", "За лондонскими крышами", "редеющие крыши, поля и телеграфные столбы вдоль дороги"),
    DOVER("dover", "Дувр", "белые скалы и маяк над линией моря", haze = 0.12f),
    CALAIS("calais", "Кале", "паром с мачтами у причала и огни порта", haze = 0.1f),
    PARIS("paris", "Париж", "купола и мансардные крыши, арка вокзала и поезд"),
    ALPS("alps", "Альпы", "снежные вершины, ели и портал тоннеля"),
    TURIN("turin", "Турин", "холмы, купол со шпилем, кипарисы и аркады"),
    BRINDISI("brindisi", "Бриндизи", "порт в морской дымке, пароход у причала", haze = 0.3f),
    MEDITERRANEAN("mediterranean", "Средиземное море", "открытое море, палуба и труба парохода", haze = 0.2f),
    SUEZ("suez", "Суэц", "дюны, купола и минареты, пальмы и мачты канала", warmth = 0.5f),
}

/** Scenes aligned with [Expedition.stops] indices, so threshold scaling needs no scene changes. */
internal val JOURNEY_SCENES: List<SceneKind> =
    FIRST_LEG.map { stop -> SceneKind.entries.first { it.stopId == stop.id } }

internal data class ScenePalette(
    val skyTop: Color,
    val skyMid: Color,
    val skyBottom: Color,
    val far: Color,
    val mid: Color,
    val near: Color,
    val celestial: Color,
    val starAlpha: Float,
    val light: Color,
    val lightAlpha: Float,
)

private val NIGHT_PALETTE = ScenePalette(
    skyTop = Color(0xFF040914), skyMid = Color(0xFF0A1830), skyBottom = Color(0xFF132A46),
    far = Color(0xFF10233C), mid = Color(0xFF0A182B), near = Color(0xFF050D19),
    celestial = Color(0xFFE9E4CF), starAlpha = 1f, light = Color(0xFFFFC57A), lightAlpha = 1f,
)
private val DAWN_PALETTE = ScenePalette(
    skyTop = Color(0xFF39587F), skyMid = Color(0xFFAE7A74), skyBottom = Color(0xFFE9B46C),
    far = Color(0xFF4E5669), mid = Color(0xFF353C4F), near = Color(0xFF1F2534),
    celestial = Color(0xFFFFD9A3), starAlpha = 0.12f, light = Color(0xFFFFC57A), lightAlpha = 0.35f,
)
private val DAY_PALETTE = ScenePalette(
    skyTop = Color(0xFF6E9CC8), skyMid = Color(0xFFA9C6DE), skyBottom = Color(0xFFDAE4E4),
    far = Color(0xFF8498AA), mid = Color(0xFF54687C), near = Color(0xFF2E4050),
    celestial = Color(0xFFFFF3C4), starAlpha = 0f, light = Color(0xFFFFC57A), lightAlpha = 0f,
)
private val SUNSET_PALETTE = ScenePalette(
    skyTop = Color(0xFF3A2A52), skyMid = Color(0xFF8A4660), skyBottom = Color(0xFFDA7040),
    far = Color(0xFF4B3651), mid = Color(0xFF342640), near = Color(0xFF1E1729),
    celestial = Color(0xFFFFB26B), starAlpha = 0.3f, light = Color(0xFFFFC57A), lightAlpha = 0.75f,
)

internal fun phasePalette(phase: DayPhase): ScenePalette = when (phase) {
    DayPhase.NIGHT -> NIGHT_PALETTE
    DayPhase.DAWN -> DAWN_PALETTE
    DayPhase.DAY -> DAY_PALETTE
    DayPhase.SUNSET -> SUNSET_PALETTE
}

internal fun mix(a: Float, b: Float, t: Float): Float = a + (b - a) * t

internal fun lerp(a: ScenePalette, b: ScenePalette, t: Float): ScenePalette = ScenePalette(
    skyTop = lerp(a.skyTop, b.skyTop, t),
    skyMid = lerp(a.skyMid, b.skyMid, t),
    skyBottom = lerp(a.skyBottom, b.skyBottom, t),
    far = lerp(a.far, b.far, t),
    mid = lerp(a.mid, b.mid, t),
    near = lerp(a.near, b.near, t),
    celestial = lerp(a.celestial, b.celestial, t),
    starAlpha = mix(a.starAlpha, b.starAlpha, t),
    light = lerp(a.light, b.light, t),
    lightAlpha = mix(a.lightAlpha, b.lightAlpha, t),
)

/** Palettes blend within half an hour around each fixed phase boundary. */
internal fun paletteFor(time: LocalTime): ScenePalette {
    val minutes = time.hour * 60 + time.minute
    val boundaries = listOf(
        Triple(300, DayPhase.NIGHT, DayPhase.DAWN),
        Triple(480, DayPhase.DAWN, DayPhase.DAY),
        Triple(1020, DayPhase.DAY, DayPhase.SUNSET),
        Triple(1320, DayPhase.SUNSET, DayPhase.NIGHT),
    )
    for ((at, before, after) in boundaries) {
        if (minutes in (at - 30) until (at + 30)) {
            return lerp(phasePalette(before), phasePalette(after), (minutes - (at - 30)) / 60f)
        }
    }
    return phasePalette(dayPhaseAt(time))
}

/**
 * Local sky adjustment (sea haze, Suez warmth) with strengths supplied as a
 * smooth function of world position — silhouettes themselves never dissolve.
 */
internal fun ScenePalette.adjusted(haze: Float, warmth: Float): ScenePalette {
    var p = this
    if (haze > 0f) {
        p = p.copy(
            skyMid = lerp(p.skyMid, lerp(p.skyBottom, Color.White, 0.3f), haze * 0.5f),
            skyBottom = lerp(p.skyBottom, Color.White, haze * 0.3f),
        )
    }
    if (warmth > 0f) {
        p = p.copy(
            skyTop = lerp(p.skyTop, Color(0xFF7A3B22), warmth * 0.2f),
            skyMid = lerp(p.skyMid, Color(0xFFB0622F), warmth * 0.25f),
            skyBottom = lerp(p.skyBottom, Color(0xFFD98A4B), warmth * 0.25f),
        )
    }
    return p
}

/** Deterministic celestial body position in canvas fractions for the local expedition time. */
internal fun celestialPosition(time: LocalTime, phase: DayPhase): Offset {
    val minutes = time.hour * 60 + time.minute
    return when (phase) {
        DayPhase.NIGHT -> {
            val t = ((minutes - 1320 + 1440) % 1440) / 420f
            Offset(mix(0.2f, 0.8f, t), 0.3f - 0.14f * sin(PI * t).toFloat())
        }
        DayPhase.DAWN -> {
            val t = (minutes - 300) / 180f
            Offset(mix(0.16f, 0.42f, t), mix(0.58f, 0.4f, t))
        }
        DayPhase.DAY -> {
            val t = (minutes - 480) / 540f
            Offset(mix(0.28f, 0.74f, t), 0.16f)
        }
        DayPhase.SUNSET -> {
            val t = (minutes - 1020) / 300f
            Offset(mix(0.68f, 0.88f, t), mix(0.32f, 0.56f, t))
        }
    }
}

/** Ground line shared by the land scenes; also the top of the horizon haze band. */
internal const val GROUND_FRACTION = 0.68f

internal fun Color.fade(a: Float): Color = copy(alpha = alpha * a)

internal fun frac(v: Double): Float = (v - floor(v)).toFloat()

internal fun DrawScope.sky(p: ScenePalette) {
    drawRect(
        Brush.verticalGradient(listOf(p.skyTop, p.skyMid, p.skyBottom), startY = 0f, endY = size.height * 0.7f),
    )
}

/** Soft atmospheric band at the horizon so far silhouettes melt into the sky. */
internal fun DrawScope.horizonHaze(p: ScenePalette) {
    val h = size.height
    val top = h * 0.48f
    val bottom = h * (GROUND_FRACTION + 0.01f)
    drawRect(
        Brush.verticalGradient(
            0f to p.skyBottom.copy(alpha = 0f),
            1f to p.skyBottom.copy(alpha = 0.5f),
            startY = top, endY = bottom,
        ),
        topLeft = Offset(0f, top),
        size = Size(size.width, bottom - top),
    )
}

internal fun DrawScope.stars(alpha: Float) {
    if (alpha <= 0f) return
    val w = size.width
    val h = size.height
    for (i in 0 until 56) {
        val x = frac(sin(i * 12.9898) * 43758.5453) * w
        val y = frac(sin(i * 78.233) * 12543.8531) * h * 0.62f
        val r = (if (i % 5 == 0) 1.7f else 1.1f) * (h / 400f).coerceIn(0.7f, 1.6f)
        val twinkle = 0.45f + 0.55f * frac(sin(i * 3.71) * 999.91)
        drawCircle(Color(0xFFEAF0FF), radius = r, center = Offset(x, y), alpha = alpha * twinkle)
    }
}

internal fun DrawScope.celestial(p: ScenePalette, pos: Offset, moon: Boolean) {
    val center = Offset(pos.x * size.width, pos.y * size.height)
    val r = size.height * (if (moon) 0.045f else 0.055f)
    drawCircle(p.celestial, radius = r * 1.9f, center = center, alpha = 0.16f)
    drawCircle(p.celestial, radius = r, center = center)
    if (moon) {
        // Craters sit fully inside the disc so the moon never looks clipped.
        val crater = lerp(p.celestial, p.skyTop, 0.4f)
        drawCircle(crater, radius = r * 0.2f, center = center + Offset(-r * 0.3f, -r * 0.18f), alpha = 0.35f)
        drawCircle(crater, radius = r * 0.13f, center = center + Offset(r * 0.26f, r * 0.22f), alpha = 0.3f)
        drawCircle(crater, radius = r * 0.09f, center = center + Offset(r * 0.08f, -r * 0.42f), alpha = 0.3f)
    }
}

/**
 * Solid silhouette of a XIX-century traveller facing right: hat brim, long
 * flared coat, walking stride, shoulder bag and a staff in the forward hand.
 */
internal fun DrawScope.hero(x: Float, groundY: Float, p: ScenePalette) {
    val s = size.height * 0.11f
    val c = lerp(p.near, Color.Black, 0.4f)
    // Staff planted ahead of the body.
    drawLine(c, Offset(x + s * 0.34f, groundY - s * 0.64f), Offset(x + s * 0.42f, groundY),
        strokeWidth = s * 0.05f, cap = StrokeCap.Round)
    // Legs in stride, under the coat hem.
    drawLine(c, Offset(x + s * 0.04f, groundY - s * 0.32f), Offset(x + s * 0.22f, groundY),
        strokeWidth = s * 0.09f, cap = StrokeCap.Round)
    drawLine(c, Offset(x - s * 0.05f, groundY - s * 0.32f), Offset(x - s * 0.16f, groundY - s * 0.01f),
        strokeWidth = s * 0.09f, cap = StrokeCap.Round)
    // Flared coat from shoulders to hem.
    val coat = Path().apply {
        moveTo(x - s * 0.10f, groundY - s * 0.80f)
        quadraticTo(x + s * 0.01f, groundY - s * 0.87f, x + s * 0.08f, groundY - s * 0.78f)
        lineTo(x + s * 0.15f, groundY - s * 0.32f)
        lineTo(x + s * 0.02f, groundY - s * 0.27f)
        lineTo(x - s * 0.17f, groundY - s * 0.31f)
        close()
    }
    drawPath(coat, c)
    // Arm reaching to the staff.
    drawLine(c, Offset(x + s * 0.05f, groundY - s * 0.68f), Offset(x + s * 0.32f, groundY - s * 0.58f),
        strokeWidth = s * 0.07f, cap = StrokeCap.Round)
    // Shoulder bag hanging behind the back.
    drawOval(c, topLeft = Offset(x - s * 0.24f, groundY - s * 0.70f), size = Size(s * 0.15f, s * 0.22f))
    // Head with a hat brim.
    drawCircle(c, radius = s * 0.085f, center = Offset(x + s * 0.03f, groundY - s * 0.92f))
    drawLine(c, Offset(x - s * 0.06f, groundY - s * 0.97f), Offset(x + s * 0.14f, groundY - s * 0.955f),
        strokeWidth = s * 0.045f, cap = StrokeCap.Round)
}

internal fun DrawScope.glow(x: Float, y: Float, r: Float, p: ScenePalette, a: Float) {
    if (p.lightAlpha <= 0f || a <= 0f) return
    drawCircle(p.light, r * 2.6f, Offset(x, y), alpha = 0.16f * p.lightAlpha * a)
    drawCircle(p.light, r, Offset(x, y), alpha = p.lightAlpha * a)
}

/** Soft overlapping steam plume: small translucent puffs, no bead chain. */
internal fun DrawScope.smoke(x: Float, y: Float, color: Color, a: Float) {
    val u = size.height
    val puffs = listOf(
        Quad(0f, 0f, 0.022f, 0.20f),
        Quad(0.018f, -0.028f, 0.03f, 0.16f),
        Quad(0.045f, -0.06f, 0.038f, 0.12f),
        Quad(0.08f, -0.1f, 0.048f, 0.09f),
        Quad(0.125f, -0.145f, 0.058f, 0.06f),
    )
    for ((ox, oy, r, alpha) in puffs) {
        drawCircle(color, u * r, Offset(x + u * ox, y + u * oy), alpha = alpha * a)
    }
}

internal data class Quad(val ox: Float, val oy: Float, val r: Float, val alpha: Float)

internal fun DrawScope.lamppost(x: Float, gy: Float, hgt: Float, color: Color, p: ScenePalette, a: Float) {
    drawLine(color, Offset(x, gy), Offset(x, gy - hgt), strokeWidth = size.height * 0.008f, cap = StrokeCap.Round)
    drawCircle(color, size.height * 0.014f, Offset(x, gy - hgt))
    glow(x, gy - hgt, size.height * 0.012f, p, a)
}

internal fun DrawScope.tree(x: Float, gy: Float, hgt: Float, color: Color) {
    drawRect(color, topLeft = Offset(x - hgt * 0.03f, gy - hgt * 0.45f), size = Size(hgt * 0.06f, hgt * 0.45f))
    drawCircle(color, hgt * 0.28f, Offset(x, gy - hgt * 0.6f))
    drawCircle(color, hgt * 0.2f, Offset(x - hgt * 0.18f, gy - hgt * 0.48f))
    drawCircle(color, hgt * 0.18f, Offset(x + hgt * 0.18f, gy - hgt * 0.5f))
}

internal fun DrawScope.fir(x: Float, gy: Float, hgt: Float, color: Color) {
    val wd = hgt * 0.42f
    val path = Path().apply {
        moveTo(x, gy - hgt)
        lineTo(x - wd * 0.32f, gy - hgt * 0.55f)
        lineTo(x - wd * 0.2f, gy - hgt * 0.55f)
        lineTo(x - wd * 0.5f, gy - hgt * 0.18f)
        lineTo(x + wd * 0.5f, gy - hgt * 0.18f)
        lineTo(x + wd * 0.2f, gy - hgt * 0.55f)
        lineTo(x + wd * 0.32f, gy - hgt * 0.55f)
        close()
    }
    drawPath(path, color)
    drawRect(color, topLeft = Offset(x - hgt * 0.03f, gy - hgt * 0.18f), size = Size(hgt * 0.06f, hgt * 0.18f))
}

internal fun DrawScope.cypress(x: Float, gy: Float, hgt: Float, color: Color) {
    val path = Path().apply {
        moveTo(x, gy - hgt)
        quadraticTo(x - hgt * 0.16f, gy - hgt * 0.5f, x - hgt * 0.1f, gy)
        lineTo(x + hgt * 0.1f, gy)
        quadraticTo(x + hgt * 0.16f, gy - hgt * 0.5f, x, gy - hgt)
        close()
    }
    drawPath(path, color)
}

internal fun DrawScope.palm(x: Float, gy: Float, hgt: Float, color: Color) {
    val top = Offset(x + hgt * 0.12f, gy - hgt)
    val trunk = Path().apply {
        moveTo(x, gy)
        quadraticTo(x - hgt * 0.05f, gy - hgt * 0.55f, top.x, top.y)
    }
    drawPath(trunk, color, style = Stroke(width = size.height * 0.012f, cap = StrokeCap.Round))
    for (i in 0 until 5) {
        drawArc(color, startAngle = -170f + i * 34f, sweepAngle = 46f, useCenter = false,
            topLeft = Offset(top.x - hgt * 0.3f, top.y - hgt * 0.1f), size = Size(hgt * 0.6f, hgt * 0.5f),
            style = Stroke(width = size.height * 0.009f, cap = StrokeCap.Round))
    }
}

/** Steam locomotive facing right: boiler, cab, chimney, dome, small wheels. */
internal fun DrawScope.train(x: Float, gy: Float, scale: Float, color: Color, p: ScenePalette, a: Float) {
    val h = size.height * scale
    // Frame linking the wheels.
    drawRect(color, topLeft = Offset(x - h * 0.35f, gy - h * 0.22f), size = Size(h * 1.3f, h * 0.13f))
    // Boiler.
    drawRect(color, topLeft = Offset(x, gy - h * 0.58f), size = Size(h * 0.95f, h * 0.36f))
    drawCircle(color, h * 0.07f, Offset(x + h * 0.42f, gy - h * 0.58f))
    // Cab at the rear.
    drawRect(color, topLeft = Offset(x - h * 0.38f, gy - h * 0.85f), size = Size(h * 0.42f, h * 0.63f))
    // Chimney at the front.
    drawRect(color, topLeft = Offset(x + h * 0.76f, gy - h * 0.82f), size = Size(h * 0.12f, h * 0.26f))
    val wheel = lerp(color, Color.Black, 0.4f)
    for (i in 0..2) drawCircle(wheel, h * 0.11f, Offset(x + h * (0.02f + i * 0.36f), gy - h * 0.11f))
    if (p.lightAlpha > 0f && a > 0f) {
        // Warm cab window at dusk and night.
        drawRect(p.light, topLeft = Offset(x - h * 0.28f, gy - h * 0.74f),
            size = Size(h * 0.14f, h * 0.15f), alpha = 0.8f * p.lightAlpha * a)
    }
    smoke(x + h * 0.82f, gy - h * 0.92f, lerp(p.skyBottom, Color.White, 0.35f), a)
}

internal fun DrawScope.mast(x: Float, gy: Float, hgt: Float, color: Color) {
    val sw = size.height * 0.007f
    drawLine(color, Offset(x, gy), Offset(x, gy - hgt), strokeWidth = sw)
    drawLine(color, Offset(x - hgt * 0.22f, gy - hgt * 0.72f), Offset(x + hgt * 0.22f, gy - hgt * 0.72f),
        strokeWidth = sw * 0.8f)
    drawLine(color, Offset(x, gy - hgt), Offset(x - hgt * 0.25f, gy), strokeWidth = sw * 0.5f)
    drawLine(color, Offset(x, gy - hgt), Offset(x + hgt * 0.25f, gy), strokeWidth = sw * 0.5f)
}

internal fun DrawScope.steamer(x0: Float, x1: Float, gy: Float, color: Color, p: ScenePalette, a: Float) {
    val w = size.width
    val h = size.height
    val hullH = h * 0.07f
    val hull = Path().apply {
        moveTo(x0, gy - hullH)
        lineTo(x1, gy - hullH)
        lineTo(x1 - (x1 - x0) * 0.06f, gy)
        lineTo(x0 + (x1 - x0) * 0.1f, gy)
        quadraticTo(x0 + (x1 - x0) * 0.02f, gy - hullH * 0.3f, x0, gy - hullH)
        close()
    }
    drawPath(hull, color)
    drawRect(color, topLeft = Offset(x0 + (x1 - x0) * 0.25f, gy - hullH - h * 0.05f),
        size = Size((x1 - x0) * 0.5f, h * 0.05f))
    val funnelX = x0 + (x1 - x0) * 0.42f
    drawRect(color, topLeft = Offset(funnelX, gy - hullH - h * 0.11f), size = Size(w * 0.03f, h * 0.07f))
    smoke(funnelX + w * 0.015f, gy - hullH - h * 0.13f, lerp(p.skyBottom, Color.White, 0.3f), a)
    mast(x0 + (x1 - x0) * 0.14f, gy - hullH, h * 0.13f, color)
    mast(x0 + (x1 - x0) * 0.86f, gy - hullH, h * 0.13f, color)
}

internal fun DrawScope.lighthouse(x: Float, gy: Float, hgt: Float, color: Color, p: ScenePalette, a: Float) {
    val path = Path().apply {
        moveTo(x - hgt * 0.12f, gy)
        lineTo(x - hgt * 0.07f, gy - hgt)
        lineTo(x + hgt * 0.07f, gy - hgt)
        lineTo(x + hgt * 0.12f, gy)
        close()
    }
    drawPath(path, color)
    drawRect(color, topLeft = Offset(x - hgt * 0.09f, gy - hgt - hgt * 0.1f), size = Size(hgt * 0.18f, hgt * 0.1f))
    val roof = Path().apply {
        moveTo(x - hgt * 0.1f, gy - hgt - hgt * 0.1f)
        lineTo(x, gy - hgt - hgt * 0.22f)
        lineTo(x + hgt * 0.1f, gy - hgt - hgt * 0.1f)
        close()
    }
    drawPath(roof, color)
    if (p.lightAlpha > 0f && a > 0f) {
        // Soft fan beam: fades with distance, no hard wedge edges in the sky.
        val beam = Path().apply {
            moveTo(x, gy - hgt - hgt * 0.05f)
            lineTo(x - size.width * 0.38f, gy - hgt - hgt * 0.14f)
            lineTo(x - size.width * 0.38f, gy - hgt + hgt * 0.04f)
            close()
        }
        drawPath(
            beam,
            Brush.horizontalGradient(
                0f to p.light.copy(alpha = 0.22f * p.lightAlpha * a),
                1f to p.light.copy(alpha = 0f),
                startX = x, endX = x - size.width * 0.38f,
            ),
        )
    }
    glow(x, gy - hgt - hgt * 0.05f, hgt * 0.05f, p, a)
}

internal fun DrawScope.clockTower(x: Float, gy: Float, hgt: Float, color: Color, p: ScenePalette, a: Float) {
    val wd = hgt * 0.16f
    drawRect(color, topLeft = Offset(x - wd / 2, gy - hgt), size = Size(wd, hgt))
    val roof = Path().apply {
        moveTo(x - wd * 0.7f, gy - hgt)
        lineTo(x, gy - hgt - hgt * 0.18f)
        lineTo(x + wd * 0.7f, gy - hgt)
        close()
    }
    drawPath(roof, color)
    val clockColor = if (p.lightAlpha > 0f) p.light else lerp(color, Color.White, 0.35f)
    val clockAlpha = if (p.lightAlpha > 0f) p.lightAlpha * a else a
    drawCircle(clockColor, wd * 0.28f, Offset(x, gy - hgt * 0.82f), alpha = clockAlpha)
}

internal fun DrawScope.crane(x: Float, gy: Float, hgt: Float, color: Color) {
    val sw = size.height * 0.007f
    drawLine(color, Offset(x, gy), Offset(x, gy - hgt), strokeWidth = sw)
    drawLine(color, Offset(x - hgt * 0.35f, gy - hgt), Offset(x + hgt * 0.5f, gy - hgt), strokeWidth = sw)
    drawLine(color, Offset(x, gy - hgt * 0.7f), Offset(x + hgt * 0.5f, gy - hgt), strokeWidth = sw)
    drawLine(color, Offset(x + hgt * 0.4f, gy - hgt), Offset(x + hgt * 0.4f, gy - hgt * 0.8f), strokeWidth = sw)
}

internal fun DrawScope.minaret(x: Float, gy: Float, hgt: Float, color: Color) {
    val wd = hgt * 0.07f
    drawRect(color, topLeft = Offset(x - wd / 2, gy - hgt), size = Size(wd, hgt))
    drawRect(color, topLeft = Offset(x - wd * 0.9f, gy - hgt * 0.72f), size = Size(wd * 1.8f, hgt * 0.04f))
    val top = Path().apply {
        moveTo(x - wd * 0.8f, gy - hgt)
        quadraticTo(x, gy - hgt - hgt * 0.2f, x + wd * 0.8f, gy - hgt)
        close()
    }
    drawPath(top, color)
}

internal fun DrawScope.dome(x: Float, gy: Float, wd: Float, color: Color) {
    drawRect(color, topLeft = Offset(x - wd / 2, gy - wd * 0.3f), size = Size(wd, wd * 0.3f))
    drawArc(color, startAngle = 180f, sweepAngle = 180f, useCenter = true,
        topLeft = Offset(x - wd / 2, gy - wd * 0.95f), size = Size(wd, wd * 0.7f))
    drawLine(color, Offset(x, gy - wd * 0.95f), Offset(x, gy - wd * 1.15f), strokeWidth = size.height * 0.006f)
}

/** Railway embankment: a raised trapezoid body with a track line and ties on top. */
internal fun DrawScope.embankment(x0: Float, x1: Float, gy: Float, hgt: Float, body: Color, track: Color) {
    val slope = hgt * 0.9f
    val path = Path().apply {
        moveTo(x0, gy)
        lineTo(x0 + slope, gy - hgt)
        lineTo(x1 - slope, gy - hgt)
        lineTo(x1, gy)
        close()
    }
    drawPath(path, body)
    val sw = size.height * 0.006f
    drawLine(track, Offset(x0 + slope * 0.7f, gy - hgt - hgt * 0.1f),
        Offset(x1 - slope * 0.7f, gy - hgt - hgt * 0.1f), strokeWidth = sw)
    var x = x0 + slope
    while (x < x1 - slope * 0.7f) {
        drawLine(track, Offset(x, gy - hgt), Offset(x, gy - hgt - hgt * 0.18f), strokeWidth = sw * 0.8f)
        x += size.width * 0.035f
    }
}

internal fun DrawScope.arcade(x0: Float, x1: Float, gy: Float, hgt: Float, color: Color) {
    val n = 6
    val wd = (x1 - x0) / n
    val path = Path()
    path.addRect(Rect(x0, gy - hgt, x1, gy))
    for (i in 0 until n) {
        val ax = x0 + wd * i + wd * 0.22f
        val aw = wd * 0.56f
        path.addPath(Path().apply {
            moveTo(ax, gy)
            lineTo(ax, gy - hgt * 0.45f)
            quadraticTo(ax + aw / 2, gy - hgt * 0.95f, ax + aw, gy - hgt * 0.45f)
            lineTo(ax + aw, gy)
            close()
        })
    }
    path.fillType = PathFillType.EvenOdd
    drawPath(path, color)
}

internal fun DrawScope.bigArch(cx: Float, gy: Float, wd: Float, hgt: Float, color: Color) {
    val path = Path()
    path.addPath(Path().apply {
        moveTo(cx - wd / 2, gy)
        lineTo(cx - wd / 2, gy - hgt * 0.5f)
        quadraticTo(cx, gy - hgt * 1.25f, cx + wd / 2, gy - hgt * 0.5f)
        lineTo(cx + wd / 2, gy)
        close()
    })
    val iw = wd * 0.68f
    path.addPath(Path().apply {
        moveTo(cx - iw / 2, gy)
        lineTo(cx - iw / 2, gy - hgt * 0.42f)
        quadraticTo(cx, gy - hgt * 0.98f, cx + iw / 2, gy - hgt * 0.42f)
        lineTo(cx + iw / 2, gy)
        close()
    })
    path.fillType = PathFillType.EvenOdd
    drawPath(path, color)
}

internal fun DrawScope.tunnel(cx: Float, gy: Float, wd: Float, hgt: Float, color: Color) {
    val path = Path().apply {
        moveTo(cx - wd / 2, gy)
        lineTo(cx - wd / 2, gy - hgt * 0.4f)
        quadraticTo(cx, gy - hgt * 1.05f, cx + wd / 2, gy - hgt * 0.4f)
        lineTo(cx + wd / 2, gy)
        close()
    }
    drawPath(path, color)
}

internal fun DrawScope.bales(x: Float, gy: Float, color: Color) {
    val u = size.height
    val r = CornerRadius(u * 0.01f)
    drawRoundRect(color, topLeft = Offset(x, gy - u * 0.05f), size = Size(u * 0.09f, u * 0.05f), cornerRadius = r)
    drawRoundRect(color, topLeft = Offset(x + u * 0.05f, gy - u * 0.1f), size = Size(u * 0.09f, u * 0.05f), cornerRadius = r)
    drawRoundRect(color, topLeft = Offset(x + u * 0.1f, gy - u * 0.05f), size = Size(u * 0.09f, u * 0.05f), cornerRadius = r)
}

internal fun DrawScope.spire(x: Float, gy: Float, hgt: Float, color: Color) {
    val wd = size.height * 0.025f
    drawRect(color, topLeft = Offset(x - wd * 1.4f, gy - hgt * 0.3f), size = Size(wd * 2.8f, hgt * 0.3f))
    val path = Path().apply {
        moveTo(x - wd / 2, gy - hgt * 0.3f)
        lineTo(x, gy - hgt)
        lineTo(x + wd / 2, gy - hgt * 0.3f)
        close()
    }
    drawPath(path, color)
}
