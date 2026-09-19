package com.boxowl.aroundtheworld

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.boxowl.aroundtheworld.expedition.ExpeditionPanel

/** Shared dark-shell design tokens (P06): near-black background, restrained gold accent. */
internal val ExpeditionBackground = Color(0xFF05080C)
internal val ExpeditionSurface = Color(0xFF0A0F14)
internal val ExpeditionSurfaceRaised = Color(0xFF121A22)
internal val ExpeditionGold = Color(0xFFD9A441)
internal val ExpeditionGoldDeep = Color(0xFFC9A227)
internal val ExpeditionText = Color(0xFFF2EAD8)
internal val ExpeditionMuted = Color(0xFF9AA3AD)
internal val ExpeditionLine = Color(0xFF2C3640)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { ExpeditionScreen() }
    }
}

@Composable
private fun ExpeditionScreen() {
    MaterialTheme(colorScheme = darkColorScheme(
        primary = ExpeditionGold,
        onPrimary = Color(0xFF241A02),
        background = ExpeditionBackground,
        onBackground = ExpeditionText,
        surface = ExpeditionSurface,
        onSurface = ExpeditionText,
        surfaceVariant = ExpeditionSurfaceRaised,
        onSurfaceVariant = ExpeditionMuted,
        outline = ExpeditionLine,
    )) {
        Column(
            Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
                .safeDrawingPadding(),
        ) {
            ExpeditionPanel()

        }
    }
}
