package dev.lad.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import dev.lad.R
import dev.lad.data.Track
import dev.lad.i18n.rememberText

fun duration(ms: Long): String {
    val s = ms.coerceAtLeast(0) / 1000
    return "%d:%02d".format(s / 60, s % 60)
}

@Composable
fun Artwork(uri: String?, title: String, modifier: Modifier = Modifier, round: Int = 24) {
    val strings = rememberText()
    var loaded by remember(uri) { mutableStateOf(false) }
    val style = LocalLadSettings.current
    val radius =
        when (style.coverShape) {
            "square" -> 8
            "circle" -> 1000
            else -> round
        }
    Box(
        modifier.clip(
            if (style.coverShape == "flower") FlowerShape else RoundedCornerShape(radius.dp)
        ),
        contentAlignment = Alignment.Center,
    ) {
        if (!loaded) ArtworkPlaceholder()
        if (!uri.isNullOrBlank())
            AsyncImage(
                model = uri,
                contentDescription = strings.format(R.string.artwork, title),
                modifier = Modifier.fillMaxSize(),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                onSuccess = { loaded = true },
                onError = { loaded = false },
            )
    }
}

@Composable
private fun ArtworkPlaceholder() {
    val scheme = MaterialTheme.colorScheme
    Canvas(Modifier.fillMaxSize().testTag("artwork-placeholder")) {
        val side = size.minDimension
        drawRect(
            Brush.linearGradient(
                listOf(
                    scheme.primaryContainer,
                    scheme.surfaceContainerHigh,
                    scheme.tertiaryContainer,
                ),
                end = Offset(size.width, size.height),
            )
        )
        drawRect(
            Brush.radialGradient(
                listOf(scheme.primary.copy(alpha = 0.28f), Color.Transparent),
                center = Offset(size.width * 0.8f, size.height * 0.18f),
                radius = side * 0.9f,
            )
        )
        val centre = Offset(size.width * 0.5f, size.height * 0.5f)
        drawCircle(scheme.onSurface.copy(alpha = 0.10f), side * 0.32f, centre)
        listOf(0.23f, 0.27f, 0.31f).forEach { r ->
            drawCircle(
                scheme.onSurface.copy(alpha = 0.16f),
                side * r,
                centre,
                style = Stroke(side * 0.005f),
            )
        }
        drawCircle(scheme.primary, side * 0.10f, centre)
        drawCircle(scheme.onPrimary, side * 0.024f, centre)

        drawArc(
            scheme.onSurface.copy(alpha = 0.28f),
            218f,
            50f,
            false,
            topLeft = Offset(centre.x - side * 0.285f, centre.y - side * 0.285f),
            size = androidx.compose.ui.geometry.Size(side * 0.57f, side * 0.57f),
            style = Stroke(side * 0.015f, cap = StrokeCap.Round),
        )
    }
}

@Composable
fun SectionTitle(title: String, subtitle: String? = null) {
    Column(Modifier.padding(top = 18.dp, bottom = 10.dp)) {
        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        subtitle?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun EmptyState(title: String, body: String, button: String? = null, onClick: () -> Unit = {}) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Surface(
            shape = RoundedCornerShape(40.dp),
            color = MaterialTheme.colorScheme.secondaryContainer,
            modifier = Modifier.size(100.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Default.LibraryMusic, null, Modifier.size(48.dp))
            }
        }
        Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(
            body,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        button?.let {
            Button(onClick = onClick, contentPadding = PaddingValues(24.dp, 16.dp)) { Text(it) }
        }
    }
}

val LocalTrackDetails = staticCompositionLocalOf<(Track) -> Unit> { {} }

@Composable
fun TrackRow(
    track: Track,
    active: Boolean,
    onClick: () -> Unit,
    onFavorite: () -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    val strings = rememberText()
    var menu by remember { mutableStateOf(false) }
    val details = LocalTrackDetails.current
    Row(
        modifier
            .fillMaxWidth()
            .clip(LadShapes.selected)
            .border(
                1.dp,
                if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
                else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                LadShapes.selected,
            )
            .background(
                if (active) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
            )
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Artwork(track.artwork, track.title, Modifier.size(52.dp), 18)
        Column(Modifier.weight(1f)) {
            Text(
                track.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                style = MaterialTheme.typography.titleMedium,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    strings.metadata(track.artist),
                    Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    duration(track.durationMs),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (track.favorite)
            Icon(
                Icons.Default.Favorite,
                null,
                Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        if (trailing != null) trailing()
        else
            Box {
                IconButton(onClick = { menu = true }, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Default.MoreVert, strings[R.string.track_actions])
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text(strings[R.string.track_information_and_tags]) },
                        onClick = {
                            menu = false
                            details(track)
                        },
                    )
                    DropdownMenuItem(
                        text = {
                            Text(
                                if (track.favorite) strings[R.string.remove_from_favourites]
                                else strings[R.string.add_to_favourites]
                            )
                        },
                        onClick = {
                            menu = false
                            onFavorite()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(strings[R.string.add_to_playlist]) },
                        onClick = {
                            menu = false
                            onAdd()
                        },
                    )
                }
            }
    }
}

@Composable
fun Metric(value: String, label: String, modifier: Modifier = Modifier) {
    Surface(
        modifier,
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(
                value,
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Black,
            )
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}
