package com.boxowl.aroundtheworld.expedition

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Locale

private val nightBg = Color(0xFF0E1B26)
private val nightCard = Color(0xFF1A2B3D)
private val nightLine = Color(0xFF35506A)
private val nightText = Color(0xFFF3EAD3)
private val nightMuted = Color(0xFFB9C6D2)
private val nightAccent = Color(0xFFE8975A)
private val nightBadgeText = Color(0xFF241505)

/** The scene is decorative. Every number and route label comes from the expedition snapshot. */
@Composable
internal fun JourneyHome(
    expedition: Expedition,
    now: Instant,
    sync: SyncResult?,
    syncing: Boolean,
    onRefresh: () -> Unit,
) {
    val today = now.atZone(expedition.zone).toLocalDate()
    val stepsToday = expedition.dailySteps[today]
    val stale = sync is SyncResult.ReadError || sync is SyncResult.PermissionRequired ||
        sync is SyncResult.Unavailable || sync is SyncResult.UpdateRequired
    val hasKnownProgress = expedition.dailySteps.isNotEmpty()
    val next = expedition.nextStop
    val projection = FirstLegMapProjection.from(expedition)
    val localTime = now.atZone(expedition.zone).toLocalTime()
    val largeFont = LocalConfiguration.current.fontScale >= 1.3f
    Column(
        Modifier.background(nightBg, RoundedCornerShape(20.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically) {
            Text("Вокруг света\nза 80 дней", color = nightText, fontFamily = FontFamily.Serif,
                style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("ДЕНЬ ${expedition.dayNumber(now)}", color = nightBadgeText, fontWeight = FontWeight.Bold,
                modifier = Modifier.background(nightAccent, RoundedCornerShape(50)).padding(horizontal = 12.dp, vertical = 8.dp))
        }
        if (!largeFont) {
            JourneySceneCanvas(projection, localTime,
                Modifier.fillMaxWidth().height(240.dp).clip(RoundedCornerShape(16.dp)))
        } else {
            val sceneNote = when {
                projection.knownSteps == null ->
                    "Показана стартовая сцена Лондона; положение на маршруте пока не подтверждено."
                projection.nextIndex == null -> "Показана сцена Суэца: первая глава пройдена."
                projection.fractionToNext > 0f ->
                    "Показан переход сцены: ${projection.stops[projection.currentIndex].stop.name} → " +
                        "${projection.stops[projection.nextIndex].stop.name}."
                else -> "Показана сцена: ${projection.stops[projection.currentIndex].stop.name}."
            }
            Text("Сцена пути заменена текстом при крупном шрифте. $sceneNote",
                color = nightMuted, style = MaterialTheme.typography.bodySmall)
        }
        Text("ПЕРВАЯ ГЛАВА", color = nightAccent, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        Text("Лондон → Суэц", color = nightText, fontFamily = FontFamily.Serif,
            style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        if (hasKnownProgress) {
            LinearProgressIndicator(
                progress = { expedition.firstLegSteps.toFloat() / expedition.firstLegGoal },
                modifier = Modifier.fillMaxWidth().height(8.dp), color = nightAccent,
                trackColor = nightLine,
            )
            Text("Известный путь: ${formatJourneySteps(expedition.firstLegSteps)} из ${formatJourneySteps(expedition.firstLegGoal)} шагов до Суэца",
                color = nightText, style = MaterialTheme.typography.bodyMedium)
        } else {
            Text("Прогресс участка пока неизвестен · цель ${formatJourneySteps(expedition.firstLegGoal)} шагов",
                color = nightText, style = MaterialTheme.typography.bodyMedium)
        }
        OutlinedCard(Modifier.fillMaxWidth(),
            colors = CardDefaults.outlinedCardColors(containerColor = nightCard),
            border = BorderStroke(1.dp, nightLine)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Шаги сегодня · ${today.format(DateTimeFormatter.ofPattern("d MMMM", Locale.forLanguageTag("ru-RU")))}",
                    color = nightMuted, style = MaterialTheme.typography.labelLarge)
                Text(stepsToday?.let(::formatJourneySteps) ?: "Нет данных", color = nightText,
                    style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text(when {
                    stepsToday == null -> "Итог за сегодня ещё не получен. Это не ноль шагов."
                    stale -> "Последний известный итог; текущая сверка не удалась."
                    syncing -> "Сверяем с Health Connect…"
                    sync is SyncResult.Updated && sync.gaps > 0 -> "Сохранённый итог; при пробелах сверки он мог не обновиться."
                    else -> "Сохранённый итог за календарный день экспедиции."
                }, color = nightMuted, style = MaterialTheme.typography.bodySmall)
            }
        }
        OutlinedCard(Modifier.fillMaxWidth(),
            colors = CardDefaults.outlinedCardColors(containerColor = nightCard),
            border = BorderStroke(1.dp, nightLine)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Следующая остановка", color = nightMuted, style = MaterialTheme.typography.labelLarge)
                Text(next?.name ?: "Суэц достигнут", color = nightText,
                    style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(if (next == null) "Первая глава пройдена."
                    else if (hasKnownProgress) "Ещё ${formatJourneySteps(next.threshold - expedition.totalSteps)} шагов по известным итогам"
                    else "Порог ${formatJourneySteps(next.threshold)} шагов; текущий путь пока неизвестен",
                    color = nightMuted, style = MaterialTheme.typography.bodyMedium)
            }
        }
        when (sync) {
            is SyncResult.Updated -> {
                if (sync.gaps > 0) Text("Для ${sync.gaps} дневных окон нет итога. Известный путь может быть неполным.",
                    color = nightMuted)
                if (sync.limited) Text("Ранние дни вне окна сверки; сохранённые итоги не удалены.",
                    color = nightMuted)
            }
            SyncResult.PermissionRequired -> Text("Нет доступа к шагам. Сохранённый путь остаётся.", color = nightText)
            SyncResult.Unavailable -> Text("Health Connect недоступен. Сохранённый путь остаётся.", color = nightText)
            SyncResult.UpdateRequired -> Text("Health Connect требует обновления. Сохранённый путь остаётся.", color = nightText)
            SyncResult.ReadError -> Text("Не удалось прочитать шаги. Сохранённый путь остаётся.", color = nightText)
            null -> Unit
        }
        if (syncing) Text("Сверяем дневные шаги…", color = nightMuted)
        Button(onClick = onRefresh, enabled = !syncing) { Text("Сверить шаги") }
        Text("День считается в часовом поясе старта: ${expedition.zone.id}. Первый день — с момента старта.",
            color = nightMuted, style = MaterialTheme.typography.bodySmall)
    }
}

internal fun formatJourneySteps(value: Long): String = "%,d".format(Locale.forLanguageTag("ru-RU"), value)
