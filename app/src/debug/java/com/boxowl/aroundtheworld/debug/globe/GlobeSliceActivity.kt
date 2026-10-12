package com.boxowl.aroundtheworld.debug.globe

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

private val Paper = Color(0xFFFAF5EE)
private val OceanLight = Color(0xFFF1F6FB)
private val Ocean = Color(0xFFD8E5F0)
private val Land = Color(0xFFF0C6BF)
private val Coast = Color(0xFF6C649E)
private val FutureColor = Color(0xFF9A9AA5)
private val HeroColor = Color(0xFFC0392B)
private val InkSoft = Color(0xFF4A4A5A)
private val BannerBg = Color(0xFFC0392B)
private val MarkDone = Color(0xFFD9A441)

private const val GLOBE_SCALE = 1.0f

/**
 * Transport -> color of the traveled route part (V2.0d slice palette).
 * TRAIN/FERRY follow the land-warm family, SHIP/WATERWAY the sea-cold one;
 * ELEPHANT and SLEDGE (the two exotic legs) get distinct accent colors so the
 * legs stand out in the debug slice. Mapping is a debug-only decision.
 */
internal fun transportColor(t: RouteData.Transport): Color = when (t) {
    RouteData.Transport.TRAIN -> Color(0xFFD9822B)
    RouteData.Transport.FERRY -> Color(0xFFB0A26A)
    RouteData.Transport.SHIP -> Color(0xFF3E6FA8)
    RouteData.Transport.WATERWAY -> Color(0xFF3AA6B9)
    RouteData.Transport.ELEPHANT -> Color(0xFF8B5A2B)
    RouteData.Transport.SLEDGE -> Color(0xFF7D5BA6)
}

/**
 * Debug-only V2.0d vertical slice: SYNTHETIC confirmed real meters ->
 * CreditLedger (k = 20, read-only use of main) -> book km -> hero position and
 * the colored traveled part of the RouteDatasetV1 route on the debug globe.
 * In-memory only: no Room, no Health Connect, no prod navigation.
 * Start explicitly, e.g.:
 *   adb shell am start -n com.boxowl.aroundtheworld/.debug.globe.GlobeSliceActivity \
 *     --es scenario suez_after
 * Extras: --es scenario <start|suez_before|suez_after|jump_two_chapters|rollback|finish>
 * or free aggregates --el meters_prev <long> --el meters_today <long>.
 */
class GlobeSliceActivity : ComponentActivity() {
    private var resolvedState: MutableState<SliceScenario.Resolved>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val resolved = mutableStateOf(resolveScenario(intent))
        resolvedState = resolved
        val rings = assets.open("ne_land.bin").use { CoastlineData.decode(it) }
        setContent { GlobeSliceScreen(rings, resolved.value) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        resolvedState?.value = resolveScenario(intent)
    }

    private fun resolveScenario(source: Intent): SliceScenario.Resolved =
        SliceScenario.resolve(
            scenario = source.getStringExtra("scenario"),
            metersPrev = source.getLongExtra("meters_prev", 0L),
            metersToday = source.getLongExtra("meters_today", 0L),
        )
}

@Composable
private fun GlobeSliceScreen(rings: List<CoastlineData.Ring>, resolved: SliceScenario.Resolved) {
    val ledger = resolved.ledger
    val realTodayKm = (ledger.creditForDay(SliceScenario.TODAY)?.realMeters ?: 0L) / 1000.0
    val realTotalKm = ledger.totalRealMeters / 1000.0
    // Book km derive from CreditLedger.totalBookMeters ONLY: k = 20 is applied
    // exactly once, inside CreditLedger; here we just rescale meters -> km.
    val bookKm = SliceScenario.bookKmOf(ledger)

    val route = remember(bookKm) { RoutePosition.buildSceneRoute(bookKm) }
    val scene = remember(rings, route) { GlobeScene(rings, route) }
    val (heroLat, heroLon) = remember(route) { vecToLatLon(route.heroPos) }

    MaterialTheme(
        lightColorScheme(
            primary = Coast,
            background = Paper,
            surface = Paper,
            onSurface = InkSoft,
        ),
    ) {
        Surface(Modifier.fillMaxSize(), color = Paper) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                SyntheticBanner()
                BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                    val widthPx = constraints.maxWidth.toFloat()
                    val heightPx = constraints.maxHeight.toFloat()
                    val layout = remember(widthPx, heightPx, heroLat, heroLon) {
                        scene.layout(widthPx, heightPx, heroLat, heroLon, GLOBE_SCALE)
                    }
                    SliceCanvas(
                        layout = layout,
                        bookKm = bookKm,
                        centerLat = heroLat,
                        centerLon = heroLon,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                DiagnosticsPanel(
                    realTodayKm = realTodayKm,
                    realTotalKm = realTotalKm,
                    bookKm = bookKm,
                    description = resolved.description,
                )
            }
        }
    }
}

