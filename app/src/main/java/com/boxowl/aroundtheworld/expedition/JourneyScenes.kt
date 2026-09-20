package com.boxowl.aroundtheworld.expedition

import android.provider.Settings
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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

internal data class SceneBlend(val fromIndex: Int, val toIndex: Int?, val fraction: Float)

/** Decomposes an animated route position (scene index + in-segment fraction) into a scene pair. */
internal fun sceneBlendAt(position: Float, sceneCount: Int): SceneBlend {
    val maxIndex = sceneCount - 1
    val clamped = position.coerceIn(0f, maxIndex.toFloat())
    val from = clamped.toInt().coerceAtMost(maxIndex)
    val fraction = (clamped - from).coerceIn(0f, 1f)
    val to = (from + 1).takeIf { it <= maxIndex && fraction > 0f }
    return SceneBlend(from, to, if (to == null) 0f else fraction)
}

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

private fun mix(a: Float, b: Float, t: Float): Float = a + (b - a) * t

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

internal fun ScenePalette.adjustedFor(scene: SceneKind): ScenePalette {
    var p = this
    if (scene.haze > 0f) {
        p = p.copy(
            skyMid = lerp(p.skyMid, lerp(p.skyBottom, Color.White, 0.3f), scene.haze * 0.5f),
            skyBottom = lerp(p.skyBottom, Color.White, scene.haze * 0.3f),
        )
    }
    if (scene.warmth > 0f) {
        p = p.copy(
            skyTop = lerp(p.skyTop, Color(0xFF7A3B22), scene.warmth * 0.2f),
            skyMid = lerp(p.skyMid, Color(0xFFB0622F), scene.warmth * 0.25f),
            skyBottom = lerp(p.skyBottom, Color(0xFFD98A4B), scene.warmth * 0.25f),
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

/** Hero position inside a scene: in-segment fraction everywhere, route end only at Suez. */
internal fun heroFractionAt(position: Float, sceneCount: Int): Float {
    val maxIndex = (sceneCount - 1).toFloat()
    if (position >= maxIndex) return 1f
    val clamped = position.coerceAtLeast(0f)
    return clamped - clamped.toInt()
}

/** Decorative procedural scene. All numbers and labels stay in text elements outside the canvas. */
@Composable
internal fun JourneySceneCanvas(
    projection: FirstLegMapProjection,
    time: LocalTime,
    modifier: Modifier = Modifier,
) {
    val resolver = LocalContext.current.contentResolver
    val reducedMotion = remember {
        Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
    val target = if (projection.nextIndex == null) projection.currentIndex.toFloat()
        else projection.currentIndex.toFloat() + projection.fractionToNext
    val position by animateFloatAsState(
        targetValue = target,
        animationSpec = if (reducedMotion) snap() else tween(durationMillis = 750, easing = FastOutSlowInEasing),
        label = "journeyScenePosition",
    )
    val phase = dayPhaseAt(time)
    val palette = paletteFor(time)
    val blend = sceneBlendAt(position, JOURNEY_SCENES.size)
    val from = JOURNEY_SCENES[blend.fromIndex]
    val to = blend.toIndex?.let(JOURNEY_SCENES::get)
    val targetBlend = sceneBlendAt(target, JOURNEY_SCENES.size)
    val targetFrom = JOURNEY_SCENES[targetBlend.fromIndex]
    val targetTo = targetBlend.toIndex?.let(JOURNEY_SCENES::get)
    val description = if (targetTo != null) {
        "Сцена пути: переход ${targetFrom.label} → ${targetTo.label}"
    } else {
        "Сцена пути: ${targetFrom.label}: ${targetFrom.details}"
    }
    Canvas(modifier.semantics { contentDescription = description }) {
        val pal = if (to == null) palette.adjustedFor(from)
            else lerp(palette.adjustedFor(from), palette.adjustedFor(to), blend.fraction)
        sky(pal)
        stars(pal.starAlpha)
        celestial(pal, celestialPosition(time, phase), moon = phase == DayPhase.NIGHT)
        val w = size.width
        val f = blend.fraction
        for (layer in SceneLayer.entries) {
            // Wide parallax separates the outgoing and incoming silhouettes
            // quickly, so a blend never reads as two cities overlaid.
            val parallax = when (layer) {
                SceneLayer.FAR -> 0.14f
                SceneLayer.MID -> 0.32f
                SceneLayer.NEAR -> 0.55f
            } * w
            if (to == null) {
                drawSceneLayer(from, layer, pal, 0f, 1f)
            } else {
                drawSceneLayer(from, layer, pal, -f * parallax, 1f - f)
                drawSceneLayer(to, layer, pal, (1f - f) * parallax, f)
            }
            if (layer == SceneLayer.FAR) horizonHaze(pal)
        }
        val heroY = if (to == null) heroGroundY(from, size.height)
            else mix(heroGroundY(from, size.height), heroGroundY(to, size.height), f)
        hero(mix(0.24f, 0.76f, heroFractionAt(position, JOURNEY_SCENES.size)) * w, heroY, pal)
    }
}

private enum class SceneLayer { FAR, MID, NEAR }

/** Ground line of the land scenes; the Mediterranean hero stands on the steamer deck. */
private const val GROUND_FRACTION = 0.68f

private fun heroGroundY(scene: SceneKind, h: Float): Float = when (scene) {
    SceneKind.MEDITERRANEAN -> h * 0.83f
    SceneKind.SUEZ -> h * 0.78f
    else -> h * GROUND_FRACTION
}

private fun Color.fade(a: Float): Color = copy(alpha = alpha * a)

private fun frac(v: Double): Float = (v - floor(v)).toFloat()

private fun DrawScope.sky(p: ScenePalette) {
    drawRect(
        Brush.verticalGradient(listOf(p.skyTop, p.skyMid, p.skyBottom), startY = 0f, endY = size.height * 0.7f),
    )
}

/** Soft atmospheric band at the horizon so far silhouettes melt into the sky. */
private fun DrawScope.horizonHaze(p: ScenePalette) {
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

private fun DrawScope.stars(alpha: Float) {
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

private fun DrawScope.celestial(p: ScenePalette, pos: Offset, moon: Boolean) {
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
private fun DrawScope.hero(x: Float, groundY: Float, p: ScenePalette) {
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

private fun DrawScope.ground(color: Color, gy: Float, dx: Float) {
    drawRect(color, topLeft = Offset(dx - size.width * 0.4f, gy),
        size = Size(size.width * 1.8f, size.height - gy))
}

private fun DrawScope.glow(x: Float, y: Float, r: Float, p: ScenePalette, a: Float) {
    if (p.lightAlpha <= 0f || a <= 0f) return
    drawCircle(p.light, r * 2.6f, Offset(x, y), alpha = 0.16f * p.lightAlpha * a)
    drawCircle(p.light, r, Offset(x, y), alpha = p.lightAlpha * a)
}

/** Soft overlapping steam plume: small translucent puffs, no bead chain. */
private fun DrawScope.smoke(x: Float, y: Float, color: Color, a: Float) {
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

private data class Quad(val ox: Float, val oy: Float, val r: Float, val alpha: Float)

private fun DrawScope.lamppost(x: Float, gy: Float, hgt: Float, color: Color, p: ScenePalette, a: Float) {
    drawLine(color, Offset(x, gy), Offset(x, gy - hgt), strokeWidth = size.height * 0.008f, cap = StrokeCap.Round)
    drawCircle(color, size.height * 0.014f, Offset(x, gy - hgt))
    glow(x, gy - hgt, size.height * 0.012f, p, a)
}

/**
 * Roofline strip with silhouette variety: gable, mansard and flat-parapet
 * houses of different heights and widths, 0–2 chimneys, an occasional spire.
 */
private fun DrawScope.roofs(gy: Float, baseH: Float, dx: Float, color: Color, seed: Int, chimneys: Boolean) {
    val unit = size.width / 7f
    var i = -2
    while (i < 10) {
        val x = i * unit + dx
        val hh = baseH * (0.7f + 0.5f * frac(sin((i + seed) * 7.13) * 91.7))
        val wd = unit * (0.75f + 0.3f * frac(sin((i + seed) * 3.31) * 57.3))
        drawRect(color, topLeft = Offset(x, gy - hh), size = Size(wd, hh))
        when ((frac(sin((i + seed) * 9.17) * 31.7) * 3f).toInt()) {
            0 -> {
                val roof = Path().apply {
                    moveTo(x - wd * 0.06f, gy - hh)
                    lineTo(x + wd * 0.5f, gy - hh - wd * 0.28f)
                    lineTo(x + wd * 1.06f, gy - hh)
                    close()
                }
                drawPath(roof, color)
            }
            1 -> {
                val mansard = Path().apply {
                    moveTo(x - wd * 0.02f, gy - hh)
                    lineTo(x + wd * 0.22f, gy - hh - wd * 0.18f)
                    lineTo(x + wd * 0.78f, gy - hh - wd * 0.18f)
                    lineTo(x + wd * 1.02f, gy - hh)
                    close()
                }
                drawPath(mansard, color)
            }
            else -> drawRect(color, topLeft = Offset(x - wd * 0.02f, gy - hh - wd * 0.05f),
                size = Size(wd * 1.04f, wd * 0.06f))
        }
        if (chimneys) {
            val count = (frac(sin((i + seed) * 6.47) * 47.9) * 3f).toInt()
            for (ch in 0 until count) {
                val cx = x + wd * (0.2f + 0.5f * frac(sin((i + seed + ch) * 4.53) * 63.1))
                drawRect(color, topLeft = Offset(cx, gy - hh - wd * 0.28f - hh * 0.1f),
                    size = Size(wd * 0.09f, hh * 0.14f + wd * 0.2f))
            }
            if (frac(sin((i + seed) * 11.31) * 83.7) > 0.86f) {
                drawRect(color, topLeft = Offset(x + wd * 0.46f, gy - hh - hh * 0.5f),
                    size = Size(wd * 0.06f, hh * 0.5f))
                val tip = Path().apply {
                    moveTo(x + wd * 0.42f, gy - hh - hh * 0.5f)
                    lineTo(x + wd * 0.49f, gy - hh - hh * 0.66f)
                    lineTo(x + wd * 0.56f, gy - hh - hh * 0.5f)
                    close()
                }
                drawPath(tip, color)
            }
        }
        i++
    }
}

private fun DrawScope.humps(gy: Float, amp: Float, dx: Float, color: Color, seed: Double) {
    val w = size.width
    val path = Path().apply {
        moveTo(-w * 0.3f + dx, gy + 2f)
        var x = -w * 0.3f + dx
        var k = 0
        while (x < w * 1.3f + dx) {
            val segW = w * (0.3f + 0.2f * frac(sin(seed + k * 5.7) * 43.1))
            val peak = gy - amp * (0.5f + 0.5f * frac(sin(seed + k * 9.3) * 71.7))
            quadraticTo(x + segW * 0.5f, peak - amp * 0.4f, x + segW, gy)
            x += segW
            k++
        }
        lineTo(w * 1.3f + dx, size.height)
        lineTo(-w * 0.3f + dx, size.height)
        close()
    }
    drawPath(path, color)
}

/** Water band whose top edge dissolves into the sky — no hard rectangle edge. */
private fun DrawScope.sea(gy: Float, topY: Float, color: Color, dx: Float) {
    drawRect(
        Brush.verticalGradient(
            0f to color.copy(alpha = 0f),
            0.45f to color,
            startY = topY, endY = gy,
        ),
        topLeft = Offset(dx - size.width * 0.3f, topY),
        size = Size(size.width * 1.6f, gy - topY),
    )
}

private fun DrawScope.tree(x: Float, gy: Float, hgt: Float, color: Color) {
    drawRect(color, topLeft = Offset(x - hgt * 0.03f, gy - hgt * 0.45f), size = Size(hgt * 0.06f, hgt * 0.45f))
    drawCircle(color, hgt * 0.28f, Offset(x, gy - hgt * 0.6f))
    drawCircle(color, hgt * 0.2f, Offset(x - hgt * 0.18f, gy - hgt * 0.48f))
    drawCircle(color, hgt * 0.18f, Offset(x + hgt * 0.18f, gy - hgt * 0.5f))
}

private fun DrawScope.fir(x: Float, gy: Float, hgt: Float, color: Color) {
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

private fun DrawScope.cypress(x: Float, gy: Float, hgt: Float, color: Color) {
    val path = Path().apply {
        moveTo(x, gy - hgt)
        quadraticTo(x - hgt * 0.16f, gy - hgt * 0.5f, x - hgt * 0.1f, gy)
        lineTo(x + hgt * 0.1f, gy)
        quadraticTo(x + hgt * 0.16f, gy - hgt * 0.5f, x, gy - hgt)
        close()
    }
    drawPath(path, color)
}

private fun DrawScope.palm(x: Float, gy: Float, hgt: Float, color: Color) {
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
private fun DrawScope.train(x: Float, gy: Float, scale: Float, color: Color, p: ScenePalette, a: Float) {
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

/** Sparse warm windows on a roofline; lit only at dusk and night. */
private fun DrawScope.windows(
    x0: Float, x1: Float, gy: Float, bandTop: Float, p: ScenePalette, a: Float, seed: Int,
) {
    if (p.lightAlpha <= 0f || a <= 0f) return
    val h = size.height
    val count = ((x1 - x0) / (size.width * 0.03f)).toInt().coerceAtLeast(0)
    for (i in 0 until count) {
        if (frac(sin((i + seed) * 5.77) * 211.3) < 0.45f) continue
        val wx = x0 + (x1 - x0) * frac(sin((i + seed) * 3.13) * 91.3)
        val wy = bandTop + (gy - bandTop) * frac(sin((i + seed) * 8.41) * 57.1)
        drawRect(p.light, topLeft = Offset(wx, wy), size = Size(h * 0.008f, h * 0.012f),
            alpha = 0.5f * p.lightAlpha * a)
    }
}

private fun DrawScope.mast(x: Float, gy: Float, hgt: Float, color: Color) {
    val sw = size.height * 0.007f
    drawLine(color, Offset(x, gy), Offset(x, gy - hgt), strokeWidth = sw)
    drawLine(color, Offset(x - hgt * 0.22f, gy - hgt * 0.72f), Offset(x + hgt * 0.22f, gy - hgt * 0.72f),
        strokeWidth = sw * 0.8f)
    drawLine(color, Offset(x, gy - hgt), Offset(x - hgt * 0.25f, gy), strokeWidth = sw * 0.5f)
    drawLine(color, Offset(x, gy - hgt), Offset(x + hgt * 0.25f, gy), strokeWidth = sw * 0.5f)
}

private fun DrawScope.steamer(x0: Float, x1: Float, gy: Float, color: Color, p: ScenePalette, a: Float) {
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

private fun DrawScope.lighthouse(x: Float, gy: Float, hgt: Float, color: Color, p: ScenePalette, a: Float) {
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

private fun DrawScope.clockTower(x: Float, gy: Float, hgt: Float, color: Color, p: ScenePalette, a: Float) {
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

/** A row of warm port lights along a pier, lit at dusk and night. */
private fun DrawScope.pierLights(x0: Float, x1: Float, y: Float, p: ScenePalette, a: Float) {
    if (p.lightAlpha <= 0f || a <= 0f) return
    var x = x0
    while (x <= x1) {
        glow(x, y, size.height * 0.007f, p, a)
        x += (x1 - x0) / 7f
    }
}

/** Two or three distant gulls, only by day and dusk. */
private fun DrawScope.birds(p: ScenePalette, a: Float) {
    if (p.starAlpha > 0.4f || a <= 0f) return
    val w = size.width
    val h = size.height
    val col = lerp(p.far, p.skyTop, 0.3f)
    for (i in 0..2) {
        val bx = w * (0.18f + 0.28f * frac(sin(i * 7.7) * 31.1))
        val by = h * (0.16f + 0.12f * frac(sin(i * 3.1) * 57.7))
        val wing = Stroke(width = h * 0.004f, cap = StrokeCap.Round)
        drawArc(col, 205f, 55f, useCenter = false, topLeft = Offset(bx - h * 0.024f, by),
            size = Size(h * 0.024f, h * 0.02f), alpha = 0.55f * a, style = wing)
        drawArc(col, 280f, 55f, useCenter = false, topLeft = Offset(bx, by),
            size = Size(h * 0.024f, h * 0.02f), alpha = 0.55f * a, style = wing)
    }
}

/** A low fence receding along the road edge. */
private fun DrawScope.fence(x0: Float, y0: Float, x1: Float, y1: Float, color: Color) {
    val sw = size.height * 0.005f
    drawLine(color, Offset(x0, y0 - size.height * 0.025f), Offset(x1, y1 - size.height * 0.045f),
        strokeWidth = sw)
    for (i in 0..5) {
        val t = i / 5f
        val px = mix(x0, x1, t)
        val py = mix(y0, y1, t)
        val ph = mix(size.height * 0.035f, size.height * 0.06f, t)
        drawLine(color, Offset(px, py), Offset(px, py - ph), strokeWidth = sw)
    }
}

private fun DrawScope.crane(x: Float, gy: Float, hgt: Float, color: Color) {
    val sw = size.height * 0.007f
    drawLine(color, Offset(x, gy), Offset(x, gy - hgt), strokeWidth = sw)
    drawLine(color, Offset(x - hgt * 0.35f, gy - hgt), Offset(x + hgt * 0.5f, gy - hgt), strokeWidth = sw)
    drawLine(color, Offset(x, gy - hgt * 0.7f), Offset(x + hgt * 0.5f, gy - hgt), strokeWidth = sw)
    drawLine(color, Offset(x + hgt * 0.4f, gy - hgt), Offset(x + hgt * 0.4f, gy - hgt * 0.8f), strokeWidth = sw)
}

private fun DrawScope.minaret(x: Float, gy: Float, hgt: Float, color: Color) {
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

private fun DrawScope.dome(x: Float, gy: Float, wd: Float, color: Color) {
    drawRect(color, topLeft = Offset(x - wd / 2, gy - wd * 0.3f), size = Size(wd, wd * 0.3f))
    drawArc(color, startAngle = 180f, sweepAngle = 180f, useCenter = true,
        topLeft = Offset(x - wd / 2, gy - wd * 0.95f), size = Size(wd, wd * 0.7f))
    drawLine(color, Offset(x, gy - wd * 0.95f), Offset(x, gy - wd * 1.15f), strokeWidth = size.height * 0.006f)
}

private fun DrawScope.telegraph(gy: Float, dx: Float, color: Color) {
    val xs = listOf(0.16f, 0.42f, 0.68f, 0.94f)
    val heights = listOf(0.3f, 0.26f, 0.22f, 0.19f)
    val sw = size.height * 0.007f
    val tops = mutableListOf<Offset>()
    xs.forEachIndexed { i, fx ->
        val x = fx * size.width + dx
        val hgt = heights[i] * size.height
        drawLine(color, Offset(x, gy), Offset(x, gy - hgt), strokeWidth = sw)
        drawLine(color, Offset(x - hgt * 0.12f, gy - hgt * 0.85f), Offset(x + hgt * 0.12f, gy - hgt * 0.85f),
            strokeWidth = sw)
        tops += Offset(x, gy - hgt * 0.85f)
    }
    for (i in 0 until tops.size - 1) {
        val a0 = tops[i]
        val b0 = tops[i + 1]
        val wire = Path().apply {
            moveTo(a0.x, a0.y)
            quadraticTo((a0.x + b0.x) / 2, maxOf(a0.y, b0.y) + size.height * 0.03f, b0.x, b0.y)
        }
        drawPath(wire, color, style = Stroke(width = sw * 0.6f))
    }
}

private fun DrawScope.railing(x0: Float, x1: Float, gy: Float, hgt: Float, color: Color) {
    val sw = size.height * 0.006f
    drawLine(color, Offset(x0, gy - hgt), Offset(x1, gy - hgt), strokeWidth = sw)
    drawLine(color, Offset(x0, gy - hgt * 0.5f), Offset(x1, gy - hgt * 0.5f), strokeWidth = sw)
    var x = x0
    while (x <= x1) {
        drawLine(color, Offset(x, gy), Offset(x, gy - hgt), strokeWidth = sw)
        x += (x1 - x0) / 12f
    }
}

/** Railway embankment: a raised trapezoid body with a track line and ties on top. */
private fun DrawScope.embankment(x0: Float, x1: Float, gy: Float, hgt: Float, body: Color, track: Color) {
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

private fun DrawScope.arcade(x0: Float, x1: Float, gy: Float, hgt: Float, color: Color) {
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

private fun DrawScope.bigArch(cx: Float, gy: Float, wd: Float, hgt: Float, color: Color) {
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

private fun DrawScope.tunnel(cx: Float, gy: Float, wd: Float, hgt: Float, color: Color) {
    val path = Path().apply {
        moveTo(cx - wd / 2, gy)
        lineTo(cx - wd / 2, gy - hgt * 0.4f)
        quadraticTo(cx, gy - hgt * 1.05f, cx + wd / 2, gy - hgt * 0.4f)
        lineTo(cx + wd / 2, gy)
        close()
    }
    drawPath(path, color)
}

/** Jagged mountain ridge across the full width with snow caps on the peaks. */
private fun DrawScope.peaks(gy: Float, dx: Float, color: Color, snow: Color) {
    val w = size.width
    val h = size.height
    val amp = h * 0.34f
    val seed = 3.7
    val tops = mutableListOf<Pair<Offset, Float>>()
    val path = Path()
    var x = -w * 0.3f + dx
    var k = 0
    path.moveTo(x, gy)
    while (x < w * 1.3f + dx) {
        val peakH = amp * (0.55f + 0.45f * frac(sin(seed + k * 7.9) * 51.3))
        val peakX = x + w * (0.09f + 0.11f * frac(sin(seed + k * 3.3) * 77.1))
        path.lineTo(peakX, gy - peakH)
        tops += Offset(peakX, gy - peakH) to peakH
        val valleyY = gy - amp * 0.3f * (0.4f + 0.6f * frac(sin(seed + k * 5.1) * 23.7))
        val valleyX = peakX + w * (0.07f + 0.09f * frac(sin(seed + k * 9.7) * 41.3))
        path.lineTo(valleyX, valleyY)
        x = valleyX
        k++
    }
    path.lineTo(w * 1.3f + dx, gy)
    path.lineTo(w * 1.3f + dx, size.height)
    path.lineTo(-w * 0.3f + dx, size.height)
    path.close()
    drawPath(path, color)
    for ((top, ph) in tops) {
        val cap = Path().apply {
            moveTo(top.x - ph * 0.26f, top.y + ph * 0.3f)
            lineTo(top.x, top.y)
            lineTo(top.x + ph * 0.26f, top.y + ph * 0.3f)
            lineTo(top.x + ph * 0.14f, top.y + ph * 0.36f)
            lineTo(top.x, top.y + ph * 0.27f)
            lineTo(top.x - ph * 0.17f, top.y + ph * 0.38f)
            close()
        }
        drawPath(cap, snow)
    }
}

private fun DrawScope.waves(baseY: Float, dx: Float, color: Color) {
    val w = size.width
    for (row in 0..1) {
        val y = baseY - size.height * (0.03f + row * 0.05f)
        val path = Path()
        var x = -w * 0.2f + dx + row * w * 0.13f
        path.moveTo(x, y)
        while (x < w * 1.2f) {
            path.quadraticTo(x + w * 0.05f, y - size.height * 0.02f, x + w * 0.1f, y)
            x += w * 0.1f
        }
        drawPath(path, color, style = Stroke(width = size.height * 0.006f, cap = StrokeCap.Round))
    }
}

private fun DrawScope.clouds(color: Color, a: Float) {
    val w = size.width
    val h = size.height
    val shapes = listOf(Offset(0.2f, 0.16f) to 0.09f, Offset(0.55f, 0.1f) to 0.07f, Offset(0.82f, 0.22f) to 0.08f)
    for ((c, r) in shapes) {
        val cx = c.x * w
        val cy = c.y * h
        val cr = r * h
        drawCircle(color, cr, Offset(cx, cy), alpha = 0.5f * a)
        drawCircle(color, cr * 0.7f, Offset(cx - cr, cy + cr * 0.3f), alpha = 0.45f * a)
        drawCircle(color, cr * 0.75f, Offset(cx + cr * 1.1f, cy + cr * 0.25f), alpha = 0.45f * a)
    }
}

private fun DrawScope.glints(gy: Float, color: Color, a: Float) {
    if (a <= 0f) return
    val w = size.width
    for (i in 0 until 10) {
        val x = frac(sin(i * 4.77) * 311.7) * w
        val y = gy - size.height * (0.03f + 0.09f * frac(sin(i * 9.13) * 717.3))
        drawLine(color, Offset(x, y), Offset(x + w * 0.03f, y),
            strokeWidth = size.height * 0.004f, cap = StrokeCap.Round, alpha = 0.5f * a)
    }
}

private fun DrawScope.bales(x: Float, gy: Float, color: Color) {
    val u = size.height
    val r = CornerRadius(u * 0.01f)
    drawRoundRect(color, topLeft = Offset(x, gy - u * 0.05f), size = Size(u * 0.09f, u * 0.05f), cornerRadius = r)
    drawRoundRect(color, topLeft = Offset(x + u * 0.05f, gy - u * 0.1f), size = Size(u * 0.09f, u * 0.05f), cornerRadius = r)
    drawRoundRect(color, topLeft = Offset(x + u * 0.1f, gy - u * 0.05f), size = Size(u * 0.09f, u * 0.05f), cornerRadius = r)
}

private fun DrawScope.spire(x: Float, gy: Float, hgt: Float, color: Color) {
    val wd = size.width * 0.03f
    drawRect(color, topLeft = Offset(x - wd * 1.4f, gy - hgt * 0.3f), size = Size(wd * 2.8f, hgt * 0.3f))
    val path = Path().apply {
        moveTo(x - wd / 2, gy - hgt * 0.3f)
        lineTo(x, gy - hgt)
        lineTo(x + wd / 2, gy - hgt * 0.3f)
        close()
    }
    drawPath(path, color)
}

private fun DrawScope.drawSceneLayer(scene: SceneKind, layer: SceneLayer, p: ScenePalette, dx: Float, a: Float) {
    if (a <= 0f) return
    val w = size.width
    val h = size.height
    val gy = h * GROUND_FRACTION
    val far = p.far.fade(a)
    val mid = p.mid.fade(a)
    val near = p.near.fade(a)
    when (scene) {
        SceneKind.LONDON -> when (layer) {
            SceneLayer.FAR -> {
                roofs(gy - h * 0.04f, h * 0.14f, dx, far, seed = 3, chimneys = true)
                spire(0.14f * w + dx, gy - h * 0.04f, h * 0.32f, far)
                clockTower(0.76f * w + dx, gy - h * 0.04f, h * 0.26f, far, p, a)
            }
            SceneLayer.MID -> {
                roofs(gy, h * 0.17f, dx, mid, seed = 11, chimneys = true)
                bigArch(0.32f * w + dx, gy, w * 0.56f, h * 0.2f, mid)
                windows(0.05f * w + dx, 0.95f * w + dx, gy - h * 0.03f, gy - h * 0.12f, p, a, seed = 5)
            }
            SceneLayer.NEAR -> {
                ground(near, gy, dx)
                // Platform edge catching the lamplight.
                drawLine(lerp(p.near, p.light, 0.35f).fade(a),
                    Offset(dx - w * 0.4f, gy + h * 0.012f), Offset(dx + w * 1.4f, gy + h * 0.012f),
                    strokeWidth = h * 0.006f, alpha = 0.35f * p.lightAlpha.coerceAtLeast(0.25f))
                train(0.34f * w + dx, gy, 0.26f, near, p, a)
                lamppost(0.68f * w + dx, gy, h * 0.26f, near, p, a)
                lamppost(0.92f * w + dx, gy, h * 0.21f, near, p, a)
            }
        }
        SceneKind.DEPARTURE -> when (layer) {
            SceneLayer.FAR -> {
                humps(gy - h * 0.02f, h * 0.1f, dx, far, seed = 2.7)
                roofs(gy - h * 0.02f, h * 0.07f, dx, far, seed = 5, chimneys = false)
            }
            SceneLayer.MID -> {
                roofs(gy, h * 0.11f, dx, mid, seed = 9, chimneys = true)
                tree(0.55f * w + dx, gy, h * 0.1f, mid)
                tree(0.72f * w + dx, gy, h * 0.08f, mid)
            }
            SceneLayer.NEAR -> {
                ground(near, gy, dx)
                val road = Path().apply {
                    moveTo(0.42f * w + dx, gy)
                    lineTo(0.58f * w + dx, gy)
                    lineTo(0.85f * w + dx, h)
                    lineTo(0.15f * w + dx, h)
                    close()
                }
                drawPath(road, lerp(p.near, p.far, 0.5f).fade(a))
                fence(0.36f * w + dx, gy, 0.08f * w + dx, h * 0.97f, near)
                telegraph(gy, dx, near)
                tree(0.3f * w + dx, gy, h * 0.12f, near)
                tree(0.88f * w + dx, gy, h * 0.15f, near)
            }
        }
        SceneKind.DOVER -> when (layer) {
            SceneLayer.FAR -> {
                sea(gy - h * 0.02f, gy - h * 0.18f, lerp(p.skyBottom, p.far, 0.55f).fade(a), dx)
                val boatX = 0.28f * w + dx
                val boatY = gy - h * 0.18f
                drawRect(far, topLeft = Offset(boatX, boatY - h * 0.015f), size = Size(w * 0.05f, h * 0.015f))
                drawLine(far, Offset(boatX + w * 0.025f, boatY - h * 0.015f),
                    Offset(boatX + w * 0.025f, boatY - h * 0.045f), strokeWidth = h * 0.006f)
                birds(p, a)
            }
            SceneLayer.MID -> {
                // White cliffs: stepped flat-top silhouette, dimmed to a shadow at night.
                val cliff = Path().apply {
                    moveTo(0.5f * w + dx, gy)
                    lineTo(0.55f * w + dx, gy - h * 0.2f)
                    lineTo(0.68f * w + dx, gy - h * 0.22f)
                    lineTo(0.72f * w + dx, gy - h * 0.245f)
                    lineTo(0.95f * w + dx, gy - h * 0.26f)
                    lineTo(w * 1.2f + dx, gy - h * 0.08f)
                    lineTo(w * 1.2f + dx, gy)
                    close()
                }
                drawPath(cliff, lerp(lerp(p.far, Color(0xFFF0EBDD), 0.6f), p.far, p.starAlpha * 0.85f).fade(a))
            }
            SceneLayer.NEAR -> {
                ground(near, gy, dx)
                railing(0.02f * w + dx, 0.5f * w + dx, gy, h * 0.06f, near)
                lighthouse(0.85f * w + dx, gy, h * 0.3f, near, p, a)
            }
        }
        SceneKind.CALAIS -> when (layer) {
            SceneLayer.FAR -> {
                sea(gy - h * 0.02f, gy - h * 0.14f, lerp(p.skyBottom, p.far, 0.55f).fade(a), dx)
                roofs(gy - h * 0.12f, h * 0.05f, dx, far, seed = 21, chimneys = false)
                birds(p, a)
            }
            SceneLayer.MID -> {
                crane(0.2f * w + dx, gy - h * 0.02f, h * 0.16f, mid)
                crane(0.55f * w + dx, gy - h * 0.02f, h * 0.13f, mid)
                drawRect(mid, topLeft = Offset(0.6f * w + dx, gy - h * 0.08f), size = Size(w * 0.35f, h * 0.06f))
                pierLights(0.62f * w + dx, 0.92f * w + dx, gy - h * 0.085f, p, a)
            }
            SceneLayer.NEAR -> {
                ground(near, gy, dx)
                steamer(0.18f * w + dx, 0.78f * w + dx, gy, near, p, a)
                lamppost(0.08f * w + dx, gy, h * 0.12f, near, p, a)
                lamppost(0.88f * w + dx, gy, h * 0.12f, near, p, a)
            }
        }
        SceneKind.PARIS -> when (layer) {
            SceneLayer.FAR -> {
                roofs(gy - h * 0.03f, h * 0.12f, dx, far, seed = 17, chimneys = true)
                dome(0.3f * w + dx, gy - h * 0.03f, w * 0.11f, far)
                spire(0.68f * w + dx, gy - h * 0.03f, h * 0.28f, far)
            }
            SceneLayer.MID -> {
                roofs(gy, h * 0.16f, dx, mid, seed = 23, chimneys = true)
                windows(0.05f * w + dx, 0.95f * w + dx, gy - h * 0.02f, gy - h * 0.1f, p, a, seed = 8)
            }
            SceneLayer.NEAR -> {
                ground(near, gy, dx)
                bigArch(0.32f * w + dx, gy, w * 0.42f, h * 0.22f, near)
                train(0.62f * w + dx, gy, 0.2f, near, p, a)
                lamppost(0.06f * w + dx, gy, h * 0.2f, near, p, a)
                lamppost(0.9f * w + dx, gy, h * 0.19f, near, p, a)
            }
        }
        SceneKind.ALPS -> when (layer) {
            SceneLayer.FAR -> peaks(gy - h * 0.02f, dx, far,
                lerp(lerp(p.far, Color.White, 0.75f), p.far, p.starAlpha * 0.7f).fade(a))
            SceneLayer.MID -> {
                humps(gy, h * 0.22f, dx, mid, seed = 7.3)
                fir(0.2f * w + dx, gy, h * 0.13f, mid)
                fir(0.34f * w + dx, gy, h * 0.1f, mid)
                fir(0.62f * w + dx, gy, h * 0.14f, mid)
                fir(0.78f * w + dx, gy, h * 0.1f, mid)
            }
            SceneLayer.NEAR -> {
                ground(near, gy, dx)
                drawRect(lerp(p.near, Color.White, 0.5f), topLeft = Offset(dx - w * 0.4f, gy),
                    size = Size(w * 1.8f, h * 0.012f), alpha = 0.6f * a)
                fir(0.14f * w + dx, gy, h * 0.2f, near)
                fir(0.26f * w + dx, gy, h * 0.15f, near)
                fir(0.72f * w + dx, gy, h * 0.22f, near)
                fir(0.85f * w + dx, gy, h * 0.16f, near)
                tunnel(0.5f * w + dx, gy, w * 0.2f, h * 0.16f, lerp(p.near, Color.Black, 0.55f).fade(a))
                embankment(0.52f * w + dx, 1.05f * w + dx, gy, h * 0.05f,
                    lerp(p.near, p.mid, 0.2f).fade(a), near)
            }
        }
        SceneKind.TURIN -> when (layer) {
            SceneLayer.FAR -> {
                humps(gy - h * 0.02f, h * 0.12f, dx, far, seed = 4.1)
                dome(0.3f * w + dx, gy - h * 0.02f, w * 0.12f, far)
                spire(0.62f * w + dx, gy - h * 0.02f, h * 0.3f, far)
            }
            SceneLayer.MID -> {
                roofs(gy, h * 0.08f, dx, mid, seed = 31, chimneys = false)
                windows(0.05f * w + dx, 0.95f * w + dx, gy - h * 0.01f, gy - h * 0.06f, p, a, seed = 12)
                cypress(0.15f * w + dx, gy, h * 0.16f, mid)
                cypress(0.24f * w + dx, gy, h * 0.12f, mid)
                cypress(0.55f * w + dx, gy, h * 0.15f, mid)
                cypress(0.68f * w + dx, gy, h * 0.11f, mid)
                cypress(0.9f * w + dx, gy, h * 0.14f, mid)
            }
            SceneLayer.NEAR -> {
                ground(near, gy, dx)
                arcade(0.08f * w + dx, 0.55f * w + dx, gy, h * 0.13f, near)
                embankment(0.55f * w + dx, 1.05f * w + dx, gy, h * 0.07f, lerp(p.near, p.mid, 0.25f).fade(a), near)
            }
        }
        SceneKind.BRINDISI -> when (layer) {
            SceneLayer.FAR -> {
                sea(gy - h * 0.02f, gy - h * 0.16f, lerp(p.skyBottom, p.far, 0.4f).fade(a), dx)
                crane(0.8f * w + dx, gy - h * 0.14f, h * 0.08f, far)
                crane(0.6f * w + dx, gy - h * 0.14f, h * 0.06f, far)
            }
            SceneLayer.MID -> {
                drawRect(mid, topLeft = Offset(0.05f * w + dx, gy - h * 0.07f), size = Size(w * 0.35f, h * 0.05f))
                pierLights(0.08f * w + dx, 0.38f * w + dx, gy - h * 0.075f, p, a)
                mast(0.45f * w + dx, gy - h * 0.02f, h * 0.16f, mid)
                mast(0.55f * w + dx, gy - h * 0.02f, h * 0.19f, mid)
                mast(0.66f * w + dx, gy - h * 0.02f, h * 0.14f, mid)
            }
            SceneLayer.NEAR -> {
                ground(near, gy, dx)
                steamer(0.3f * w + dx, 0.92f * w + dx, gy, near, p, a)
                bales(0.06f * w + dx, gy, near)
                lamppost(0.24f * w + dx, gy, h * 0.13f, near, p, a)
            }
        }
        SceneKind.MEDITERRANEAN -> when (layer) {
            SceneLayer.FAR -> {
                clouds(lerp(p.skyBottom, Color.White, 0.4f), a)
                sea(h * 0.86f, h * 0.5f, lerp(p.skyBottom, p.far, 0.5f).fade(a), dx)
                glints(h * 0.78f, p.celestial, (0.25f + p.lightAlpha * 0.5f) * a)
            }
            SceneLayer.MID -> {
                waves(h * 0.72f, dx, mid)
                waves(h * 0.8f, dx + w * 0.07f, mid)
            }
            SceneLayer.NEAR -> {
                val deck = Path().apply {
                    moveTo(-0.2f * w + dx, h)
                    lineTo(-0.2f * w + dx, h * 0.9f)
                    lineTo(1.2f * w + dx, h * 0.84f)
                    lineTo(1.2f * w + dx, h)
                    close()
                }
                drawPath(deck, near)
                drawLine(near, Offset(-0.2f * w + dx, h * 0.88f), Offset(1.2f * w + dx, h * 0.82f),
                    strokeWidth = h * 0.008f)
                var px = -0.1f * w + dx
                while (px < 1.1f * w + dx) {
                    val edge = mix(h * 0.88f, h * 0.82f, (px - dx + 0.2f * w) / (1.4f * w))
                    drawLine(near, Offset(px, edge), Offset(px, edge - h * 0.05f), strokeWidth = h * 0.006f)
                    px += w * 0.1f
                }
                drawRect(near, topLeft = Offset(0.7f * w + dx, h * 0.6f), size = Size(w * 0.045f, h * 0.24f))
                drawRect(lerp(p.near, Color.White, 0.45f).fade(a),
                    topLeft = Offset(0.7f * w + dx, h * 0.64f), size = Size(w * 0.045f, h * 0.02f))
                smoke(0.72f * w + dx, h * 0.58f, lerp(p.skyBottom, Color.White, 0.3f), a)
            }
        }
        SceneKind.SUEZ -> when (layer) {
            SceneLayer.FAR -> {
                humps(gy - h * 0.02f, h * 0.1f, dx, far, seed = 6.9)
                dome(0.68f * w + dx, gy - h * 0.06f, w * 0.1f, far)
                minaret(0.58f * w + dx, gy - h * 0.06f, h * 0.2f, far)
                minaret(0.82f * w + dx, gy - h * 0.06f, h * 0.24f, far)
            }
            SceneLayer.MID -> {
                humps(gy, h * 0.07f, dx, mid, seed = 9.4)
                palm(0.16f * w + dx, gy, h * 0.22f, mid)
                palm(0.28f * w + dx, gy, h * 0.17f, mid)
                palm(0.9f * w + dx, gy, h * 0.19f, mid)
            }
            SceneLayer.NEAR -> {
                ground(near, gy, dx)
                // Canal: water strip with a dissolving far edge, shipping, near bank.
                val water = lerp(p.skyBottom, p.far, 0.4f).fade(a)
                drawRect(
                    Brush.verticalGradient(
                        0f to water.copy(alpha = 0f), 0.45f to water,
                        startY = gy, endY = gy + h * 0.09f,
                    ),
                    topLeft = Offset(dx - w * 0.4f, gy), size = Size(w * 1.8f, h * 0.09f),
                )
                mast(0.56f * w + dx, gy + h * 0.07f, h * 0.22f, near)
                mast(0.7f * w + dx, gy + h * 0.06f, h * 0.16f, near)
                steamer(0.1f * w + dx, 0.4f * w + dx, gy + h * 0.085f, near, p, a)
                drawRect(lerp(p.near, Color.Black, 0.3f).fade(a),
                    topLeft = Offset(dx - w * 0.4f, gy + h * 0.1f), size = Size(w * 1.8f, h))
            }
        }
    }
}
