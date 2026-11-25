package dev.lad.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import coil.request.ImageRequest
import dev.lad.LadApplication
import dev.lad.data.ArtistPhotos
import dev.lad.data.Track

@Composable
internal fun ArtistPortrait(
    name: String,
    tracks: List<Track>,
    modifier: Modifier = Modifier,
    openSource: Boolean = false,
    round: Int = 20,
    fallbackArtwork: String? = null,
    shapeOverride: androidx.compose.ui.graphics.Shape? = null,
) {
    val context = LocalContext.current
    val settings = LocalLadSettings.current
    val enabled = settings.artistPhotos
    val repository = (context.applicationContext as? LadApplication)?.artistPhotos
    val evidenceKey = remember(tracks) { tracks.map { it.title to it.album }.distinct() }
    val cached = remember(name, enabled, repository) { if (enabled) repository?.peek(name) else null }
    val photo by
        produceState<ArtistPhotos.Photo?>(cached, name, evidenceKey, enabled, repository) {
            if (!enabled || repository == null) {
                value = null
            } else {
                if (value == null) value = repository.peek(name)
                value = repository.find(name, tracks)
            }
        }
    val clipShape = shapeOverride ?: coverClipShape(settings.coverShape, round)
    Box(
        modifier
            .clip(clipShape)
            .then(
                if (openSource && photo != null)
                    Modifier.clickable {
                        photo?.let {
                            runCatching {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse(it.page))
                                )
                            }
                        }
                    }
                else Modifier
            )
    ) {
        Artwork(fallbackArtwork ?: tracks.firstOrNull()?.artwork, name, Modifier.fillMaxSize(), round)
        photo?.let {
            AsyncImage(
                model =
                    ImageRequest.Builder(context)
                        .data(it.image)
                        .crossfade(if (settings.animations) 220 else 0)
                        .build(),
                contentDescription = name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }
    }
}
