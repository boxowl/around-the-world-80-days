package com.boxowl.aroundtheworld.expedition

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

private val navPaper = Color(0xFFFFF9EC)
private val navInk = Color(0xFF283E49)
private val navTerracotta = Color(0xFFA4412C)
private val navOnTerracotta = Color(0xFFFFF9EC)

private class JourneyNavItem(val id: String, val label: String, val icon: ImageVector)

private val journeyNavItems = listOf(
    JourneyNavItem("journey", "Путь", JourneyIcons.Path),
    JourneyNavItem("map", "Карта", JourneyIcons.Map),
    JourneyNavItem("diary", "Дневник", JourneyIcons.Diary),
    JourneyNavItem("health", "Шаги и доступ", JourneyIcons.Steps),
)

/** Light atlas-paper bottom bar, intentionally contrasting the dark «Путь» page. */
@Composable
internal fun JourneyNavBar(
    page: String,
    hasUnviewedEvents: Boolean,
    onSelect: (String) -> Unit,
) {
    Column(Modifier.navigationBarsPadding()) {
        HorizontalDivider(thickness = 1.dp, color = navInk.copy(alpha = 0.15f))
        NavigationBar(containerColor = navPaper, tonalElevation = 0.dp) {
            journeyNavItems.forEach { item ->
                NavigationBarItem(
                    selected = page == item.id,
                    onClick = { onSelect(item.id) },
                    icon = {
                        val badge = item.id == "diary" && hasUnviewedEvents
                        if (badge) {
                            BadgedBox(badge = { Badge() }) {
                                Icon(item.icon, contentDescription = null)
                            }
                        } else {
                            Icon(item.icon, contentDescription = null)
                        }
                    },
                    label = { Text(item.label) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = navOnTerracotta,
                        selectedTextColor = navInk,
                        unselectedIconColor = navInk,
                        unselectedTextColor = navInk,
                        indicatorColor = navTerracotta,
                    ),
                )
            }
        }
    }
}
