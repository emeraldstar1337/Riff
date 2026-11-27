@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
)

package dev.lad.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.carousel.HorizontalMultiBrowseCarousel
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.lad.R
import dev.lad.data.Track
import dev.lad.domain.groupAlbums
import dev.lad.i18n.rememberText

@Composable
internal fun ArtistAlbums(tracks: List<Track>, onOpen: (String, List<Track>) -> Unit) {
    val strings = rememberText()
    val albums = remember(tracks) { groupAlbums(tracks) }
    if (albums.isEmpty()) return
    Column(Modifier.fillMaxWidth().padding(vertical = 12.dp).testTag("artist-albums")) {
        Text(
            strings[R.string.albums],
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 12.dp),
        )
        val carouselState = rememberCarouselState { albums.size }
        HorizontalMultiBrowseCarousel(
            state = carouselState,
            preferredItemWidth = 176.dp,
            itemSpacing = 10.dp,
            modifier = Modifier.fillMaxWidth().height(186.dp),
        ) { index ->
            val rows = albums[index]
            val first = rows.first()
            Box(
                Modifier.fillMaxSize()
                    .maskClip(RoundedCornerShape(28.dp))
                    .clickable { onOpen("${first.album} • ${first.artist}", rows) }
            ) {
                Artwork(first.artwork, first.album, Modifier.fillMaxSize(), 0)
                Box(
                    Modifier.fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                0.35f to Color.Transparent,
                                1f to Color.Black.copy(alpha = 0.78f),
                            )
                        )
                )
                Column(
                    Modifier.align(Alignment.BottomStart).padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        strings.metadata(first.album),
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        strings.plural(R.plurals.tracks, rows.size, rows.size),
                        color = Color.White.copy(alpha = 0.80f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }
    }
}
