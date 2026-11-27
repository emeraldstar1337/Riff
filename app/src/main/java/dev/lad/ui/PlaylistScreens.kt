@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class,
)

package dev.lad.ui

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.lad.R
import dev.lad.data.*
import dev.lad.i18n.rememberText
import dev.lad.util.attempt

@Composable
fun NameDialog(
    title: String,
    initial: String = "",
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    val strings = rememberText()
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                name,
                { name = it },
                label = { Text(strings[R.string.name]) },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(name.trim()) },
                enabled = name.isNotBlank(),
                shapes = ButtonDefaults.shapes(),
            ) {
                Text(strings[R.string.save])
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                shapes = ButtonDefaults.shapes(),
            ) {
                Text(strings[R.string.cancel])
            }
        },
    )
}

@Composable
fun AddPlaylistDialog(
    track: Track,
    playlists: List<Playlist>,
    onDismiss: () -> Unit,
    onAdd: (Long) -> Unit,
    onCreate: (String) -> Unit,
) {
    val strings = rememberText()
    var creating by remember { mutableStateOf(false) }
    if (creating)
        NameDialog(
            strings[R.string.new_playlist],
            onDismiss = { creating = false },
            onSave = onCreate,
        )
    else
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(strings.format(R.string.add, track.title)) },
            text = {
                LazyColumn(Modifier.heightIn(max = 340.dp)) {
                    items(playlists, key = { it.id }) { p ->
                        ListItem(
                            headlineContent = { Text(p.name) },
                            supportingContent = {
                                Text(strings.plural(R.plurals.tracks, p.tracks.size, p.tracks.size))
                            },
                            modifier = Modifier.clickable { onAdd(p.id) },
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { creating = true },
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text(strings[R.string.create_playlist])
                }
            },
            dismissButton = {
                TextButton(
                    onClick = onDismiss,
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text(strings[R.string.close])
                }
            },
        )
}