@Composable
private fun SyntheticBanner() {
    Box(
        Modifier.fillMaxWidth().background(BannerBg).padding(vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "СИНТЕТИЧЕСКИЕ ДАННЫЕ — тестовый контур",
            color = Color.White,
            style = MaterialTheme.typography.titleSmall,
        )
    }
}

@Composable
private fun SliceCanvas(
    layout: GlobeScene.Layout,
    bookKm: Double,
    centerLat: Double,
    centerLon: Double,
    modifier: Modifier,
) {
    val labelPaint = remember {
        android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.argb(255, 58, 58, 74)
        }
    }
    Canvas(modifier) {
        val r = layout.radius
        val c = layout.center
        drawCircle(
            brush = Brush.radialGradient(0f to OceanLight, 1f to Ocean, center = c, radius = r),
            radius = r,
            center = c,
        )
        drawCircle(Coast.copy(alpha = 0.35f), radius = r, center = c, style = Stroke(width = 1.dp.toPx()))
        drawPath(layout.graticule, Coast.copy(alpha = 0.16f), style = Stroke(width = 0.75f.dp.toPx()))
        drawPath(layout.land, Land)
        drawPath(layout.coast, Coast.copy(alpha = 0.85f), style = Stroke(width = 0.9f.dp.toPx()))
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
                color = transportColor(mode),
                start = o0,
                end = o1,
                strokeWidth = 3.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }

        // Chapter-finish markers on the same projection the scene just used.
        val frame = GlobeFrame(centerLat, centerLon)
        labelPaint.textSize = 10.5f.sp.toPx()
        val dotR = 4.dp.toPx()
        for (mark in RoutePosition.chapterMarks) {
            if (!frame.isVisible(mark.pos)) continue
            val p = projectWithLayout(layout, frame, mark.pos)
            val reached = bookKm >= mark.bookKm - 1e-9
            drawCircle(Color.White, radius = dotR, center = p)
            if (reached) {
                drawCircle(MarkDone, radius = dotR, center = p)
            }
            drawCircle(Coast, radius = dotR, center = p, style = Stroke(width = 1.2f.dp.toPx()))
            drawContext.canvas.nativeCanvas.drawText(
                mark.nameRu,
                p.x + dotR + 3.dp.toPx(),
                p.y - dotR,
                labelPaint,
            )
        }

        layout.hero?.let { p ->
            drawCircle(Color.White, radius = 7.dp.toPx(), center = p)
            drawCircle(HeroColor, radius = 5.dp.toPx(), center = p)
            drawCircle(
                HeroColor.copy(alpha = 0.4f),
                radius = 9.dp.toPx(),
                center = p,
                style = Stroke(width = 1.5f.dp.toPx()),
            )
        }
    }
}

@Composable
private fun DiagnosticsPanel(
    realTodayKm: Double,
    realTotalKm: Double,
    bookKm: Double,
    description: String,
) {
    val totalKm = RoutePosition.totalKm
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Сегодня, реальные км", style = MaterialTheme.typography.labelSmall)
                Text(
                    String.format(Locale.US, "%.2f", realTodayKm),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Всего, реальные км", style = MaterialTheme.typography.labelSmall)
                Text(
                    String.format(Locale.US, "%.2f", realTotalKm),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
        Text(
            String.format(
                Locale.US,
                "Книжный прогресс: %.0f из %.0f км (%.1f%%)",
                bookKm,
                totalKm,
                if (totalKm > 0) bookKm / totalKm * 100.0 else 0.0,
            ),
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(description, style = MaterialTheme.typography.bodySmall)
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            LegendDot(transportColor(RouteData.Transport.TRAIN))
            Text("поезд", style = MaterialTheme.typography.labelSmall)
            LegendDot(transportColor(RouteData.Transport.SHIP))
            Text("корабль", style = MaterialTheme.typography.labelSmall)
            LegendDot(transportColor(RouteData.Transport.FERRY))
            Text("паром", style = MaterialTheme.typography.labelSmall)
            LegendDot(transportColor(RouteData.Transport.WATERWAY))
            Text("канал/река", style = MaterialTheme.typography.labelSmall)
            LegendDot(transportColor(RouteData.Transport.ELEPHANT))
            Text("слон", style = MaterialTheme.typography.labelSmall)
            LegendDot(transportColor(RouteData.Transport.SLEDGE))
            Text("сани", style = MaterialTheme.typography.labelSmall)
            LegendDot(FutureColor)
            Text("впереди", style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun LegendDot(color: Color) {
    Canvas(Modifier.width(16.dp).height(6.dp)) {
        drawLine(
            color = color,
            start = Offset(0f, size.height / 2),
            end = Offset(size.width, size.height / 2),
            strokeWidth = 2.5f.dp.toPx(),
            cap = StrokeCap.Round,
        )
    }
}

/** Projects [p] with [frame] into the screen space of [layout]. */
private fun projectWithLayout(layout: GlobeScene.Layout, frame: GlobeFrame, p: Vec3): Offset {
    val proj = frame.project(p)
    return Offset(
        layout.center.x + (proj.x * layout.radius).toFloat(),
        layout.center.y - (proj.y * layout.radius).toFloat(),
    )
}
