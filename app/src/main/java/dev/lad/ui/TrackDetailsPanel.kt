@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package dev.lad.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.lad.R
import dev.lad.data.*
import dev.lad.i18n.rememberText
import dev.lad.util.attempt
import kotlinx.coroutines.launch

@Composable
fun TrackDetailsPanel(track: Track, history: List<Listen>, vm: LadViewModel, onClose: () -> Unit) {
    val strings = rememberText()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var details by remember(track.id) { mutableStateOf<FileDetails?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var editedTrack by remember(track.id) { mutableStateOf(track) }
    var coverForAlbum by remember { mutableStateOf(false) }
    val coverPicker =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null)
                scope.launch {
                    attempt {
                        context.contentResolver.takePersistableUriPermission(
                            uri,
                            android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,
                        )
                        if (coverForAlbum) vm.saveAlbumCover(track.albumId, uri.toString())
                        else
                            vm.saveEdit(
                                editedTrack,
                                TrackEdit(
                                    track.id,
                                    editedTrack.title,
                                    editedTrack.artist,
                                    editedTrack.album,
                                    editedTrack.year,
                                    editedTrack.genre,
                                    uri.toString(),
                                ),
                            )
                        editedTrack = editedTrack.copy(customArtwork = uri.toString())
                    }
                        .onFailure { error = it.message }
                }
        }
    val exporter =
        rememberLauncherForActivityResult(
            ActivityResultContracts.CreateDocument(track.mimeType.ifBlank { "audio/mpeg" })
        ) { uri ->
            if (uri != null)
                scope.launch {
                    busy = true
                    attempt { vm.mediaTools.exportTags(editedTrack, uri) }
                        .onSuccess { error = strings[R.string.a_copy_with_updated_tags_has] }
                        .onFailure { error = it.message }
                    busy = false
                }
        }
    LaunchedEffect(track.id) {
        attempt { vm.mediaTools.details(track) }
            .onSuccess { details = it }
            .onFailure { error = it.message }
    }
    val rows = remember(history, track.id) { history.filter { it.trackId == track.id } }
    PlayerSheet(strings[R.string.track_information], "", "details", onClose) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Artwork(editedTrack.artwork, editedTrack.title, Modifier.size(82.dp))
                Column {
                    Text(
                        strings.metadata(editedTrack.title),
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Text(strings.metadata(editedTrack.artist))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Metric(duration(track.durationMs), strings[R.string.duration], Modifier.weight(1f))
                Metric(
                    track.year.takeIf { it > 0 }?.toString() ?: "—",
                    strings[R.string.year],
                    Modifier.weight(1f),
                )
            }
            InfoTile(strings[R.string.album], editedTrack.album)
            SectionTitle(strings[R.string.your_history])
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Metric(
                    rows.count { it.counted }.toString(),
                    strings[R.string.track_details_panel_plays],
                    Modifier.weight(1f),
                )
                Metric(
                    duration(rows.sumOf { it.listenedMs }),
                    strings[R.string.total_listening_time],
                    Modifier.weight(1f),
                )
            }
            SectionTitle(strings[R.string.audio_file])
            InfoTile(
                strings[R.string.format],
                details?.mime?.ifBlank { track.mimeType }
                    ?: track.mimeType.ifBlank { strings[R.string.loading] },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                InfoTile(
                    strings[R.string.bitrate],
                    details?.bitrate?.takeIf { it > 0 }?.let { "${it / 1000} kbps" } ?: "—",
                    Modifier.weight(1f),
                )
                InfoTile(
                    strings[R.string.sample_rate],
                    details?.sampleRate?.takeIf { it.isNotBlank() }?.let { "$it Hz" } ?: "—",
                    Modifier.weight(1f),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                InfoTile(
                    strings[R.string.bit_depth],
                    details?.bits?.takeIf { it.isNotBlank() }?.let { "$it bit" } ?: "—",
                    Modifier.weight(1f),
                )
                InfoTile(
                    strings[R.string.size],
                    strings.format(R.string.mb, (details?.size ?: track.fileSize) / 1_048_576.0),
                    Modifier.weight(1f),
                )
            }
            InfoTile(
                strings[R.string.channels],
                details?.channels?.let {
                    when (it) {
                        "1" -> strings[R.string.mono]
                        "2" -> strings[R.string.stereo]
                        "" -> "—"
                        else -> it
                    }
                } ?: "—",
            )
            InfoTile(
                strings[R.string.location],
                strings.folder(dev.lad.domain.FolderRules.folder(track.path)) +
                    "/" +
                    details?.filename.orEmpty(),
            )
            error?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
            Button({ editing = true }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Edit, null)
                Text(strings[R.string.edit_tags])
            }
            OutlinedButton(
                {
                    coverForAlbum = false
                    coverPicker.launch(arrayOf("image/*"))
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(strings[R.string.track_artwork])
            }
            OutlinedButton(
                {
                    coverForAlbum = true
                    coverPicker.launch(arrayOf("image/*"))
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(strings[R.string.album_artwork])
            }
            FilledTonalButton(
                { exporter.launch(details?.filename ?: "${editedTrack.title}.mp3") },
                enabled =
                    !busy &&
                        details != null &&
                        !details!!.filename.endsWith(".opus", ignoreCase = true),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    if (busy) strings[R.string.writing_tags]
                    else strings[R.string.export_file_with_updated_tags]
                )
            }
            Text(
                if (details?.filename?.endsWith(".opus", ignoreCase = true) == true)
                    strings[R.string.your_edits_are_saved_in_the]
                else strings[R.string.track_details_panel_your_edits_are_saved_in_the],
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(24.dp))
        }
    }
    if (editing) {
        var title by remember { mutableStateOf(editedTrack.title) }
        var artist by remember { mutableStateOf(editedTrack.artist) }
        var album by remember { mutableStateOf(editedTrack.album) }
        var year by remember {
            mutableStateOf(editedTrack.year.takeIf { it > 0 }?.toString().orEmpty())
        }
        var genre by remember { mutableStateOf(editedTrack.genre) }
        AlertDialog(
            onDismissRequest = { editing = false },
            title = { Text(strings[R.string.tag_editor]) },
            text = {
                Column(
                    Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    OutlinedTextField(
                        title,
                        { title = it },
                        label = { Text(strings[R.string.name]) },
                    )
                    OutlinedTextField(
                        artist,
                        { artist = it },
                        label = { Text(strings[R.string.artist]) },
                    )
                    OutlinedTextField(
                        album,
                        { album = it },
                        label = { Text(strings[R.string.album]) },
                    )
                    OutlinedTextField(
                        year,
                        { year = it },
                        label = { Text(strings[R.string.year]) },
                        singleLine = true,
                    )
                    OutlinedTextField(
                        genre,
                        { genre = it },
                        label = { Text(strings[R.string.genre]) },
                    )
                }
            },
            confirmButton = {
                TextButton(
                    {
                        scope.launch {
                            attempt {
                                vm.saveEdit(
                                    editedTrack,
                                    TrackEdit(
                                        track.id,
                                        title.trim().ifBlank { strings[R.string.untitled] },
                                        artist.trim().ifBlank {
                                            strings[R.string.unknown_artist]
                                        },
                                        album.trim().ifBlank {
                                            strings[R.string.unknown_album]
                                        },
                                        year.toIntOrNull()?.coerceIn(0, 9999) ?: 0,
                                        genre.trim(),
                                        editedTrack.customArtwork,
                                    ),
                                )
                            }
                                .onSuccess {
                                    editedTrack =
                                        editedTrack.copy(
                                            title =
                                                title.trim().ifBlank { strings[R.string.untitled] },
                                            artist =
                                                artist.trim().ifBlank {
                                                    strings[R.string.unknown_artist]
                                                },
                                            album =
                                                album.trim().ifBlank {
                                                    strings[R.string.unknown_album]
                                                },
                                            year = year.toIntOrNull()?.coerceIn(0, 9999) ?: 0,
                                            genre = genre.trim(),
                                        )
                                    editing = false
                                }
                                .onFailure { error = it.message }
                        }
                    },
                    enabled = title.isNotBlank(),
                ) {
                    Text(strings[R.string.save])
                }
            },
            dismissButton = { TextButton({ editing = false }) { Text(strings[R.string.cancel]) } },
        )
    }
}

@Composable
fun InfoTile(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        modifier.fillMaxWidth(),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(value, style = MaterialTheme.typography.titleMedium)
        }
    }
}
