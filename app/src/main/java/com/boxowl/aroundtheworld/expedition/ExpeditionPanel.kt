package com.boxowl.aroundtheworld.expedition

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.boxowl.aroundtheworld.PermissionsRationaleActivity
import com.boxowl.aroundtheworld.health.HealthConnectStepsGateway
import java.time.Instant
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay

@Composable
fun ExpeditionPanel(model: ExpeditionViewModel = viewModel()) {
    val state by model.state.collectAsStateWithLifecycle()
    val access by model.access.collectAsStateWithLifecycle()
    val owner = LocalLifecycleOwner.current
    val context = LocalContext.current
    var selectedModeName by rememberSaveable { mutableStateOf(JourneyMode.WAGER.name) }
    var selectedPace by rememberSaveable { mutableIntStateOf(BASE_PACE) }
    var actionError by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        PermissionController.createRequestPermissionResultContract(),
    ) {
        model.checkAccess()
        model.refresh()
    }
    DisposableEffect(owner, model) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                model.checkAccess()
                model.refresh()
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    fun open(intent: Intent) {
        actionError = false
        try { context.startActivity(intent) }
        catch (_: ActivityNotFoundException) { actionError = true }
        catch (_: SecurityException) { actionError = true }
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        when (val current = state) {
            JourneyState.Loading -> CircularProgressIndicator()
            JourneyState.NotStarted -> {
                Text("Настройка экспедиции", style = MaterialTheme.typography.titleLarge)
                Text("Выберите режим и темп до старта. Отсчёт шагов начнётся с момента нажатия «Начать экспедицию».")
                JourneyMode.entries.forEach { mode ->
                    Row {
                        RadioButton(selected = selectedModeName == mode.name, onClick = { selectedModeName = mode.name })
                        Text(if (mode == JourneyMode.WAGER) "Пари на 80 календарных дней" else "Свободное путешествие без срока")
                    }
                }
                if (selectedModeName == JourneyMode.WAGER.name) Text("Срок рассчитывается по часовому поясу на момент старта. Опоздание не удаляет путь или дневник.")
                Text("Темп, шагов в день", style = MaterialTheme.typography.titleMedium)
                PACES.forEach { pace ->
                    Row {
                        RadioButton(selected = selectedPace == pace, onClick = { selectedPace = pace })
                        Text("${formatSteps(pace.toLong())} шагов")
                    }
                }
                Text("Полный маршрут: ${formatSteps(selectedPace * 80L)} шагов · Лондон → Суэц: ${formatSteps(selectedPace * 7L)} шагов.")
                Text("Ближайшее событие: «За лондонскими крышами» — ${formatSteps(scaledThreshold(1_000, selectedPace * 80L))} шагов.")
                Text("Цель фиксируется при старте; будущая смена темпа не передвинет текущую экспедицию.")
                StepAccessPanel(access, actionError,
                    request = {
                        actionError = false
                        try { permissionLauncher.launch(HealthConnectStepsGateway.PERMISSIONS) }
                        catch (_: ActivityNotFoundException) { actionError = true }
                        catch (_: SecurityException) { actionError = true }
                    }, open = ::open, retry = model::checkAccess)
                Button(onClick = { model.start(JourneyMode.valueOf(selectedModeName), selectedPace) }) {
                    Text("Начать экспедицию")
                }
                Text("Можно начать без доступа. Подключите шаги позже: выбранные режим и темп сохранятся.")
            }
            JourneyState.StorageError -> {
                Text("Не удалось прочитать или сохранить экспедицию. Существующая история не сброшена.")
                Button(onClick = model::reload) { Text("Повторить чтение сохранения") }
            }
            is JourneyState.Active -> {
                val expedition = current.expedition
                var now by remember { mutableStateOf(Instant.now()) }
                LaunchedEffect(expedition.startedAt) { while (true) { now = Instant.now(); delay(30_000) } }
                val format = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss").withZone(expedition.zone)
                Text("Лондон → Суэц", style = MaterialTheme.typography.headlineSmall)
                Text("День ${expedition.dayNumber(now)} · ${if (expedition.mode == JourneyMode.FREE) "Свободное путешествие" else "Пари на 80 дней"}")
                Text("Темп: ${formatSteps(expedition.paceStepsPerDay.toLong())} шагов/день · цель: ${formatSteps(expedition.worldGoal)} шагов")
                Text("Первый участок: ${formatSteps(expedition.firstLegGoal)} шагов. ${expedition.goalExplanation}")
                Text("Старт: ${format.format(expedition.startedAt)}\nЧасовой пояс: ${expedition.zone.id}")
                if (expedition.mode == JourneyMode.WAGER) {
                    Text("Срок пари: ${format.format(expedition.deadline)}")
                    if (expedition.wagerStatus(now) == WagerStatus.EXPIRED) Text("Срок пари истёк. Путешествие и дневник сохраняются.")
                }
                if (expedition.dailySteps.isEmpty()) Text("Подтверждённых дневных итогов пока нет.")
                else Text("Известный прогресс: ${formatSteps(expedition.totalSteps)} шагов")
                if (expedition.lastReadAt == null) Text("Успешного чтения шагов пока не было.")
                else Text("Последняя успешная сверка: ${format.format(expedition.lastReadAt)}")
                when (val sync = current.sync) {
                    is SyncResult.Updated -> {
                        if (sync.gaps > 0) Text("Нет доступного итога для ${sync.gaps} дневных окон. Известные шаги сохранены.")
                        if (sync.limited) Text("Ранние дни вне текущего окна сверки; сохранённые итоги не удалены.")
                        if (sync.gaps == 0 && !sync.limited) Text("Дневные итоги сверены с Health Connect.")
                    }
                    SyncResult.PermissionRequired -> Text("Доступ к шагам отсутствует. Сохранённый прогресс остаётся.")
                    SyncResult.Unavailable -> Text("Health Connect недоступен. Сохранённый прогресс остаётся.")
                    SyncResult.UpdateRequired -> Text("Health Connect требует установки или обновления. Сохранённый прогресс остаётся.")
                    SyncResult.ReadError -> Text("Не удалось прочитать шаги. Это не ноль шагов; сохранённый прогресс остаётся.")
                    null -> Unit
                }
                StepAccessPanel(access, actionError,
                    request = {
                        actionError = false
                        try { permissionLauncher.launch(HealthConnectStepsGateway.PERMISSIONS) }
                        catch (_: ActivityNotFoundException) { actionError = true }
                        catch (_: SecurityException) { actionError = true }
                    }, open = ::open, retry = model::checkAccess)
                if (current.syncing) Text("Сверяем дневные шаги…")
                OutlinedButton(onClick = model::refresh, enabled = !current.syncing) { Text("Сверить шаги") }
                LinearProgressIndicator(progress = { expedition.firstLegSteps.toFloat() / expedition.firstLegGoal }, modifier = Modifier.fillMaxWidth())
                Text(expedition.nextStop?.let { "Следующее открытие: ${it.name} · ещё ${formatSteps(it.threshold - expedition.totalSteps)} шагов" } ?: "Суэц достигнут. Следующая глава пока не реализована.")
                HorizontalDivider()
                Text("Дневник путешествия", style = MaterialTheme.typography.titleLarge)
                expedition.stops.filter { it.id in expedition.unlocked }.forEach {
                    Text(it.name, style = MaterialTheme.typography.titleMedium)
                    Text(it.diary)
                }
            }
        }
    }
}

