package com.boxowl.aroundtheworld.expedition

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.boxowl.aroundtheworld.expedition.credit.V2SyncResult
import com.boxowl.aroundtheworld.expedition.credit.V2WagerCalendar
import com.boxowl.aroundtheworld.expedition.globe.GlobeStopCardsV1
import com.boxowl.aroundtheworld.expedition.globe.V2GlobeMap
import com.boxowl.aroundtheworld.expedition.passport.StampId
import com.boxowl.aroundtheworld.expedition.route.RouteMeasure
import com.boxowl.aroundtheworld.health.HealthConnectStepsGateway
import java.math.BigDecimal
import java.time.Instant
import java.time.Duration
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.delay

/** The first production v2 surface; the illustrated globe follows as a separate block. */
@Composable
internal fun V2JourneyScreen(
    current: V2JourneyState.Active,
    onRefresh: () -> Unit,
    onRestart: () -> Unit,
) {
    val snapshot = current.snapshot
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner, onRefresh) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) onRefresh()
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    val row = snapshot.expedition
    val zone = ZoneId.of(row.zone)
    var now by remember(row.id) { mutableStateOf(Instant.now()) }
    LaunchedEffect(row.id, zone) {
        while (true) {
            now = Instant.now()
            val nextMidnight = now.atZone(zone).toLocalDate().plusDays(1).atStartOfDay(zone).toInstant()
            delay(Duration.between(now, nextMidnight).toMillis().coerceAtLeast(1L))
        }
    }
    val calendar = V2WagerCalendar.evaluate(
        Instant.parse(row.startedAt), zone, JourneyMode.valueOf(row.mode), snapshot.ledger, now,
    )
    val format = remember(zone) { DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm").withZone(zone) }
    var page by rememberSaveable { mutableStateOf("path") }
    val permissionLauncher = rememberLauncherForActivityResult(
        PermissionController.createRequestPermissionResultContract(),
    ) { onRefresh() }
    val sync = current.sync
    val realKm = snapshot.ledger.totalRealMeters / 1000.0
    val bookKm = snapshot.ledger.totalBookMeters / 1000.0
    val today = now.atZone(zone).toLocalDate()
    val todaySteps = snapshot.stepsByDay[today]

    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (page) {
                "path" -> {
                    Text("Путь вокруг света", style = MaterialTheme.typography.headlineMedium)
                    Text("Расчётное реальное расстояние: ${km(realKm)} км")
                    Text("Книжный прогресс: ${preciseBookKm(bookKm)} км · маршрут ≈${String.format(Locale.US, "%.0f", RouteMeasure.totalKm())} км")
                    Text("Шаги сегодня: ${todaySteps?.toString() ?: "нет данных"}")
                    Text("Календарный день: ${calendar.dayNumber}")
                    WagerCalendarStatus(calendar, format)
                    Text("Длина шага сохранена для этой экспедиции; это оценка расстояния, а не показание GPS.")
                    SyncStatus(sync, current.syncing)
                    OutlinedButton(onClick = onRefresh, enabled = !current.syncing) { Text("Сверить шаги") }
                }
                "map" -> {
                    Text("Карта остановок", style = MaterialTheme.typography.headlineMedium)
                    V2GlobeMap(snapshot.ledger.totalRealMeters, snapshot.ledger.totalBookMeters)
                    Text("Герой и маршрут показаны по сохранённому прогрессу. Остановки расположены по измеренной геометрии.")
                    GlobeStopCardsV1.cards.forEach { card ->
                        val reached = GlobeStopCardsV1.reached(card, snapshot.ledger.totalBookMeters.toDouble())
                        Text("${if (reached) "●" else "○"} ${card.nameRu} · ${km(card.thresholdBookKm)} книжных км")
                    }
                }
                "passport" -> {
                    Text("Паспорт экспедиции", style = MaterialTheme.typography.headlineMedium)
                    Text("Штампы этой попытки сохраняются после корректировок шагов.")
                    StampId.entries.forEach { stamp ->
                        val granted = snapshot.passport.granted.containsKey(stamp)
                        val date = snapshot.passport.granted[stamp]
                        Text(
                            "${if (granted) "●" else "○"} ${stampName(stamp)} — " +
                                if (!granted) "впереди" else date?.let(format::format) ?: "дата неизвестна",
                        )
                    }
                }
                else -> {
                    Text("Настройки экспедиции", style = MaterialTheme.typography.headlineMedium)
                    Text(if (row.mode == JourneyMode.WAGER.name) "Пари на 80 календарных дней" else "Свободное путешествие")
                    Text("Старт: ${format.format(Instant.parse(row.startedAt))} · пояс ${row.zone}")
                    WagerCalendarStatus(calendar, format)
                    Text("Длина шага: ${String.format(Locale.US, "%.1f", row.strideMillimeters / 10.0)} см (зафиксирована)")
                    row.lastReadAt?.let { Text("Последняя успешная сверка: ${format.format(Instant.parse(it))}") }
                    SyncStatus(sync, current.syncing)
                    if (sync == V2SyncResult.PermissionRequired) {
                        Button(onClick = { permissionLauncher.launch(HealthConnectStepsGateway.PERMISSIONS) }) {
                            Text("Разрешить чтение шагов")
                        }
                    }
                    OutlinedButton(onClick = onRefresh, enabled = !current.syncing) { Text("Сверить шаги") }
                    OutlinedButton(onClick = onRestart) { Text("Начать новую экспедицию") }
                    Text("Прежние экспедиции и v1-данные сохраняются. Экран истории появится позднее.")
                }
            }
        }
        NavigationBar {
            listOf("path" to "Путь", "map" to "Карта", "passport" to "Паспорт", "settings" to "Настройки")
                .forEach { (id, label) ->
                    NavigationBarItem(
                        selected = page == id,
                        onClick = { page = id },
                        icon = { Text(if (page == id) "●" else "○") },
                        label = { Text(label) },
                    )
                }
        }
    }
}

