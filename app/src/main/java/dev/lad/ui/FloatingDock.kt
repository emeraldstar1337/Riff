@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package dev.lad.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.automirrored.outlined.QueueMusic
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.lad.R
import dev.lad.i18n.rememberText

private fun tabMetadata(
    strings: dev.lad.i18n.TextResources
): Triple<Map<String, String>, Map<String, ImageVector>, Map<String, ImageVector>> {
    val names =
        mapOf(
            "home" to strings[R.string.home],
            "library" to strings[R.string.music],
            "playlists" to strings[R.string.collections],
            "stats" to strings[R.string.history],
        )
    val filled =
        mapOf(
            "home" to Icons.Filled.Home,
            "library" to Icons.Filled.LibraryMusic,
            "playlists" to Icons.AutoMirrored.Filled.QueueMusic,
            "stats" to Icons.Filled.Insights,
        )
    val outlined =
        mapOf(
            "home" to Icons.Outlined.Home,
            "library" to Icons.Outlined.LibraryMusic,
            "playlists" to Icons.AutoMirrored.Outlined.QueueMusic,
            "stats" to Icons.Outlined.Insights,
        )
    return Triple(names, filled, outlined)
}

@Composable
fun FloatingDock(
    tabs: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    navStyle: String = LocalLadSettings.current.navStyle,
    statsBadge: Boolean = false,
) {
    val strings = rememberText()
    val (names, filledIcons, outlinedIcons) = tabMetadata(strings)
    val isDock = navStyle == "dock"

    val navContent: @Composable RowScope.() -> Unit = {
        tabs.forEach { key ->
            val active = selected == key
            val icon =
                if (active) filledIcons[key] ?: Icons.Filled.Home
                else outlinedIcons[key] ?: Icons.Outlined.Home
            val label = names[key] ?: key
            NavigationBarItem(
                selected = active,
                onClick = { onSelect(key) },
                icon = {
                    if (key == "stats" && statsBadge && !active) {
                        BadgedBox(badge = { Badge() }) {
                            Icon(icon, null, Modifier.size(24.dp))
                        }
                    } else {
                        Icon(icon, null, Modifier.size(24.dp))
                    }
                },
                label = {
                    Text(
                        label,
                        maxLines = 1,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                    )
                },
                colors =
                    NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        selectedTextColor = MaterialTheme.colorScheme.secondary,
                        indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                alwaysShowLabel = true,
            )
        }
    }

    if (isDock) {
        Surface(
            Modifier.fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                tonalElevation = 0.dp,
                windowInsets = WindowInsets(0, 0, 0, 0),
                modifier = Modifier.fillMaxWidth().height(72.dp).clip(RoundedCornerShape(28.dp)),
                content = navContent,
            )
        }
    } else {
        // Official M3 Expressive Full-Width Flexible Navigation Bar
        NavigationBar(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            tonalElevation = 0.dp,
            modifier = Modifier.fillMaxWidth(),
            content = navContent,
        )
    }
}

@Composable
internal fun AdaptiveNavRail(
    tabs: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    statsBadge: Boolean = false,
) {
    val strings = rememberText()
    val (names, filledIcons, outlinedIcons) = tabMetadata(strings)
    NavigationRail(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxHeight(),
    ) {
        Spacer(Modifier.weight(1f))
        tabs.forEach { key ->
            val active = selected == key
            val icon =
                if (active) filledIcons[key] ?: Icons.Filled.Home
                else outlinedIcons[key] ?: Icons.Outlined.Home
            val label = names[key] ?: key
            NavigationRailItem(
                selected = active,
                onClick = { onSelect(key) },
                icon = {
                    if (key == "stats" && statsBadge && !active) {
                        BadgedBox(badge = { Badge() }) {
                            Icon(icon, null, Modifier.size(24.dp))
                        }
                    } else {
                        Icon(icon, null, Modifier.size(24.dp))
                    }
                },
                label = {
                    Text(
                        label,
                        maxLines = 1,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                    )
                },
                colors =
                    NavigationRailItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        selectedTextColor = MaterialTheme.colorScheme.secondary,
                        indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
            )
        }
        Spacer(Modifier.weight(1f))
    }
}
