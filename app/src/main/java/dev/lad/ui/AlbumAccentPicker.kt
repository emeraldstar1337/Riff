@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class,
)

package dev.lad.ui

import android.graphics.drawable.BitmapDrawable
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.imageLoader
import coil.request.ImageRequest
import dev.lad.R
import dev.lad.data.Track
import dev.lad.domain.albumKey
import dev.lad.i18n.rememberText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun AlbumAccentPicker(track: Track, vm: LadViewModel, onClose: () -> Unit) {
    val strings = rememberText()
    val context = LocalContext.current
    var bitmap by remember(track.artwork) { mutableStateOf<android.graphics.Bitmap?>(null) }
    var selected by remember(track.albumKey()) { mutableStateOf(vm.appearance.albumAccent(track)) }
    var failed by remember(track.artwork) { mutableStateOf(false) }
    LaunchedEffect(track.artwork) {
        bitmap =
            withContext(Dispatchers.IO) {
                (context.imageLoader
                        .execute(
                            ImageRequest.Builder(context)
                                .data(track.artwork)
                                .size(640)
                                .allowHardware(false)
                                .build()
                        )
                        .drawable as? BitmapDrawable)
                    ?.bitmap
            }
        failed = bitmap == null
    }
    ExpressiveSheet(onClose) {
        Column(
            Modifier.fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(strings[R.string.album_accent], style = MaterialTheme.typography.headlineSmall)
            Text(strings[R.string.pick_cover_pixel], style = MaterialTheme.typography.bodyMedium)
            val image = bitmap
            if (image != null) {
                Image(
                    image.asImageBitmap(),
                    strings[R.string.album_accent],
                    Modifier.fillMaxWidth().aspectRatio(1f).pointerInput(image) {
                        detectTapGestures { point ->
                            if (
                                size.width > 0 &&
                                    size.height > 0 &&
                                    image.width > 0 &&
                                    image.height > 0 &&
                                    !image.isRecycled
                            ) {
                                selected =
                                    image.getPixel(
                                        (point.x / size.width * image.width)
                                            .toInt()
                                            .coerceIn(0, image.width - 1),
                                        (point.y / size.height * image.height)
                                            .toInt()
                                            .coerceIn(0, image.height - 1),
                                    ) or 0xff000000.toInt()
                            }
                        }
                    },
                    contentScale = ContentScale.FillBounds,
                )
            } else if (failed) Text(strings[R.string.cover_unavailable])
            else CircularProgressIndicator()
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    Modifier.size(42.dp)
                        .background(
                            selected?.let { Color(it) } ?: MaterialTheme.colorScheme.primary,
                            CircleShape,
                        )
                )
                Text(
                    selected?.let { "#%06X".format(it and 0xffffff) }
                        ?: strings[R.string.automatic_color]
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    {
                        vm.appearance.setAlbumAccent(track, null)
                        onClose()
                    },
                    modifier = Modifier.weight(1f),
                    shapes = androidx.compose.material3.ButtonDefaults.shapes(),
                ) {
                    Text(strings[R.string.reset])
                }
                Button(
                    {
                        vm.appearance.setAlbumAccent(track, selected)
                        onClose()
                    },
                    modifier = Modifier.weight(1f),
                    enabled = selected != null,
                    shapes = androidx.compose.material3.ButtonDefaults.shapes(),
                ) {
                    Text(strings[R.string.save])
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
