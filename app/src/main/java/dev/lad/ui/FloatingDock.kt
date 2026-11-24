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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.lad.R
import dev.lad.i18n.rememberText

@Composable
fun FloatingDock(
    tabs: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
) {
    val strings = rememberText()
    val names =
        mapOf(
            "home" to strings[R.string.home],
            "library" to strings[R.string.music],
            "playlists" to strings[R.string.collections],
            "stats" to strings[R.string.history],
        )
    val filledIcons =
        mapOf(
            "home" to Icons.Filled.Home,
            "library" to Icons.Filled.LibraryMusic,
            "playlists" to Icons.AutoMirrored.Filled.QueueMusic,
            "stats" to Icons.Filled.Insights,
        )
    val outlinedIcons =
        mapOf(
            "home" to Icons.Outlined.Home,
            "library" to Icons.Outlined.LibraryMusic,
            "playlists" to Icons.AutoMirrored.Outlined.QueueMusic,
            "stats" to Icons.Outlined.Insights,
        )
    // M3 Expressive: tonal surface separation (surfaceContainer on surface), filled active icon vs outlined inactive
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
        ) {
            tabs.forEach { key ->
                val active = selected == key
                val icon =
                    if (active) filledIcons[key] ?: Icons.Filled.Home
                    else outlinedIcons[key] ?: Icons.Outlined.Home
                val label = names[key] ?: key
                NavigationBarItem(
                    selected = active,
                    onClick = { onSelect(key) },
                    icon = { Icon(icon, null, Modifier.size(24.dp)) },
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
    }
}
