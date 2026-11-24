package dev.lad.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.lad.R
import dev.lad.data.Track
import dev.lad.domain.albumKey
import dev.lad.domain.groupAlbums
import dev.lad.i18n.rememberText

@Composable
internal fun ArtistAlbums(tracks: List<Track>, onOpen: (String, List<Track>) -> Unit) {
    val strings = rememberText()
    val albums = remember(tracks) { groupAlbums(tracks) }
    Column(Modifier.fillMaxWidth().padding(vertical = 12.dp).testTag("artist-albums")) {
        Text(
            strings[R.string.albums],
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 14.dp),
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            items(albums, key = { it.first().albumKey() }) { rows ->
                val first = rows.first()
                Card(
                    onClick = { onOpen("${first.album} • ${first.artist}", rows) },
                    modifier = Modifier.width(164.dp),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(28.dp),
                    colors =
                        CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            contentColor = MaterialTheme.colorScheme.onSurface,
                        ),
                ) {
                    Column(
                        Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Artwork(
                            first.artwork,
                            first.album,
                            Modifier.fillMaxWidth().aspectRatio(1f),
                            18,
                        )
                        Text(
                            strings.metadata(first.album),
                            maxLines = 2,
                            minLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Text(
                            strings.plural(R.plurals.tracks, rows.size, rows.size),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
