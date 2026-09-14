package com.boxowl.aroundtheworld.expedition

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import java.time.Instant
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay

@Composable
fun ExpeditionPanel(model: ExpeditionViewModel = viewModel()) {
    val state by model.state.collectAsStateWithLifecycle()
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        when (val current = state) {
            JourneyState.Loading -> CircularProgressIndicator()
            JourneyState.NotStarted -> {
                Text("Лондон → Суэц · 49 000 шагов", style = MaterialTheme.typography.titleLarge)
                Text("Выберите режим. Начало и часовой пояс сохранятся на этом телефоне. Учёт шагов путешествия подключим на следующем этапе; диагностика ниже уже доступна.")
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
                if (expedition.lastReadAt == null) Text("Экспедиция сохранена. Шаги маршрута ещё не синхронизированы.")
                else Text("Зачтено: ${expedition.totalSteps} шагов")
                LinearProgressIndicator(progress = { expedition.firstLegSteps.toFloat() / FIRST_LEG_GOAL }, modifier = Modifier.fillMaxWidth())
                Text(expedition.nextStop?.let { "Следующее открытие: ${it.name} · ещё ${it.threshold - expedition.totalSteps} шагов" } ?: "Суэц достигнут. Следующая глава пока не реализована.")
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
