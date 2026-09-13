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
                        Text("Вокруг света за 80 дней · диагностическая версия P02")
                        Text("Приложение запрашивает только чтение шагов из Health Connect, чтобы проверить их получение перед подключением к путешествию. Читаются итоги за сегодня и последние семь календарных дней, включая сегодня, и названия пакетов источников этих итогов.")
                        Text("Данные показываются на экране и временно находятся в памяти приложения. Мы не записываем их в Health Connect, не сохраняем в файлах, не отправляем на сервер и не используем для рекламы. Аккаунта и аналитики в этой версии нет.")
                        Text("Доступ можно отклонить или отозвать в настройках Health Connect. Приложение проверяет разрешение перед чтением и при возвращении на экран. Фонового чтения нет. Удаление приложения не удаляет исходные записи Health Connect; ими можно управлять в самом Health Connect.")
                        Text("Диагностические периоды используют текущий часовой пояс телефона. Правила будущей экспедиции будут описаны отдельно до её запуска.")
                        Button(onClick = { finish() }) { Text("Назад") }
                    }
                }
            }
        }
    }
}
