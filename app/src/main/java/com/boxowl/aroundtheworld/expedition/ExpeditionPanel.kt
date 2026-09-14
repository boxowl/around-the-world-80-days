package com.boxowl.aroundtheworld.expedition

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import java.time.Instant
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay

@Composable
fun ExpeditionPanel(model: ExpeditionViewModel = viewModel()) {
    val state by model.state.collectAsStateWithLifecycle()
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner, model) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) model.refresh() }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        when (val current = state) {
            JourneyState.Loading -> CircularProgressIndicator()
            JourneyState.NotStarted -> {
                Text("Лондон → Суэц · 49 000 шагов", style = MaterialTheme.typography.titleLarge)
                Text("Выберите режим. После старта шаги из Health Connect начнут двигать героя; начало и часовой пояс сохранятся на этом телефоне.")
                Button(onClick = { model.start(JourneyMode.WAGER) }) { Text("Начать пари на 80 дней") }
                OutlinedButton(onClick = { model.start(JourneyMode.FREE) }) { Text("Путешествовать без срока") }
            }
            JourneyState.StorageError -> {
                Text("Не удалось прочитать или сохранить экспедицию. Существующий файл не перезаписан новым путешествием.")
                Button(onClick = model::reload) { Text("Повторить чтение сохранения") }
            }
            is JourneyState.Active -> {
                val expedition = current.expedition
                var now by remember { mutableStateOf(Instant.now()) }
                LaunchedEffect(expedition.startedAt) { while (true) { now = Instant.now(); delay(30_000) } }
                val format = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss").withZone(expedition.zone)
                Text("Лондон → Суэц", style = MaterialTheme.typography.headlineSmall)
                Text("День ${expedition.dayNumber(now)} · ${if (expedition.mode == JourneyMode.FREE) "Свободное путешествие" else "Пари на 80 дней"}")
                Text("Старт: ${format.format(expedition.startedAt)}\nЧасовой пояс: ${expedition.zone.id}")
                if (expedition.mode == JourneyMode.WAGER) {
                    Text("Срок пари: ${format.format(expedition.deadline)}")
                    if (expedition.wagerStatus(now) == WagerStatus.EXPIRED) Text("Срок пари истёк. Путешествие и дневник сохраняются.")
                }
                if (expedition.dailySteps.isEmpty()) Text("Подтверждённых дневных итогов пока нет.")
                else Text("Известный прогресс: ${expedition.totalSteps} шагов")
                if (expedition.lastReadAt == null) Text("Успешного чтения шагов пока не было.")
                else Text("Последняя успешная сверка: ${format.format(expedition.lastReadAt)}")
                when (val sync = current.sync) {
                    is SyncResult.Updated -> {
                        if (sync.gaps > 0) Text("Нет доступного итога для ${sync.gaps} дневных окон. Известные шаги сохранены.")
                        if (sync.limited) Text("Ранние дни вне текущего окна сверки; сохранённые итоги не удалены.")
                        if (sync.gaps == 0 && !sync.limited) Text("Дневные итоги сверены с Health Connect.")
                    }
                    SyncResult.PermissionRequired -> Text("Нет разрешения на чтение шагов. Сохранённый прогресс остаётся; доступ можно выдать в разделе диагностики ниже.")
                    SyncResult.Unavailable -> Text("Health Connect недоступен. Сохранённый прогресс остаётся.")
                    SyncResult.UpdateRequired -> Text("Health Connect требует установки или обновления. Сохранённый прогресс остаётся.")
                    SyncResult.ReadError -> Text("Не удалось прочитать часть данных или сохранить сверку. Это не ноль шагов; сохранённый прогресс остаётся.")
                    null -> Unit
                }
                if (current.syncing) Text("Сверяем дневные шаги…")
                OutlinedButton(onClick = model::refresh, enabled = !current.syncing) { Text("Сверить шаги") }
                if (expedition.dailySteps.isNotEmpty()) {
                    LinearProgressIndicator(progress = { expedition.firstLegSteps.toFloat() / FIRST_LEG_GOAL }, modifier = Modifier.fillMaxWidth())
                    Text(expedition.nextStop?.let { "Следующее открытие: ${it.name} · ещё ${it.threshold - expedition.totalSteps} шагов" } ?: "Суэц достигнут. Следующая глава пока не реализована.")
                }
                HorizontalDivider()
                Text("Дневник путешествия", style = MaterialTheme.typography.titleLarge)
                FIRST_LEG.filter { it.id in expedition.unlocked }.forEach {
                    Text(it.name, style = MaterialTheme.typography.titleMedium)
                    Text(it.diary)
                }
            }
        }
    }
}