@Composable
fun PlaylistsScreen(
    playlists: List<Playlist>,
    tracks: List<Track>,
    onSave: (Playlist) -> Unit,
    onOpen: (Long) -> Unit,
) {
    val strings = rememberText()
    var create by remember { mutableStateOf(false) }
    val lookup = remember(tracks) { tracks.associateBy { it.id } }
    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 92.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            if (playlists.isEmpty())
                item {
                    EmptyState(
                        strings[R.string.set_the_mood],
                        strings[R.string.create_a_playlist_then_add_tracks],
                    )
                }
            itemsIndexed(playlists, key = { _, p -> p.id }) { idx, p ->
                Surface(
                    onClick = { onOpen(p.id) },
                    shape = segmentedItemShape(idx, playlists.size),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Artwork(
                            p.cover ?: p.tracks.firstOrNull()?.let { lookup[it]?.artwork },
                            p.name,
                            Modifier.size(72.dp),
                            14,
                        )
                        Column(Modifier.weight(1f)) {
                            Text(
                                p.name,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                strings.plural(R.plurals.tracks, p.tracks.size, p.tracks.size),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Icon(
                            Icons.Default.ChevronRight,
                            null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        // Official M3 Expressive Extended FAB anchored bottom-end above MiniPlayer & NavigationBar
        ExtendedFloatingActionButton(
            onClick = { create = true },
            icon = { Icon(Icons.Default.Add, null) },
            text = { Text(strings[R.string.new_playlist], fontWeight = FontWeight.SemiBold) },
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp),
        )
    }
    if (create)
        NameDialog(
            strings[R.string.new_playlist],
            onDismiss = { create = false },
            onSave = {
                onSave(Playlist(name = it))
                create = false
            },
        )
}

@Composable
fun PlaylistScreen(
    p: Playlist,
    tracks: List<Track>,
    current: Long?,
    vm: LadViewModel,
    onAdd: (Track) -> Unit,
    onDeleted: () -> Unit,
) {
    val strings = rememberText()
    val context = LocalContext.current
    var rename by remember { mutableStateOf(false) }
    var delete by remember { mutableStateOf(false) }
    var picking by remember { mutableStateOf(false) }
    var localOrder by remember(p.id, p.tracks) { mutableStateOf(p.tracks.distinct()) }
    var dragId by remember { mutableStateOf<Long?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val latestPlaylist by rememberUpdatedState(p)
    val rowHeight = with(LocalDensity.current) { 78.dp.toPx() }
    val lookup = remember(tracks) { tracks.associateBy { it.id } }
    val latestLookup by rememberUpdatedState(lookup)
    val available = remember(localOrder, lookup) { localOrder.mapNotNull { lookup[it] } }
    val cover =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                attempt {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION,
                    )
                }
                vm.savePlaylist(p.copy(cover = uri.toString()))
            }
        }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        item {
            Column(
                Modifier.fillMaxWidth().padding(bottom = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Artwork(
                    p.cover ?: available.firstOrNull()?.artwork,
                    p.name,
                    Modifier.size(190.dp).clickable { cover.launch(arrayOf("image/*")) },
                    28,
                )
                Text(
                    p.name,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Black,
                )
                Text(
                    strings.plural(
                        R.plurals.playlist_screens_tracks,
                        available.size,
                        available.size,
                        duration(available.sumOf { it.durationMs }),
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SplitButtonLayout(
                        leadingButton = {
                            SplitButtonDefaults.LeadingButton(
                                onClick = {
                                    available.firstOrNull()?.let {
                                        vm.play(it, available, p.name, true, false)
                                    }
                                },
                                enabled = available.isNotEmpty(),
                            ) {
                                Icon(Icons.Default.PlayArrow, null, Modifier.size(20.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(strings[R.string.listen])
                            }
                        },
                        trailingButton = {
                            SplitButtonDefaults.TrailingButton(
                                checked = false,
                                onCheckedChange = {
                                    available.randomOrNull()?.let {
                                        vm.play(it, available, p.name, true, true)
                                    }
                                },
                                enabled = available.isNotEmpty(),
                            ) {
                                Icon(
                                    Icons.Default.Shuffle,
                                    strings[R.string.library_screens_shuffle],
                                    Modifier.size(20.dp),
                                )
                            }
                        },
                    )
                    FilledTonalIconButton(onClick = { rename = true }) {
                        Icon(Icons.Default.Edit, strings[R.string.rename])
                    }
                    FilledTonalIconButton(onClick = { delete = true }) {
                        Icon(Icons.Default.Delete, strings[R.string.delete_playlist])
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistChip(
                        onClick = { picking = true },
                        label = { Text(strings[R.string.add_tracks]) },
                        leadingIcon = { Icon(Icons.Default.Add, null, Modifier.size(18.dp)) },
                    )
                    AssistChip(
                        onClick = {
                            localOrder =
                                available.sortedBy { it.title.lowercase() }.map { it.id } +
                                    localOrder.filter { it !in lookup }
                            vm.savePlaylist(p.copy(tracks = localOrder))
                        },
                        label = { Text(strings[R.string.a_z]) },
                    )
                }
                Text(
                    strings[R.string.hold_to_reorder_tap_the_artwork],
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (localOrder.size > available.size) {
                    val missing = localOrder.size - available.size
                    Text(
                        strings.plural(R.plurals.unavailable_files, missing, missing),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
        if (available.isEmpty())
            item {
                EmptyState(
                    strings[R.string.your_playlist_is_waiting_for_music],
                    strings[R.string.tap_add_tracks_to_choose_songs],
                )
            }
        itemsIndexed(available, key = { _, t -> t.id }) { idx, t ->
            TrackRow(
                t,
                t.id == current,
                { vm.play(t, available, p.name) },
                { vm.favorite(t) },
                { onAdd(t) },
                modifier = Modifier.animateItem(),
                shape = segmentedItemShape(idx, available.size),
                trailing = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = {
                                localOrder = localOrder - t.id
                                vm.savePlaylist(p.copy(tracks = localOrder))
                            },
                            modifier = Modifier.size(30.dp),
                        ) {
                            Icon(
                                Icons.Default.RemoveCircleOutline,
                                strings[R.string.remove_from_playlist],
                                Modifier.size(20.dp),
                            )
                        }
                        Icon(
                            Icons.Default.DragHandle,
                            strings[R.string.reorder],
                            Modifier.padding(8.dp).pointerInput(t.id) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = {
                                        dragId = t.id
                                        dragOffset = 0f
                                    },
                                    onDragEnd = {
                                        dragId = null
                                        vm.savePlaylist(latestPlaylist.copy(tracks = localOrder))
                                    },
                                    onDragCancel = {
                                        dragId = null
                                        vm.savePlaylist(latestPlaylist.copy(tracks = localOrder))
                                    },
                                    onDrag = { change, amount ->
                                        change.consume()
                                        dragOffset += amount.y
                                        val direction =
                                            if (dragOffset > rowHeight) 1
                                            else if (dragOffset < -rowHeight) -1 else 0
                                        if (direction != 0) {
                                            val activeId = dragId
                                            val from = localOrder.indexOf(activeId)
                                            if (from >= 0) {
                                                var to = from + direction
                                                while (
                                                    to in localOrder.indices &&
                                                        localOrder[to] !in latestLookup
                                                ) {
                                                    to += direction
                                                }
                                                if (to in localOrder.indices) {
                                                    val next = localOrder.toMutableList()
                                                    next.add(to, next.removeAt(from))
                                                    localOrder = next
                                                    dragOffset -= direction * rowHeight
                                                }
                                            }
                                        }
                                    },
                                )
                            },
                        )
                    }
                },
            )
        }
    }
    if (rename)
        NameDialog(
            strings[R.string.rename],
            p.name,
            onDismiss = { rename = false },
            onSave = {
                vm.savePlaylist(p.copy(name = it))
                rename = false
            },
        )
    if (delete)
        AlertDialog(
            onDismissRequest = { delete = false },
            title = { Text(strings[R.string.playlist_screens_delete_playlist]) },
            text = { Text(strings[R.string.music_files_stay_on_your_device]) },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.deletePlaylist(p.id)
                        delete = false
                        onDeleted()
                    },
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text(strings[R.string.delete])
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { delete = false },
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text(strings[R.string.cancel])
                }
            },
        )
    if (picking)
        PickTracksDialog(
            tracks,
            p.tracks,
            onDismiss = { picking = false },
            onSave = {
                vm.savePlaylist(p.copy(tracks = (p.tracks + it).distinct()))
                picking = false
            },
        )
}

@Composable
fun PickTracksDialog(
    tracks: List<Track>,
    existing: List<Long>,
    onDismiss: () -> Unit,
    onSave: (List<Long>) -> Unit,
) {
    val strings = rememberText()
    var selected by remember { mutableStateOf(emptySet<Long>()) }
    var query by remember { mutableStateOf("") }
    val existingIds = remember(existing) { existing.toHashSet() }
    val filtered =
        remember(tracks, existingIds, query) {
            tracks.filter {
                it.id !in existingIds &&
                    (it.title.contains(query, true) || it.artist.contains(query, true))
            }
        }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings[R.string.choose_tracks]) },
        text = {
            Column {
                OutlinedTextField(
                    query,
                    { query = it },
                    placeholder = { Text(strings[R.string.search]) },
                    singleLine = true,
                )
                LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    items(filtered, key = { it.id }) { t ->
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                selected =
                                    if (t.id in selected) selected - t.id else selected + t.id
                            },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                t.id in selected,
                                { selected = if (it) selected + t.id else selected - t.id },
                            )
                            Column {
                                Text(strings.metadata(t.title), maxLines = 1)
                                Text(
                                    strings.metadata(t.artist),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(selected.toList()) },
                enabled = selected.isNotEmpty(),
                shapes = ButtonDefaults.shapes(),
            ) {
                Text(strings.format(R.string.playlist_screens_add, selected.size))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                shapes = ButtonDefaults.shapes(),
            ) {
                Text(strings[R.string.cancel])
            }
        },
    )
}
