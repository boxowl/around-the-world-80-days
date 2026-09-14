package com.boxowl.aroundtheworld

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.boxowl.aroundtheworld.health.DiagnosticsPanel
import com.boxowl.aroundtheworld.expedition.ExpeditionPanel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { ExpeditionScreen() }
    }
}

@Composable
private fun ExpeditionScreen() {
    MaterialTheme(colorScheme = lightColorScheme(
        primary = Color(0xFF254E63),
        background = Color(0xFFF5EEDD),
        surface = Color(0xFFFFF9EC),
        onBackground = Color(0xFF283E49),
        onSurface = Color(0xFF283E49),
    )) {
        Column(
            Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
                .safeDrawingPadding().verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Text(stringResource(R.string.atlas_label), style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary)
            Text(stringResource(R.string.journey_title), style = MaterialTheme.typography.displaySmall,
                fontFamily = FontFamily.Serif, color = MaterialTheme.colorScheme.onBackground)
            Text(stringResource(R.string.journey_subtitle), color = MaterialTheme.colorScheme.onBackground)
            ExpeditionPanel()
            HorizontalDivider()
            DiagnosticsPanel()
            HorizontalDivider()

        }
    }
}
