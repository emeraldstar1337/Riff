package dev.lad.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import dev.lad.LadApplication
import dev.lad.data.ArtistPhotos
import dev.lad.data.Track

@Composable
internal fun ArtistPortrait(
    name: String,
    tracks: List<Track>,
    modifier: Modifier = Modifier,
    openSource: Boolean = false,
) {
    val context = LocalContext.current
    val enabled = LocalLadSettings.current.artistPhotos
    val repository = (context.applicationContext as? LadApplication)?.artistPhotos
    val evidenceKey = remember(tracks) { tracks.map { it.title to it.album }.distinct() }
    val photo by
        produceState<ArtistPhotos.Photo?>(null, name, evidenceKey, enabled, repository) {
            value = null
            if (enabled && repository != null) value = repository.find(name, tracks)
        }
    Box(
        modifier
            .clip(CircleShape)
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
        Artwork(tracks.firstOrNull()?.artwork, name, Modifier.fillMaxSize(), 1000)
        photo?.let {
            AsyncImage(
                it.image,
                "$name · Apple Music",
                Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }
    }
}
