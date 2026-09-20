package com.boxowl.aroundtheworld.expedition

import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.boxowl.aroundtheworld.ExpeditionBackground
import com.boxowl.aroundtheworld.ExpeditionGold
import com.boxowl.aroundtheworld.ExpeditionMuted

private class JourneyNavItem(val id: String, val label: String, val icon: ImageVector)

private val journeyNavItems = listOf(
    JourneyNavItem("journey", "Путь", JourneyIcons.Path),
    JourneyNavItem("map", "Карта", JourneyIcons.Map),
    JourneyNavItem("diary", "Дневник", JourneyIcons.Diary),
    JourneyNavItem("health", "Настройки", JourneyIcons.Gear),
)

/**
 * Dark bottom bar in the Fantasy Hike manner: thin line icons on the shared
 * near-black surface, no indicator pill — the active item is marked by the
 * gold icon and label only.
 */
@Composable
internal fun JourneyNavBar(
    page: String,
    hasUnviewedEvents: Boolean,
    onSelect: (String) -> Unit,
) {
    Column {
        HorizontalDivider(thickness = 1.dp, color = Color.White.copy(alpha = 0.08f))
        NavigationBar(containerColor = ExpeditionBackground, tonalElevation = 0.dp) {
            journeyNavItems.forEach { item ->
                NavigationBarItem(
                    selected = page == item.id,
                    onClick = { onSelect(item.id) },
                    icon = {
                        val badge = item.id == "diary" && hasUnviewedEvents
                        if (badge) {
                            BadgedBox(badge = {
                                Badge(
                                    Modifier.semantics {
                                        contentDescription = "Есть непрочитанные записи дневника"
                                    },
                                    containerColor = ExpeditionGold,
                                )
                            }) {
                                Icon(item.icon, contentDescription = null)
                            }
                        } else {
                            Icon(item.icon, contentDescription = null)
                        }
                    },
                    label = { Text(item.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = ExpeditionGold,
                        selectedTextColor = ExpeditionGold,
                        unselectedIconColor = ExpeditionMuted,
                        unselectedTextColor = ExpeditionMuted,
                        indicatorColor = Color.Transparent,
                    ),
                )
            }
        }
    }
}
