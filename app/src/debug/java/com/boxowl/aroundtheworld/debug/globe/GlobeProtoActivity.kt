package com.boxowl.aroundtheworld.debug.globe

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.min
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

private val Paper = Color(0xFFFAF5EE)
private val OceanLight = Color(0xFFF1F6FB)
private val Ocean = Color(0xFFD8E5F0)
private val Land = Color(0xFFF0C6BF)
private val Coast = Color(0xFF6C649E)
private val TrainColor = Color(0xFFD9822B)
private val ShipColor = Color(0xFF3E6FA8)
private val FutureColor = Color(0xFF9A9AA5)
private val HeroColor = Color(0xFFC0392B)
private val InkSoft = Color(0xFF4A4A5A)

private const val MIN_SCALE = 0.6f
private const val MAX_SCALE = 2.2f
private const val LABEL_SCALE = 0.9f

/**
 * Debug-only V21 globe prototype: stylized orthographic globe spun by gestures,
 * test route/stops (RouteData is TEST data). No Room, no Health Connect.
 * Start with:
 *   adb shell am start -n com.boxowl.aroundtheworld/.debug.globe.GlobeProtoActivity
 */
class GlobeProtoActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val rings = assets.open("ne_land.bin").use { CoastlineData.decode(it) }
        val route = RouteData.build()
        setContent { GlobeProtoScreen(GlobeScene(rings, route), route) }
    }
}