@Composable
private fun WagerCalendarStatus(
    calendar: V2WagerCalendar.Result,
    format: DateTimeFormatter,
) {
    calendar.deadline?.let { Text("Срок пари: ${format.format(it)}") }
    when (calendar.status) {
        WagerStatus.ACTIVE -> Text("Пари продолжается. В зачёт срока: ${preciseBookKm(calendar.wagerBookMeters / 1000.0)} книжных км.")
        WagerStatus.WON -> Text("Пари выиграно по подтверждённым дням в пределах срока.")
        WagerStatus.EXPIRED -> Text("Срок пари истёк. Путь продолжается; поздние исправления дней в сроке могут изменить исход.")
        WagerStatus.NOT_APPLICABLE -> Text("Свободное путешествие без срока.")
    }
}

@Composable
private fun SyncStatus(sync: V2SyncResult?, syncing: Boolean) {
    when {
        syncing -> Text("Сверяем дневные шаги…")
        sync is V2SyncResult.Updated -> {
            if (sync.gaps > 0) Text("Нет итога для ${sync.gaps} дневных окон; известный путь может быть неполным.")
            if (sync.limited) Text("Ранние дни вне окна сверки; сохранённые итоги не удалены.")
        }
        sync == V2SyncResult.PermissionRequired -> Text("Нет доступа к шагам. Сохранённый путь не обнулён.")
        sync == V2SyncResult.Unavailable -> Text("Health Connect недоступен на этом устройстве.")
        sync == V2SyncResult.UpdateRequired -> Text("Для шагов требуется обновить Health Connect.")
        sync == V2SyncResult.ReadError -> Text("Не удалось прочитать шаги. Сохранённый путь не обнулён.")
    }
}

@Composable
internal fun V2StartDialog(
    hasActiveV2: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (JourneyMode, Long) -> Unit,
) {
    var mode by rememberSaveable { mutableStateOf(JourneyMode.WAGER) }
    var strideCentimeters by rememberSaveable { mutableStateOf("75") }
    val strideMillimeters = remember(strideCentimeters) {
        runCatching { BigDecimal(strideCentimeters.replace(',', '.')).movePointRight(1).longValueExact() }
            .getOrNull()?.takeIf { it > 0L }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Новая экспедиция v2") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                JourneyMode.entries.forEach { item ->
                    Row {
                        RadioButton(selected = mode == item, onClick = { mode = item })
                        Text(if (item == JourneyMode.WAGER) "Пари на 80 дней" else "Свободное путешествие")
                    }
                }
                OutlinedTextField(
                    value = strideCentimeters,
                    onValueChange = { strideCentimeters = it },
                    label = { Text("Длина шага, см") },
                    supportingText = { Text("75 см — игровое предположение; можно изменить до старта.") },
                    isError = strideMillimeters == null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                )
                Text("Шаги × выбранная длина дают расчётные километры. После старта длина не меняется.")
                Text("Пари на 80 дней при текущем балансе трудно завершить вовремя; истечение срока не удаляет путь.")
                Text(
                    if (hasActiveV2) "После подтверждения текущая v2-попытка завершится. Её шаги и штампы сохранятся, но история пока недоступна."
                    else "Данные v1 сохранятся, но после старта v2 переключение назад пока недоступно.",
                )
            }
        },
        confirmButton = {
            Button(onClick = { strideMillimeters?.let { onConfirm(mode, it) } }, enabled = strideMillimeters != null) {
                Text("Подтвердить старт")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

private fun km(value: Double): String = String.format(Locale.US, "%.1f", value)
private fun preciseBookKm(value: Double): String = String.format(Locale.US, "%.3f", value)

private fun stampName(stamp: StampId): String = when (stamp) {
    StampId.LONDON_START -> "Лондон · старт"
    StampId.SUEZ_FINISH -> "Суэц"
    StampId.BOMBAY_FINISH -> "Бомбей"
    StampId.CALCUTTA_FINISH -> "Калькутта"
    StampId.HONG_KONG_FINISH -> "Гонконг"
    StampId.YOKOHAMA_FINISH -> "Иокогама"
    StampId.SAN_FRANCISCO_FINISH -> "Сан-Франциско"
    StampId.NEW_YORK_FINISH -> "Нью-Йорк"
    StampId.LONDON_FINISH -> "Лондон · финиш"
}