@Composable
private fun StepAccessPanel(
    access: StepAccess,
    actionError: Boolean,
    request: () -> Unit,
    open: (Intent) -> Unit,
    retry: () -> Unit,
) {
    val context = LocalContext.current
    Text("Доступ к шагам", style = MaterialTheme.typography.titleMedium)
    Text("Health Connect нужен только для чтения шагов. Мы сохраняем дневные итоги на этом телефоне; данные могут появляться с задержкой или отсутствовать у источника.")
    when (access) {
        StepAccess.Checking -> Text("Проверяем доступ…")
        StepAccess.Granted -> {
            Text("Чтение шагов разрешено. Если данных пока нет, проверьте источник в Health Connect.")
            TextButton(onClick = { open(Intent(HealthConnectClient.ACTION_HEALTH_CONNECT_SETTINGS)) }) { Text("Управлять доступом в Health Connect") }
        }
        StepAccess.PermissionRequired -> {
            Text("Доступ к шагам не выдан или был отозван. Можно продолжить экспедицию и запросить его позже.")
            Button(onClick = request) { Text("Разрешить чтение шагов") }
            TextButton(onClick = { open(Intent(HealthConnectClient.ACTION_HEALTH_CONNECT_SETTINGS)) }) { Text("Управлять доступом в Health Connect") }
        }
        StepAccess.Unavailable -> Text("Health Connect недоступен на этом устройстве. Экспедицию можно начать, но шаги пока не будут поступать.")
        StepAccess.UpdateRequired -> {
            Text("Нужно установить или обновить Health Connect. После обновления вернитесь и проверьте доступ.")
            Button(onClick = { open(if (Build.VERSION.SDK_INT >= 34) Intent(Settings.ACTION_SETTINGS) else Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=com.google.android.apps.healthdata"))) }) { Text(if (Build.VERSION.SDK_INT >= 34) "Открыть настройки" else "Установить Health Connect") }
        }
        StepAccess.ReadError -> {
            Text("Не удалось проверить доступ. Попробуйте ещё раз; сохранённая экспедиция останется.")
            OutlinedButton(onClick = retry) { Text("Проверить доступ") }
        }
    }
    TextButton(onClick = { open(Intent(context, PermissionsRationaleActivity::class.java)) }) { Text("Как используются данные") }
    if (actionError) Text("Не удалось открыть системный экран. Откройте Health Connect через настройки телефона.", color = MaterialTheme.colorScheme.error)
}

private fun formatSteps(value: Long): String = "%,d".format(java.util.Locale.forLanguageTag("ru-RU"), value)
