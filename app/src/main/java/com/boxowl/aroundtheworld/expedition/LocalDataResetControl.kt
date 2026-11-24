package com.boxowl.aroundtheworld.expedition

import android.app.ActivityManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/** Uses Android's whole-app reset so the retained F01 copy cannot reimport a deleted Room DB. */
@Composable
internal fun LocalDataResetControl() {
    val context = LocalContext.current
    var showConfirmation by remember { mutableStateOf(false) }
    var confirmation by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    var requested by remember { mutableStateOf(false) }

    OutlinedButton(onClick = {
        confirmation = ""
        error = false
        showConfirmation = true
    }) { Text("Удалить все локальные данные") }
    if (error) Text("Не удалось запросить удаление данных. Они сохранены; попробуйте снова.", color = MaterialTheme.colorScheme.error)
    if (requested) Text("Запрос на удаление принят системой. Откройте приложение снова после его закрытия.")

    if (showConfirmation) {
        AlertDialog(
            onDismissRequest = { showConfirmation = false },
            title = { Text("Удалить данные приложения?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Все локальные экспедиции, дневные итоги, дневник и штампы будут удалены без восстановления. Приложение закроется. Шаги, записанные другими приложениями в Health Connect, останутся там.")
                    Text("Для подтверждения введите DELETE латинскими буквами.")
                    OutlinedTextField(
                        value = confirmation,
                        onValueChange = { confirmation = it },
                        label = { Text("Подтверждение") },
                        singleLine = true,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = confirmation == "DELETE",
                    onClick = {
                        showConfirmation = false
                        val accepted = runCatching {
                            context.getSystemService(ActivityManager::class.java)?.clearApplicationUserData() == true
                        }.getOrDefault(false)
                        requested = accepted
                        error = !accepted
                    },
                ) { Text("Удалить безвозвратно") }
            },
            dismissButton = { TextButton(onClick = { showConfirmation = false }) { Text("Отмена") } },
        )
    }
}
