package com.boxowl.aroundtheworld.health

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.boxowl.aroundtheworld.PermissionsRationaleActivity
import java.time.format.DateTimeFormatter

@Composable
fun DiagnosticsPanel(model: DiagnosticsViewModel = viewModel()) {
    val state by model.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val density = LocalDensity.current
    var previousHeight by remember { mutableStateOf(0.dp) }
    val owner = LocalLifecycleOwner.current
    var actionError by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        PermissionController.createRequestPermissionResultContract(),
    ) { model.refresh() }
    DisposableEffect(owner, model) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) model.refresh()
            if (event == Lifecycle.Event.ON_STOP) model.clear()
        }
        owner.lifecycle.addObserver(observer)
        if (owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) model.refresh()
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    fun open(intent: Intent) {
        actionError = false
        try { context.startActivity(intent) }
        catch (_: ActivityNotFoundException) { actionError = true }
        catch (_: SecurityException) { actionError = true }
    }
    Column(
        modifier = Modifier.heightIn(min = if (state == DiagnosticState.Loading) previousHeight else 0.dp)
            .onSizeChanged { size ->
                if (state != DiagnosticState.Loading) previousHeight = with(density) { size.height.toDp() }
            },
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Шаги · проверка подключения", style = MaterialTheme.typography.titleLarge)
        Text("Читаем только число шагов. Данные остаются на телефоне; в этой версии они ещё не двигают героя и не сохраняются приложением.")
        when (val current = state) {
            DiagnosticState.Loading -> { CircularProgressIndicator(); Text("Проверяем доступ и читаем шаги…") }
            DiagnosticState.Unavailable -> Text("Health Connect недоступен на этом устройстве. Проверим другой источник шагов после испытания телефона.")
            DiagnosticState.UpdateRequired -> {
                Text("Нужно установить или обновить Health Connect. На Android 14 и выше проверьте также системные обновления.")
                Button(onClick = { open(if (android.os.Build.VERSION.SDK_INT >= 34) Intent(Settings.ACTION_SETTINGS) else Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=com.google.android.apps.healthdata"))) }) {
                    Text(if (android.os.Build.VERSION.SDK_INT >= 34) "Открыть настройки" else "Установить Health Connect")
                }
            }
            DiagnosticState.PermissionRequired -> {
                Text("Разрешите чтение шагов в Health Connect. После отказа можно попробовать снова или изменить доступ в настройках.")
                Button(onClick = {
                    actionError = false
                    try { permissionLauncher.launch(HealthConnectStepsGateway.PERMISSIONS) }
                    catch (_: ActivityNotFoundException) { actionError = true }
                    catch (_: SecurityException) { actionError = true }
                }) { Text("Разрешить чтение шагов") }
            }
            DiagnosticState.ReadError -> Text("Не удалось прочитать шаги. Проверьте Health Connect и повторите обновление. Ошибка не означает ноль шагов.")
            is DiagnosticState.Ready -> {
                val snapshot = current.snapshot
                Text("Сегодня: ${snapshot.today.count?.let { "$it шагов" } ?: "нет данных"}", style = MaterialTheme.typography.headlineSmall)
                Text("За 7 календарных дней, включая сегодня: ${snapshot.week.count?.let { "$it шагов" } ?: "нет данных"}")
                if (snapshot.today.count == null) Text("Источник пока не передал записи за сегодня. Проверьте сбор шагов и синхронизацию в Health Connect.")
                val format = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss").withZone(snapshot.zone)
                Text("Прочитано: ${format.format(snapshot.todayWindow.end)}\nЧасовой пояс: ${snapshot.zone.id}\nПериод 7 дней: с ${format.format(snapshot.weekWindow.start)}")
                Text("Источники итога за 7 дней: ${snapshot.week.origins.sorted().joinToString().ifEmpty { "не указаны провайдером" }}", style = MaterialTheme.typography.bodySmall)
                Text("Повторное чтение заменяет итог. Возможны задержки синхронизации и исправления данных источником.")
            }
        }
        OutlinedButton(onClick = { actionError = false; model.refresh() }, enabled = state != DiagnosticState.Loading) { Text("Обновить") }
        if (state != DiagnosticState.Unavailable && state != DiagnosticState.UpdateRequired && state != DiagnosticState.Loading) {
            TextButton(onClick = { open(HealthConnectClient.getHealthConnectManageDataIntent(context)) }) { Text("Настройки Health Connect") }
        }
        TextButton(onClick = { open(Intent(context, PermissionsRationaleActivity::class.java)) }) { Text("Как используются данные") }
        if (actionError) Text("Не удалось открыть системный экран. Откройте Health Connect через настройки телефона.", color = MaterialTheme.colorScheme.error)
    }
}
