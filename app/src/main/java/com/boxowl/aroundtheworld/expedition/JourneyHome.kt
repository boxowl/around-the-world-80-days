package com.boxowl.aroundtheworld.expedition

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.boxowl.aroundtheworld.R
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Locale

private val ink = Color(0xFF173543)
private val rust = Color(0xFFA4412C)
private val paper = Color(0xFFFFF8E8)

/** The illustration is decorative. Every number and route label comes from the expedition snapshot. */
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
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically) {
            Text("Вокруг света\nза 80 дней", color = ink, fontFamily = FontFamily.Serif,
                style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("ДЕНЬ ${expedition.dayNumber(now)}", color = paper, fontWeight = FontWeight.Bold,
                modifier = Modifier.background(ink, RoundedCornerShape(50)).padding(horizontal = 12.dp, vertical = 8.dp))
        }
        Image(painter = painterResource(R.drawable.london_book_scene),
            contentDescription = "Лондон: вокзал, фонари и паровой поезд на мокрой мостовой",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxWidth().height(220.dp))
        Text("ПЕРВАЯ ГЛАВА", color = rust, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        Text("Лондон → Суэц", color = ink, fontFamily = FontFamily.Serif,
            style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        if (hasKnownProgress) {
            LinearProgressIndicator(
                progress = { expedition.firstLegSteps.toFloat() / expedition.firstLegGoal },
                modifier = Modifier.fillMaxWidth().height(8.dp), color = rust,
                trackColor = Color(0xFFD8C9AF),
            )
            Text("Известный путь: ${formatJourneySteps(expedition.firstLegSteps)} из ${formatJourneySteps(expedition.firstLegGoal)} шагов до Суэца",
                color = ink, style = MaterialTheme.typography.bodyMedium)
        } else {
            Text("Прогресс участка пока неизвестен · цель ${formatJourneySteps(expedition.firstLegGoal)} шагов",
                color = ink, style = MaterialTheme.typography.bodyMedium)
        }
        OutlinedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Шаги сегодня · ${today.format(DateTimeFormatter.ofPattern("d MMMM", Locale.forLanguageTag("ru-RU")))}",
                    color = ink, style = MaterialTheme.typography.labelLarge)
                Text(stepsToday?.let(::formatJourneySteps) ?: "Нет данных", color = ink,
                    style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text(when {
                    stepsToday == null -> "Итог за сегодня ещё не получен. Это не ноль шагов."
                    stale -> "Последний известный итог; текущая сверка не удалась."
                    syncing -> "Сверяем с Health Connect…"
                    sync is SyncResult.Updated && sync.gaps > 0 -> "Сохранённый итог; при пробелах сверки он мог не обновиться."
                    else -> "Сохранённый итог за календарный день экспедиции."
                }, color = ink, style = MaterialTheme.typography.bodySmall)
            }
        }
        OutlinedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Следующая остановка", color = ink, style = MaterialTheme.typography.labelLarge)
                Text(next?.name ?: "Суэц достигнут", color = ink,
                    style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(if (next == null) "Первая глава пройдена."
                    else if (hasKnownProgress) "Ещё ${formatJourneySteps(next.threshold - expedition.totalSteps)} шагов по известным итогам"
                    else "Порог ${formatJourneySteps(next.threshold)} шагов; текущий путь пока неизвестен",
                    color = ink, style = MaterialTheme.typography.bodyMedium)
            }
        }
        when (sync) {
            is SyncResult.Updated -> {
                if (sync.gaps > 0) Text("Для ${sync.gaps} дневных окон нет итога. Известный путь может быть неполным.")
                if (sync.limited) Text("Ранние дни вне окна сверки; сохранённые итоги не удалены.")
            }
            SyncResult.PermissionRequired -> Text("Нет доступа к шагам. Сохранённый путь остаётся.")
            SyncResult.Unavailable -> Text("Health Connect недоступен. Сохранённый путь остаётся.")
            SyncResult.UpdateRequired -> Text("Health Connect требует обновления. Сохранённый путь остаётся.")
            SyncResult.ReadError -> Text("Не удалось прочитать шаги. Сохранённый путь остаётся.")
            null -> Unit
        }
        if (syncing) Text("Сверяем дневные шаги…")
        Button(onClick = onRefresh, enabled = !syncing) { Text("Сверить шаги") }
        Text("День считается в часовом поясе старта: ${expedition.zone.id}. Первый день — с момента старта.",
            color = ink, style = MaterialTheme.typography.bodySmall)
    }
}

internal fun formatJourneySteps(value: Long): String = "%,d".format(Locale.forLanguageTag("ru-RU"), value)
