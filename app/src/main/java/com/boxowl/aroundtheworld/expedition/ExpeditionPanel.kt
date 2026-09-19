package com.boxowl.aroundtheworld.expedition

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.boxowl.aroundtheworld.ExpeditionMuted
import com.boxowl.aroundtheworld.ExpeditionSurface
import com.boxowl.aroundtheworld.ExpeditionText
import com.boxowl.aroundtheworld.PermissionsRationaleActivity
import com.boxowl.aroundtheworld.health.DiagnosticsPanel
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
    var showPrestartDiagnostics by rememberSaveable { mutableStateOf(false) }
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
    val requestPermissions: () -> Unit = {
        actionError = false
        try { permissionLauncher.launch(HealthConnectStepsGateway.PERMISSIONS) }
        catch (_: ActivityNotFoundException) { actionError = true }
        catch (_: SecurityException) { actionError = true }
    }
    val current = state
    if (current is JourneyState.Active) {
        ActiveExpedition(current, access, actionError, requestPermissions, ::open,
            model::refresh, model::checkAccess, model::markEventViewed)
    } else {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (current) {
                JourneyState.Loading -> CircularProgressIndicator()
                JourneyState.NotStarted -> {
                    Text("Настройка экспедиции", style = MaterialTheme.typography.titleLarge,
                        fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold)
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
                        request = requestPermissions, open = ::open, retry = model::checkAccess)
                    Button(onClick = { model.start(JourneyMode.valueOf(selectedModeName), selectedPace) }) {
                        Text("Начать экспедицию")
                    }
                    Text("Можно начать без доступа. Подключите шаги позже: выбранные режим и темп сохранятся.")
                }
                JourneyState.StorageError -> {
                    Text("Не удалось прочитать или сохранить экспедицию. Существующая история не сброшена.")
                    Button(onClick = model::reload) { Text("Повторить чтение сохранения") }
                }
                is JourneyState.Active -> Unit
            }
            TextButton(onClick = { showPrestartDiagnostics = !showPrestartDiagnostics }) {
                Text(if (showPrestartDiagnostics) "Скрыть диагностику шагов" else "Диагностика шагов")
            }
            if (showPrestartDiagnostics) DiagnosticsPanel()
        }
    }
}

