package dev.lad.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import dev.lad.R
import dev.lad.data.Settings
import dev.lad.domain.LyricLine
import dev.lad.i18n.rememberText

internal data class PlayerAction(
    val title: String,
    val detail: String,
    val icon: ImageVector,
    val run: () -> Unit,
)

@Composable
internal fun ExpressiveActions(
    title: String,
    detail: String,
    onClose: () -> Unit,
    actions: List<PlayerAction>,
) {
    val strings = rememberText()
    Dialog(onDismissRequest = onClose) {
        Surface(
            shape = RoundedCornerShape(32.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            Column(
                Modifier.fillMaxWidth()
                    .heightIn(max = 560.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            title,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            detail,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClose) {
                        Icon(Icons.Default.Close, strings[R.string.close_actions])
                    }
                }
                Spacer(Modifier.height(8.dp))
                actions.forEach { action ->
                    StyleChoice(action.title, action.detail, false, action.icon, action.run)
                }
            }
        }
    }
}

@Composable
internal fun StyleChoice(
    title: String,
    description: String,
    selected: Boolean,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    val strings = rememberText()
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(if (selected) 28.dp else 20.dp),
        color =
            if (selected) MaterialTheme.colorScheme.secondaryContainer
            else MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Surface(
                shape = if (selected) RoundedCornerShape(16.dp) else CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Icon(
                    icon,
                    null,
                    Modifier.padding(12.dp).size(24.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (selected)
                Icon(
                    Icons.Default.CheckCircle,
                    strings[R.string.selection_label],
                    tint = MaterialTheme.colorScheme.primary,
                )
        }
    }
}

@Composable
internal fun SettingsNote(title: String, text: String) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun MiniStylePreview(settings: Settings) {
    val strings = rememberText()
    Surface(
        shape = RoundedCornerShape(32.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text(
                strings[R.string.preview],
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Surface(
                shape = RoundedCornerShape(26.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
            ) {
                Column(Modifier.fillMaxWidth().padding(12.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        if (settings.miniStyle != "slim")
                            Artwork(null, strings[R.string.lad], Modifier.size(48.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                strings[R.string.your_favourite_track],
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Text(
                                if (settings.miniStyle == "expanded") "1:24 / 3:42"
                                else strings[R.string.artist],
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary) {
                            Icon(
                                Icons.Default.Pause,
                                null,
                                Modifier.padding(12.dp),
                                tint = MaterialTheme.colorScheme.onPrimary,
                            )
                        }
                        Icon(Icons.Default.SkipNext, null, Modifier.size(24.dp))
                    }
                    Spacer(Modifier.height(10.dp))
                    LinearProgressIndicator(
                        progress = { 0.38f },
                        Modifier.fillMaxWidth().height(3.dp),
                    )
                }
            }
            Text(
                strings[R.string.the_preview_works_even_when_no],
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun NavigationPreview(selected: List<String>, names: Map<String, String>) {
    val strings = rememberText()
    val icons =
        mapOf(
            "home" to Icons.Default.Home,
            "library" to Icons.Default.LibraryMusic,
            "playlists" to Icons.AutoMirrored.Filled.QueueMusic,
            "stats" to Icons.Default.Insights,
        )
    Surface(
        shape = RoundedCornerShape(32.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                strings[R.string.your_navigation],
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    selected.forEachIndexed { index, key ->
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color =
                                if (index == 0) MaterialTheme.colorScheme.primaryContainer
                                else Color.Transparent,
                        ) {
                            Icon(
                                icons.getValue(key),
                                names[key],
                                Modifier.padding(12.dp).size(24.dp),
                            )
                        }
                    }
                }
            }
            Text(
                selected.joinToString(" → ") { names.getValue(it) },
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
internal fun LyricsStylePreview(settings: Settings) {
    val strings = rememberText()
    CompositionLocalProvider(LocalLadSettings provides settings.copy(animations = false)) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Column(
                Modifier.fillMaxWidth().padding(18.dp),
                verticalArrangement =
                    Arrangement.spacedBy(((settings.lyricsSpacing - 1f) * 48f).dp),
            ) {
                Text(
                    strings[R.string.lyrics_preview],
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                LyricLineText(LyricLine(0, strings[R.string.the_music_stays_with_you]), true, 0, {})
                LyricLineText(
                    LyricLine(1000, strings[R.string.every_line_follows_your_rhythm]),
                    false,
                    0,
                    {},
                )
            }
        }
    }
}

@Composable
internal fun GlowStylePreview(settings: Settings, seed: Color? = null) {
    val strings = rememberText()
    CompositionLocalProvider(LocalLadSettings provides settings.copy(animations = false)) {
        Surface(shape = RoundedCornerShape(32.dp)) {
            AmbientBackdrop(
                seed ?: MaterialTheme.colorScheme.primary,
                enabled = settings.ambientBackground,
                animations = false,
                modifier = Modifier.fillMaxWidth().height(180.dp),
            ) {
                Column(
                    Modifier.align(Alignment.Center).padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        Icons.Default.AutoAwesome,
                        null,
                        Modifier.size(44.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        strings[R.string.the_mood_of_your_music],
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}
