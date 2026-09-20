package com.boxowl.aroundtheworld.debug

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.boxowl.aroundtheworld.expedition.scene.SeamlessJourneyCanvas
import com.boxowl.aroundtheworld.expedition.scene.sceneDescriptionAt
import java.time.LocalTime
import java.util.Locale
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Debug-only demo of the seamless journey world (P07). Purely in-memory: it
 * never touches Room or Health Connect and never unlocks real diary events.
 * Not exported to the launcher; start with:
 *   adb shell am start -n com.boxowl.aroundtheworld/.debug.JourneyDemoActivity
 */
class JourneyDemoActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Optional extras for scripted recordings:
        // --ef start <position> --ez auto <bool> --ez night <bool> --ei autodelay <seconds>
        // --ef back <positions> — after autodelay, jump back (downward correction demo)
        val start = intent.getFloatExtra("start", 0f).coerceIn(0f, 9f)
        val auto = intent.getBooleanExtra("auto", false)
        val night = intent.getBooleanExtra("night", false)
        val autoDelay = intent.getIntExtra("autodelay", 0)
        val back = intent.getFloatExtra("back", 0f)
        setContent { JourneyDemoScreen(start, auto, night, autoDelay, back) }
    }
}

/** Fixed visual speed of the auto pass, route positions per second. */
private const val AUTO_SPEED = 0.1f

@Composable
private fun JourneyDemoScreen(
    initialPosition: Float,
    autoStart: Boolean,
    nightStart: Boolean,
    autoDelaySec: Int = 0,
    backPositions: Float = 0f,
) {
    var target by remember { mutableStateOf(initialPosition) }
    var night by remember { mutableStateOf(nightStart) }
    val scope = rememberCoroutineScope()
    var autoJob by remember { mutableStateOf<Job?>(null) }

    fun stopAuto() {
        autoJob?.cancel()
        autoJob = null
    }

    fun autoPass() {
        stopAuto()
        autoJob = scope.launch {
            // Frame-clock driven: fixed visual speed even when recording slows frames down.
            val from = target
            val startNanos = withFrameNanos { it }
            while (target < 9f) {
                withFrameNanos { now ->
                    target = (from + (now - startNanos) / 1_000_000_000f * AUTO_SPEED).coerceAtMost(9f)
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        if (autoStart) {
            if (autoDelaySec > 0) delay(autoDelaySec * 1000L)
            autoPass()
        } else if (backPositions > 0f) {
            if (autoDelaySec > 0) delay(autoDelaySec * 1000L)
            target = (target - backPositions).coerceAtLeast(0f)
        }
    }

    MaterialTheme(
        darkColorScheme(
            primary = androidx.compose.ui.graphics.Color(0xFFD9A441),
            background = androidx.compose.ui.graphics.Color(0xFF05080C),
            surface = androidx.compose.ui.graphics.Color(0xFF0A0F14),
            onSurface = androidx.compose.ui.graphics.Color(0xFFF2EAD8),
        ),
    ) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    SeamlessJourneyCanvas(
                        targetPosition = target,
                        description = sceneDescriptionAt(target),
                        time = if (night) LocalTime.of(2, 0) else LocalTime.of(14, 0),
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        "Позиция %.2f из 9 · %s".format(Locale.US, target, sceneDescriptionAt(target)),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Slider(
                        value = target,
                        onValueChange = { stopAuto(); target = it },
                        valueRange = 0f..9f,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { autoPass() }) { Text("Автопроход") }
                        OutlinedButton(onClick = {
                            stopAuto()
                            target = (target - 3f).coerceAtLeast(0f)
                        }) { Text("Коррекция −3") }
                        OutlinedButton(onClick = { night = !night }) {
                            Text(if (night) "День" else "Ночь")
                        }
                    }
                }
            }
        }
    }
}