@Composable
private fun ActiveExpedition(
    current: JourneyState.Active,
    access: StepAccess,
    actionError: Boolean,
    requestPermissions: () -> Unit,
    open: (Intent) -> Unit,
    onRefresh: () -> Unit,
    onCheckAccess: () -> Unit,
    onEventViewed: (String) -> Unit,
) {
    val expedition = current.expedition
    val newEvent = expedition.stops.firstOrNull { it.id in current.unviewedEventIds }
    val owner = LocalLifecycleOwner.current
    var now by remember { mutableStateOf(Instant.now()) }
    // The clock tick (and thus scene animations) stops while the app is not visible.
    LaunchedEffect(expedition.startedAt, owner) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) { now = Instant.now(); delay(30_000) }
        }
    }
    val format = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss").withZone(expedition.zone)
    var page by rememberSaveable { mutableStateOf("journey") }
    var dismissedEventIds by rememberSaveable { mutableStateOf(listOf<String>()) }
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            val shownEvent = newEvent?.takeIf { page != "diary" && it.id !in dismissedEventIds }
            when (page) {
                "journey" -> JourneyHome(expedition, now, current.sync, current.syncing, onRefresh)
                "map" -> Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (shownEvent != null) {
                        NewEventNotification(
                            name = shownEvent.name,
                            onOpen = { page = "diary" },
                            onDismiss = { dismissedEventIds = dismissedEventIds + shownEvent.id },
                        )
                    }
                    FirstLegMap(expedition, onOpenDiary = { page = "diary" })
                }
                "diary" -> Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text("Дневник путешествия", style = MaterialTheme.typography.titleLarge,
                        fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold)
                    Text("Открытые записи остаются здесь, даже если источник шагов позже скорректирует итог.",
                        color = ExpeditionMuted)
                    expedition.stops.filter { it.id in expedition.unlocked }.forEach { stop ->
                        ElevatedCard(
                            Modifier.fillMaxWidth(),
                            colors = CardDefaults.elevatedCardColors(containerColor = ExpeditionSurface),
                        ) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(stop.name, style = MaterialTheme.typography.titleMedium,
                                    fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold)
                                Text(stop.diary, color = ExpeditionText.copy(alpha = 0.9f))
                                if (stop.id in current.unviewedEventIds) {
                                    Text("Новая запись", color = MaterialTheme.colorScheme.primary,
                                        style = MaterialTheme.typography.labelLarge)
                                    OutlinedButton(onClick = { onEventViewed(stop.id) }) {
                                        Text("Отметить прочитанной")
                                    }
                                    if (current.eventActionError) {
                                        Text("Не удалось сохранить отметку. Запись останется новой; попробуйте ещё раз.",
                                            color = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }
                    }
                }
                else -> Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (shownEvent != null) {
                        NewEventNotification(
                            name = shownEvent.name,
                            onOpen = { page = "diary" },
                            onDismiss = { dismissedEventIds = dismissedEventIds + shownEvent.id },
                        )
                    }
                    Text("Настройки", style = MaterialTheme.typography.titleLarge,
                        fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold)
                    Text("${if (expedition.mode == JourneyMode.FREE) "Свободное путешествие" else "Пари на 80 дней"} · темп ${formatSteps(expedition.paceStepsPerDay.toLong())} шагов/день")
                    Text("Старт: ${format.format(expedition.startedAt)} · пояс ${expedition.zone.id}")
                    Text("День считается в часовом поясе старта. Первый день — с момента старта.")
                    if (expedition.mode == JourneyMode.WAGER) {
                        Text("Срок пари: ${format.format(expedition.deadline)}")
                        if (expedition.wagerStatus(now) == WagerStatus.EXPIRED) Text("Срок пари истёк. Путешествие и дневник сохраняются.")
                    }
                    if (expedition.lastReadAt != null) Text("Последняя успешная сверка: ${format.format(expedition.lastReadAt)}")
                    (current.sync as? SyncResult.Updated)?.let { sync ->
                        if (sync.gaps > 0) Text("Для ${sync.gaps} дневных окон нет итога. Известный путь может быть неполным.")
                        if (sync.limited) Text("Ранние дни вне окна сверки; сохранённые итоги не удалены.")
                    }
                    StepAccessPanel(access, actionError,
                        request = requestPermissions, open = open, retry = onCheckAccess)
                    if (current.syncing) Text("Сверяем дневные шаги…")
                    OutlinedButton(onClick = onRefresh, enabled = !current.syncing) { Text("Сверить шаги") }
                    HorizontalDivider()
                    DiagnosticsPanel()
                }
            }
            if (page == "journey" && shownEvent != null) {
                NewEventNotification(
                    name = shownEvent.name,
                    onOpen = { page = "diary" },
                    onDismiss = { dismissedEventIds = dismissedEventIds + shownEvent.id },
                    modifier = Modifier.align(Alignment.TopCenter).padding(12.dp),
                )
            }
        }
        JourneyNavBar(page, current.unviewedEventIds.isNotEmpty(), onSelect = { page = it })
    }
}

/**
 * Compact new-event notice (P06): an invitation to open the diary, not a banner
 * card. Dismissing it only hides the notice for this session — the entry stays
 * unviewed (the nav badge remains) until «Отметить прочитанной» in the diary.
 */
@Composable
private fun NewEventNotification(
    name: String,
    onOpen: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)),
    ) {
        Row(
            Modifier.padding(start = 14.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(8.dp).background(MaterialTheme.colorScheme.primary, CircleShape),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "Новое событие · $name",
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            TextButton(onClick = onOpen) { Text("Читать") }
            IconButton(onClick = onDismiss, Modifier.size(36.dp)) {
                Icon(JourneyIcons.Close, contentDescription = "Скрыть уведомление о новом событии",
                    Modifier.size(16.dp))
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
