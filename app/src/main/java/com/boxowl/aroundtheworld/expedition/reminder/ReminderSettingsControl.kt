package com.boxowl.aroundtheworld.expedition.reminder

import android.Manifest
import android.os.Build
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

@Composable
fun ReminderSettingsControl() {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    var enabled by remember { mutableStateOf(ReminderScheduler.isEnabled(context)) }
    var canPost by remember { mutableStateOf(ReminderScheduler.canPost(context)) }
    var error by remember { mutableStateOf(false) }

    fun refresh() {
        enabled = ReminderScheduler.isEnabled(context)
        canPost = ReminderScheduler.canPost(context)
    }

    fun turnOn() {
        error = !ReminderScheduler.setEnabled(context, true)
        refresh()
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) turnOn() else {
            error = true
            refresh()
        }
    }
    DisposableEffect(owner, context) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) refresh() }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }

    Text("Напоминание о прогулке — около 19:00 по местному времени устройства. Android может доставить его позже. По умолчанию выключено; шаги в фоне не читаются.")
    if (enabled) {
        OutlinedButton(onClick = {
            error = !ReminderScheduler.setEnabled(context, false)
            refresh()
        }) { Text("Выключить напоминание") }
        if (!canPost) Text("Уведомления заблокированы в настройках Android; напоминание не показывается.")
    } else {
        OutlinedButton(onClick = {
            error = false
            if (Build.VERSION.SDK_INT >= 33 &&
                context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            else turnOn()
        }) { Text("Включить напоминание") }
    }
    if (error) Text("Не удалось включить или выключить напоминание. Проверьте разрешение уведомлений.", color = MaterialTheme.colorScheme.error)
}
