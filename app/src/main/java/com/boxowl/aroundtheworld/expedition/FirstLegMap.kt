package com.boxowl.aroundtheworld.expedition

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val ink = Color(0xFF173543)
private val rust = Color(0xFFA4412C)
private val paper = Color(0xFFFFF8E8)
private val sea = Color(0xFFD8E6E3)

@Composable
internal fun FirstLegMap(expedition: Expedition, onOpenDiary: () -> Unit) {
    val map = FirstLegMapProjection.from(expedition)
    val current = map.stops[map.currentIndex].stop
    val next = map.nextIndex?.let { map.stops[it].stop }
    val largeFont = LocalConfiguration.current.fontScale >= 1.3f
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("АТЛАС · ПЕРВАЯ ГЛАВА", color = rust, style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold)
        Text("Лондон → Суэц", color = ink, fontFamily = FontFamily.Serif,
            style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(when {
            map.knownSteps == null -> "Положение пока неизвестно: дневные итоги шагов ещё не получены. Показан старт в Лондоне."
            map.knownSteps == 0L -> "Подтверждено 0 шагов. Путешественник в Лондоне."
            next == null -> "Суэц достигнут. Известный путь первого участка: ${formatJourneySteps(map.goal)} шагов."
            else -> "Известный путь: ${formatJourneySteps(map.knownSteps.coerceAtMost(map.goal))} из ${formatJourneySteps(map.goal)} шагов. " +
                (if (map.fractionToNext > 0f) "Участок: ${current.name} → ${next.name}. "
                else "Сейчас: ${current.name}. ") +
                "До ${next.name}: ${formatJourneySteps(next.threshold - map.knownSteps)} шагов."
        }, color = ink)
        if (!largeFont) RouteIllustration(map)
        else Text("Схема маршрута ниже представлена списком: все остановки и их состояния доступны текстом.",
            style = MaterialTheme.typography.bodySmall)
        Text("Остановки по маршруту", color = ink, fontFamily = FontFamily.Serif,
            style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        map.stops.forEach { mapped ->
            val stop = mapped.stop
            OutlinedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(stop.name, color = ink, style = MaterialTheme.typography.titleMedium,
                            fontWeight = if (mapped.state == MapStopState.CURRENT) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier.weight(1f))
                        Text("${formatJourneySteps(stop.threshold)} шагов", color = rust,
                            style = MaterialTheme.typography.labelMedium)
                    }
                    Text(when (mapped.state) {
                        MapStopState.CURRENT -> if (map.knownSteps == null) "Старт · положение не подтверждено"
                            else if (map.fractionToNext > 0f) "Начало текущего участка · запись открыта"
                            else "Текущее положение · запись открыта"
                        MapStopState.PASSED -> "Пройдено · запись открыта"
                        MapStopState.REMEMBERED -> "Впереди по текущим шагам · запись уже открыта"
                        MapStopState.AHEAD -> "Впереди · запись откроется на пороге"
                    }, style = MaterialTheme.typography.bodySmall, color = ink)
                }
            }
        }
        if (map.stops.any { it.diaryAvailable }) {
            OutlinedButton(onClick = onOpenDiary) { Text("Открыть дневник") }
        }
        Text("Схема показывает порядок остановок, а не географически точный трек. Пороги указаны в шагах для выбранного темпа. После пересчёта шагов текущая позиция может отступить; открытые записи сохраняются.",
            style = MaterialTheme.typography.bodySmall, color = ink)
    }
}

@Composable
private fun RouteIllustration(map: FirstLegMapProjection) {
    val density = LocalDensity.current
    val points = listOf(
        .43f, .53f, .48f, .55f, .44f, .52f, .46f, .54f, .48f, .55f,
    )
    val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.rgb(23, 53, 67)
        textSize = with(density) { 11.sp.toPx() }
        typeface = android.graphics.Typeface.create("serif", android.graphics.Typeface.NORMAL)
    }
    Canvas(Modifier.fillMaxWidth().height(530.dp).background(paper, RoundedCornerShape(12.dp))
        .semantics { contentDescription = "Иллюстрированная схема пути Лондон — Суэц. Подробный список остановок и состояний расположен ниже." }) {
        val w = size.width
        val h = size.height
        val y = { index: Int -> h * (.055f + index * .099f) }
        val p = { index: Int -> Offset(w * points[index], y(index)) }
        val coast = Path().apply {
            moveTo(0f, 0f); lineTo(w * .21f, 0f)
            lineTo(w * .16f, h * .18f); lineTo(w * .29f, h * .28f)
            lineTo(w * .18f, h * .42f); lineTo(0f, h * .48f); close()
        }
        drawPath(coast, Color(0xFFE8D9BC))
        val southLand = Path().apply {
            moveTo(w, h * .48f); lineTo(w * .83f, h * .57f)
            lineTo(w * .92f, h * .72f); lineTo(w * .7f, h)
            lineTo(w, h); close()
        }
        drawPath(southLand, Color(0xFFE8D9BC))
        drawRect(sea.copy(alpha = .48f), topLeft = Offset(0f, h * .73f),
            size = androidx.compose.ui.geometry.Size(w * .69f, h * .27f))
        val route = Path().apply {
            moveTo(p(0).x, p(0).y)
            for (i in 1 until points.size) lineTo(p(i).x, p(i).y)
        }
        drawPath(route, ink.copy(alpha = .55f), style = Stroke(width = 2.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(7.dp.toPx(), 5.dp.toPx()))))
        for (i in 0 until map.currentIndex) {
            drawLine(rust, p(i), p(i + 1), strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
        }
        val traveler = if (map.nextIndex == null) p(map.currentIndex) else {
            val a = p(map.currentIndex); val b = p(map.nextIndex)
            Offset(a.x + (b.x - a.x) * map.fractionToNext,
                a.y + (b.y - a.y) * map.fractionToNext)
        }
        if (map.nextIndex != null) {
            drawLine(rust, p(map.currentIndex), traveler, strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
        }
        map.stops.forEachIndexed { index, mapped ->
            val point = p(index)
            drawCircle(if (index <= map.currentIndex) rust else ink, radius = 5.dp.toPx(), center = point)
            drawCircle(paper, radius = 2.dp.toPx(), center = point)
            val mapLabel = when (mapped.stop.id) {
                "departure" -> "За крышами"
                "mediterranean" -> "Средиземное"
                else -> mapped.stop.name
            }
            drawContext.canvas.nativeCanvas.drawText(mapLabel,
                if (index % 2 == 0) w * .04f else w * .62f,
                point.y + 4.dp.toPx(), label)
        }
        drawCircle(paper, radius = 10.dp.toPx(), center = traveler)
        if (map.knownSteps == null) {
            drawCircle(ink, radius = 7.dp.toPx(), center = traveler,
                style = Stroke(width = 2.dp.toPx()))
        } else {
            drawCircle(rust, radius = 7.dp.toPx(), center = traveler)
            drawCircle(paper, radius = 2.dp.toPx(), center = traveler)
        }
    }
}
