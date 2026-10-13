package dev.lad.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import dev.lad.R
import dev.lad.i18n.rememberText

@Composable
fun FloatingDock(
    tabs: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    onSearch: () -> Unit,
) {
    val strings = rememberText()
    val names =
        mapOf(
            "home" to strings[R.string.home],
            "library" to strings[R.string.music],
            "playlists" to strings[R.string.collections],
            "stats" to strings[R.string.history],
        )
    val icons =
        mapOf(
            "home" to Icons.Default.Home,
            "library" to Icons.Default.LibraryMusic,
            "playlists" to Icons.AutoMirrored.Filled.QueueMusic,
            "stats" to Icons.Default.Insights,
        )
    Surface(
        Modifier.fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        shape = RoundedCornerShape(30.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
    ) {
        NavigationBar(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            tonalElevation = 0.dp,
            windowInsets = WindowInsets(0, 0, 0, 0),
            modifier = Modifier.fillMaxWidth().height(72.dp).clip(RoundedCornerShape(30.dp)),
        ) {
            tabs.forEach { key ->
                NavigationBarItem(
                    selected = selected == key,
                    onClick = { onSelect(key) },
                    icon = { Icon(icons.getValue(key), null, Modifier.size(23.dp)) },
                    label = {
                        Text(
                            names.getValue(key),
                            maxLines = 1,
                            style = MaterialTheme.typography.labelSmall,
                        )
                    },
                    alwaysShowLabel = true,
                )
            }
        }
    }
}
