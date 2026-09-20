package com.boxowl.aroundtheworld.expedition

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.boxowl.aroundtheworld.ExpeditionGold
import com.boxowl.aroundtheworld.ExpeditionMuted
import com.boxowl.aroundtheworld.ExpeditionText
import com.boxowl.aroundtheworld.expedition.scene.SeamlessJourneyCanvas
import java.time.Instant
import java.util.Locale

/**
 * «Путь» home page (P06): edge-to-edge scene dissolving into the dark shell,
 * then a centred reading hierarchy — day, one big gold number (steps today),
 * the leg line, an italic serif line and a secondary chapter-progress line.
 * Every number and route label comes from the expedition snapshot; the scene
 * stays decorative. No-data is never shown as zero.
 */
@Composable
internal fun JourneyHome(
    expedition: Expedition,
    now: Instant,
    sync: SyncResult?,
    syncing: Boolean,
    onRefresh: () -> Unit,
    scrollState: ScrollState,
) {
    val today = now.atZone(expedition.zone).toLocalDate()
    val stepsToday = expedition.dailySteps[today]
    val stale = sync is SyncResult.ReadError || sync is SyncResult.PermissionRequired ||
        sync is SyncResult.Unavailable || sync is SyncResult.UpdateRequired
    val hasKnownProgress = expedition.dailySteps.isNotEmpty()
    val next = expedition.nextStop
    val projection = FirstLegMapProjection.from(expedition)
    val current = projection.stops[projection.currentIndex].stop
    val localTime = now.atZone(expedition.zone).toLocalTime()
    val background = MaterialTheme.colorScheme.background
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val sceneHeight = maxHeight * 0.58f
        Column(
            Modifier.fillMaxSize().verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.fillMaxWidth().height(sceneHeight)) {
                SeamlessJourneyCanvas(projection, localTime, Modifier.fillMaxSize())
                // The foreground dissolves into the shell: no visible picture edge.
                Box(
                    Modifier.fillMaxWidth().height(sceneHeight * 0.32f)
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                0f to Color.Transparent,
                                1f to background,
                            ),
                        ),
                )
            }
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    "ДЕНЬ ${expedition.dayNumber(now)}",
                    color = ExpeditionText.copy(alpha = 0.85f),
                    style = MaterialTheme.typography.labelMedium,
                    letterSpacing = 4.sp,
                )
                if (stepsToday != null) {
                    Text(
                        formatJourneySteps(stepsToday),
                        color = ExpeditionGold,
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.Bold,
                        fontSize = 60.sp,
                        lineHeight = 64.sp,
                    )
                    Text(
                        "шагов сегодня",
                        color = ExpeditionGold.copy(alpha = 0.85f),
                        style = MaterialTheme.typography.labelMedium,
                        letterSpacing = 2.sp,
                    )
                } else {
                    Text(
                        "—",
                        color = ExpeditionGold,
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.Bold,
                        fontSize = 60.sp,
                        lineHeight = 64.sp,
                    )
                    Text(
                        "Нет данных за сегодня — это не ноль",
                        color = ExpeditionMuted,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "Глава 1 · Лондон → Суэц",
                    color = ExpeditionMuted,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    when {
                        next == null -> "Суэц достигнут — первая глава пройдена"
                        projection.nextIndex != null && projection.fractionToNext > 0f ->
                            "В пути: ${current.name} → ${next.name}"
                        else -> current.name
                    },
                    color = ExpeditionText,
                    fontFamily = FontFamily.Serif,
                    fontStyle = FontStyle.Italic,
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                )
                Text(
                    when {
                        next == null -> "Путь главы: ${formatJourneySteps(expedition.firstLegSteps)} " +
                            "из ${formatJourneySteps(expedition.firstLegGoal)} шагов"
                        hasKnownProgress -> "Следующая остановка — ${next.name} · ещё " +
                            "${formatJourneySteps(next.threshold - expedition.totalSteps)} шагов · " +
                            "пройдено ${formatJourneySteps(expedition.firstLegSteps)} " +
                            "из ${formatJourneySteps(expedition.firstLegGoal)}"
                        else -> "Путь главы пока неизвестен · цель " +
                            "${formatJourneySteps(expedition.firstLegGoal)} шагов"
                    },
                    color = ExpeditionMuted,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(4.dp))
                val syncError = when (sync) {
                    SyncResult.PermissionRequired ->
                        "Нет доступа к шагам. Сохранённый путь остаётся; восстановление — во вкладке «Настройки»."
                    SyncResult.Unavailable ->
                        "Health Connect недоступен. Сохранённый путь остаётся."
                    SyncResult.UpdateRequired ->
                        "Health Connect требует обновления. Сохранённый путь остаётся."
                    SyncResult.ReadError ->
                        "Не удалось прочитать шаги. Сохранённый путь остаётся."
                    else -> null
                }
                val status = when {
                    stepsToday == null || syncError != null -> null
                    stale -> "Показан последний известный итог: текущая сверка недоступна."
                    syncing -> "Сверяем с Health Connect…"
                    sync is SyncResult.Updated && sync.gaps > 0 ->
                        "Нет итога для ${sync.gaps} дневных окон; путь может быть неполным."
                    else -> null
                }
                if (status != null) {
                    Text(status, color = ExpeditionMuted, style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center)
                }
                if (syncError != null) {
                    Text(syncError, color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
                }
                TextButton(onClick = onRefresh, enabled = !syncing) {
                    Icon(JourneyIcons.Refresh, contentDescription = null, Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (syncing) "Сверяем…" else "Сверить шаги")
                }
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

internal fun formatJourneySteps(value: Long): String = "%,d".format(Locale.forLanguageTag("ru-RU"), value)
