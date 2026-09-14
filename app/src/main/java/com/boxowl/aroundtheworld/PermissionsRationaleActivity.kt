package com.boxowl.aroundtheworld

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

class PermissionsRationaleActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) {
                    Column(Modifier.safeDrawingPadding().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                        Text("Как используются данные", style = MaterialTheme.typography.headlineMedium)
                        Text("Приложение запрашивает только чтение шагов из Health Connect. После явного старта экспедиции оно сверяет доступные дневные итоги от момента старта, чтобы продвигать героя и открывать дневник. Диагностика отдельно показывает итоги за сегодня и семь календарных дней, включая сегодня, и названия пакетов источников.")
                        Text("Дневные итоги экспедиции, её режим, темп и открытые записи дневника сохраняются локально на этом телефоне. Мы не записываем шаги в Health Connect, не отправляем их на сервер и не используем для рекламы. Аккаунта и аналитики в этой версии нет.")
                        Text("Доступ можно отклонить или отозвать в настройках Health Connect. Приложение проверяет разрешение перед чтением и при возвращении на экран. Фонового чтения нет. Удаление приложения не удаляет исходные записи Health Connect; ими можно управлять в самом Health Connect.")
                        Text("Экспедиция использует часовой пояс, зафиксированный при старте. Диагностика использует текущий часовой пояс телефона. Отсутствие данных источника не означает ноль шагов.")
                        Button(onClick = { finish() }) { Text("Назад") }
                    }
                }
            }
        }
    }
}
