package com.boxowl.aroundtheworld.expedition.globe

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.boxowl.aroundtheworld.expedition.path.DistanceLighting
import com.boxowl.aroundtheworld.expedition.route.RouteDatasetV1
import com.boxowl.aroundtheworld.expedition.route.Transport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

private val paper = Color(0xFFFAF5EE)
private val ocean = Color(0xFFD8E5F0)
private val land = Color(0xFFF0C6BF)
private val coast = Color(0xFF6C649E)
private val future = Color(0xFF9A9AA5)
private val hero = Color(0xFFC0392B)

private sealed interface CoastState {
    data object Loading : CoastState
    data class Ready(val rings: List<CoastlineData.Ring>) : CoastState
    data object Error : CoastState
}

/** The user map derives its position exclusively from persisted v2 totals. */
@Composable
fun V2GlobeMap(totalRealMeters: Long, totalBookMeters: Long) {
    val context = LocalContext.current
    val coastState = produceState<CoastState>(CoastState.Loading, context) {
        value = withContext(Dispatchers.IO) {
            runCatching { context.assets.open("ne_land.bin").use(CoastlineData::decode) }
                .fold({ CoastState.Ready(it) }, { CoastState.Error })
        }
    }.value
    val rings = (coastState as? CoastState.Ready)?.rings
    when {
        coastState == CoastState.Error -> Text("Карта берегов недоступна. Список остановок доступен ниже.")
        rings == null -> Text("Загружаем карту…")
        else -> {
            val bookKm = totalBookMeters / 1000.0
            val darkness = DistanceLighting.darknessAtRealKm(totalRealMeters / 1000.0)
            val route = remember(totalBookMeters) { RoutePosition.buildSceneRoute(bookKm) }
            val (lat, lon) = remember(route) { vecToLatLon(route.heroPos) }
            val stops = remember {
                GlobeStopCardsV1.cards.map { card ->
                    val node = RouteDatasetV1.node(card.nodeId)
                    GlobeStop(card.nameRu, node.lat, node.lon)
                }
            }
            val scene = remember(rings, route, stops) { GlobeScene(rings, route, stops) }
            BoxWithConstraints(Modifier.fillMaxWidth().height(320.dp)) {
                val width = constraints.maxWidth.toFloat()
                val height = constraints.maxHeight.toFloat()
                val layout = remember(scene, width, height, lat, lon) {
                    scene.layout(width, height, lat, lon, 1.0f)
                }
                Canvas(Modifier.fillMaxWidth().height(320.dp).semantics {
                    contentDescription = "Глобус: подтверждено ${String.format(Locale.US, "%.3f", bookKm)} книжных километров; положение героя на маршруте"
                }) {
                    val c = layout.center
                    val r = layout.radius
                    drawCircle(brush = Brush.radialGradient(0f to paper, 1f to ocean, center = c, radius = r),
                        radius = r, center = c)
                    drawCircle(coast.copy(alpha = 0.4f), radius = r, center = c,
                        style = Stroke(width = 1.dp.toPx()))
                    drawPath(layout.graticule, coast.copy(alpha = 0.17f),
                        style = Stroke(width = 0.7.dp.toPx()))
                    drawPath(layout.land, land)
                    drawPath(layout.coast, coast, style = Stroke(width = 0.9.dp.toPx()))
                    drawCircle(Color(0xFF071830).copy(alpha = (darkness * 0.72).toFloat()), radius = r, center = c)
                    drawPath(layout.futureRoute, future,
                        style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(7.dp.toPx(), 5.dp.toPx()))))
                    layout.pastRoute.forEach { (from, to, mode) ->
                        drawLine(transportColor(mode), from, to, strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
                    }
                    val frame = GlobeFrame(lat, lon)
                    RoutePosition.chapterMarks.forEach { mark ->
                        if (frame.isVisible(mark.pos)) {
                            val p = frame.project(mark.pos)
                            val center = Offset(c.x + (p.x * r).toFloat(), c.y - (p.y * r).toFloat())
                            drawCircle(if (bookKm >= mark.bookKm) Color(0xFFD9A441) else Color.White,
                                radius = 4.dp.toPx(), center = center)
                            drawCircle(coast, radius = 4.dp.toPx(), center = center,
                                style = Stroke(width = 1.dp.toPx()))
                        }
                    }
                    layout.hero?.let { point ->
                        drawCircle(Color.White, radius = 7.dp.toPx(), center = point)
                        drawCircle(hero, radius = 5.dp.toPx(), center = point)
                    }
                }
            }
        }
    }
}

private fun transportColor(mode: Transport): Color = when (mode) {
    Transport.TRAIN -> Color(0xFFD9822B)
    Transport.SHIP -> Color(0xFF3E6FA8)
    Transport.FERRY -> Color(0xFFB0A26A)
    Transport.WATERWAY -> Color(0xFF3AA6B9)
    Transport.ELEPHANT -> Color(0xFF8B5A2B)
    Transport.SLEDGE -> Color(0xFF7D5BA6)
}