@Composable
private fun GlobeProtoScreen(scene: GlobeScene, route: RouteData.Route) {
    val (heroLat, heroLon) = remember { vecToLatLon(route.heroPos) }
    var centerLat by remember { mutableStateOf(heroLat) }
    var centerLon by remember { mutableStateOf(heroLon) }
    var scale by remember { mutableStateOf(1f) }
    var selected by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    var gestureJob by remember { mutableStateOf<Job?>(null) }

    fun wrapLon(lon: Double): Double {
        var l = lon % 360.0
        if (l > 180.0) l -= 360.0
        if (l < -180.0) l += 360.0
        return l
    }

    fun flyToHero() {
        gestureJob?.cancel()
        gestureJob = scope.launch {
            val fromLat = centerLat
            val fromLon = centerLon
            val fromScale = scale
            val dLon = ((heroLon - fromLon + 540.0) % 360.0) - 180.0
            val start = withFrameNanos { it }
            val duration = 600_000_000L
            while (true) {
                val now = withFrameNanos { it }
                val t = ((now - start).toFloat() / duration).coerceIn(0f, 1f)
                val e = 1f - (1f - t) * (1f - t) * (1f - t)
                centerLat = fromLat + (heroLat - fromLat) * e
                centerLon = fromLon + dLon * e
                scale = fromScale + (1f - fromScale) * e
                if (t >= 1f) break
            }
        }
    }

    MaterialTheme(
        lightColorScheme(
            primary = Coast,
            background = Paper,
            surface = Paper,
            onSurface = InkSoft,
        ),
    ) {
        Surface(Modifier.fillMaxSize(), color = Paper) {
            Column(Modifier.fillMaxSize()) {
                BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                    val widthPx = constraints.maxWidth.toFloat()
                    val heightPx = constraints.maxHeight.toFloat()
                    val layout = remember(widthPx, heightPx, centerLat, centerLon, scale) {
                        scene.layout(widthPx, heightPx, centerLat, centerLon, scale)
                    }
                    GlobeCanvas(
                        layout = layout,
                        showLabels = scale >= LABEL_SCALE,
                        selected = selected,
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                awaitEachGesture {
                                    gestureJob?.cancel()
                                    awaitFirstDown(requireUnconsumed = false)
                                    val r0 = min(widthPx, heightPx) * 0.40f
                                    var vLon = 0.0
                                    var vLat = 0.0
                                    var lastTime = 0L
                                    var moved = false
                                    while (true) {
                                        val event = awaitPointerEvent()
                                        if (event.changes.none { it.pressed }) break
                                        val zoom = event.calculateZoom()
                                        if (zoom != 1f) {
                                            scale = (scale * zoom).coerceIn(MIN_SCALE, MAX_SCALE)
                                        }
                                        val pan = event.calculatePan()
                                        if (pan != Offset.Zero) {
                                            moved = true
                                            val r = r0 * scale
                                            val cosLat = cos(Math.toRadians(centerLat))
                                                .coerceAtLeast(0.2)
                                            val dLon = -pan.x / r * (180.0 / PI) / cosLat
                                            val dLat = pan.y / r * (180.0 / PI)
                                            centerLon = wrapLon(centerLon + dLon)
                                            centerLat = clampCenterLat(centerLat + dLat)
                                            val now = event.changes.first().uptimeMillis
                                            if (lastTime != 0L) {
                                                val dt = (now - lastTime).coerceAtLeast(1) / 1000.0
                                                vLon = 0.7 * vLon + 0.3 * (dLon / dt)
                                                vLat = 0.7 * vLat + 0.3 * (dLat / dt)
                                            }
                                            lastTime = now
                                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                                        }
                                    }
                                    val speed = hypot(vLon, vLat)
                                    if (moved && speed > 20.0) {
                                        var flLon = vLon
                                        var flLat = vLat
                                        gestureJob = scope.launch {
                                            var last = withFrameNanos { it }
                                            while (hypot(flLon, flLat) > 3.0) {
                                                withFrameNanos { now ->
                                                    val dt = ((now - last) / 1e9f).coerceAtMost(0.05f)
                                                    last = now
                                                    val decay = exp(-3.0 * dt)
                                                    flLon *= decay
                                                    flLat *= decay
                                                    centerLon = wrapLon(centerLon + flLon * dt)
                                                    centerLat = clampCenterLat(centerLat + flLat * dt)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            .pointerInput(Unit) {
                                detectTapGestures { tap ->
                                    val hitRadius = 28.dp.toPx()
                                    val hit = layout.stopMarks
                                        .filter { (it.center - tap).getDistance() <= hitRadius }
                                        .minByOrNull { (it.center - tap).getDistance() }
                                    selected = hit?.stop?.name
                                }
                            },
                    )
                }
                BottomBar(
                    selected = selected,
                    onClearSelection = { selected = null },
                    onFlyToHero = { flyToHero() },
                )
            }
        }
    }
}

@Composable
private fun GlobeCanvas(
    layout: GlobeScene.Layout,
    showLabels: Boolean,
    selected: String?,
    modifier: Modifier,
) {
    val labelPaint = remember {
        android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.argb(255, 58, 58, 74)
        }
    }
    val density = LocalDensity.current
    Canvas(modifier) {
        val r = layout.radius
        val c = layout.center

        // Ocean disk with a soft radial falloff, then a thin rim.
        drawCircle(
            brush = Brush.radialGradient(
                0f to OceanLight,
                1f to Ocean,
                center = c,
                radius = r,
            ),
            radius = r,
            center = c,
        )
        drawCircle(Coast.copy(alpha = 0.35f), radius = r, center = c, style = Stroke(width = 1.dp.toPx()))

        // Graticule under the land.
        drawPath(layout.graticule, Coast.copy(alpha = 0.16f), style = Stroke(width = 0.75f.dp.toPx()))

        // Land fill + coastline strokes.
        drawPath(layout.land, Land)
        drawPath(layout.coast, Coast.copy(alpha = 0.85f), style = Stroke(width = 0.9f.dp.toPx()))

        // Future route: grey dashed, under the traveled part.
        drawPath(
            layout.futureRoute,
            FutureColor,
            style = Stroke(
                width = 2.2f.dp.toPx(),
                cap = StrokeCap.Round,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(7.dp.toPx(), 5.dp.toPx()), 0f),
            ),
        )
        for ((o0, o1, mode) in layout.pastRoute) {
            drawLine(
                color = if (mode == RouteData.Transport.TRAIN) TrainColor else ShipColor,
                start = o0,
                end = o1,
                strokeWidth = 3.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }

        // Stops.
        val dotR = 3.5f.dp.toPx()
        labelPaint.textSize = 10.5f.sp.toPx()
        val labelDx = 75.dp.toPx()
        val labelDy = 16.dp.toPx()
        val drawnLabels = mutableListOf<Offset>()
        for (mark in layout.stopMarks) {
            val p = mark.center
            drawCircle(Color.White, radius = dotR, center = p)
            drawCircle(Coast, radius = dotR, center = p, style = Stroke(width = 1.2f.dp.toPx()))
            if (mark.stop.name == selected) {
                drawCircle(HeroColor, radius = dotR + 3.dp.toPx(), center = p, style = Stroke(width = 1.4f.dp.toPx()))
            }
            if (showLabels) {
                // Cheap collision avoidance: skip labels crowding an already drawn one.
                val crowded = drawnLabels.any {
                    kotlin.math.abs(it.x - p.x) < labelDx && kotlin.math.abs(it.y - p.y) < labelDy
                }
                if (!crowded || mark.stop.name == selected) {
                    drawContext.canvas.nativeCanvas.drawText(
                        mark.stop.name,
                        p.x + dotR + 3.dp.toPx(),
                        p.y - dotR,
                        labelPaint,
                    )
                    drawnLabels.add(p)
                }
            }
        }

        // Hero marker.
        layout.hero?.let { p ->
            drawCircle(Color.White, radius = 7.dp.toPx(), center = p)
            drawCircle(HeroColor, radius = 5.dp.toPx(), center = p)
            drawCircle(HeroColor.copy(alpha = 0.4f), radius = 9.dp.toPx(), center = p, style = Stroke(width = 1.5f.dp.toPx()))
        }
    }
}

@Composable
private fun BottomBar(selected: String?, onClearSelection: () -> Unit, onFlyToHero: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            LegendLine(TrainColor, dashed = false)
            Text("поезд", style = MaterialTheme.typography.bodySmall)
            LegendLine(ShipColor, dashed = false)
            Text("корабль", style = MaterialTheme.typography.bodySmall)
            LegendLine(FutureColor, dashed = true)
            Text("впереди", style = MaterialTheme.typography.bodySmall)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = selected?.let { "Остановка: $it" }
                    ?: "Тестовые данные маршрута · прототип V21 · тап по маркеру — название",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f),
            )
            if (selected != null) {
                Button(onClick = onClearSelection) { Text("×") }
                Spacer(Modifier.width(8.dp))
            }
            Button(onClick = onFlyToHero) { Text("К герою") }
        }
    }
}

@Composable
private fun LegendLine(color: Color, dashed: Boolean) {
    Canvas(Modifier.width(26.dp).height(6.dp)) {
        drawLine(
            color = color,
            start = Offset(0f, size.height / 2),
            end = Offset(size.width, size.height / 2),
            strokeWidth = 2.5f.dp.toPx(),
            cap = StrokeCap.Round,
            pathEffect = if (dashed) {
                PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()), 0f)
            } else {
                null
            },
        )
    }
}
