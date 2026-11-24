@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package dev.lad.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.lad.R
import dev.lad.data.*
import dev.lad.domain.ArtistCredits
import dev.lad.domain.albumKey
import dev.lad.domain.groupAlbums
import dev.lad.i18n.rememberText

@Composable
fun HomeScreen(
    tracks: List<Track>,
    playlists: List<Playlist>,
    history: List<Listen>,
    current: Long?,
    vm: LadViewModel,
    permitted: Boolean,
    scanning: Boolean,
    onPermission: () -> Unit,
    onCollection: (String, List<Track>) -> Unit,
    onAdd: (Track) -> Unit,
    onPlaylist: (Long) -> Unit,
    onDemo: () -> Unit,
) {
    val strings = rememberText()
    if (!permitted) {
        EmptyState(
            strings[R.string.music_within_reach],
            strings[R.string.allow_access_to_audio_files_lad],
            strings[R.string.open_my_music],
            onPermission,
        )
        return
    }
    if (tracks.isEmpty()) {
        EmptyState(
            if (scanning) strings[R.string.finding_music]
            else strings[R.string.your_music_starts_here],
            strings[R.string.copy_mp3_flac_or_other_audio],
            if (scanning) null else strings[R.string.refresh],
            vm::refresh,
        )
        return
    }
    val byId = remember(tracks) { tracks.associateBy { it.id } }
    val recent =
        remember(history, byId) {
            history.map { it.trackId }.distinct().mapNotNull { byId[it] }.take(12)
        }
    val frequent =
        remember(history, byId) {
            history
                .groupBy { it.trackId }
                .entries
                .sortedByDescending { it.value.sumOf { row -> row.listenedMs } }
                .mapNotNull { byId[it.key] }
                .take(12)
        }
    val featured = byId[current] ?: recent.firstOrNull() ?: frequent.firstOrNull() ?: tracks.first()
    val album =
        remember(tracks, featured.albumKey()) {
            tracks.filter { it.albumKey() == featured.albumKey() }.sortedBy { it.trackNumber }
        }
    val seed = artworkSeed(featured.artwork, LocalLadSettings.current.artworkColor)
    val baseScheme = MaterialTheme.colorScheme
    val palette = remember(seed, baseScheme) { seed?.let { coverScheme(it, baseScheme) } ?: baseScheme }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    strings[R.string.your_rhythm_today],
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.primary,
                )
                Text(
                    strings.plural(R.plurals.tracks, tracks.size, tracks.size),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item {
            BitsReveal(key = featured.albumKey()) {
                Card(
                    onClick = { onCollection("${featured.album} • ${featured.artist}", album) },
                    shape = LadShapes.hero,
                    colors =
                        CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                        ),
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Artwork(
                            featured.artwork,
                            featured.album,
                            Modifier.fillMaxWidth().aspectRatio(1.16f),
                            24,
                        )
                        Row(
                            Modifier.fillMaxWidth()
                                .padding(start = 6.dp, end = 4.dp, top = 16.dp, bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    strings.plural(
                                        R.plurals.album_tracks,
                                        album.size,
                                        album.size,
                                        if (featured.year > 0) " · ${featured.year}" else "",
                                    ),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = palette.primary,
                                )
                                Text(
                                    featured.album,
                                    style = MaterialTheme.typography.headlineLarge,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    featured.artist,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            FilledIconButton(
                                { vm.play(album.first(), album, featured.album, true, false) },
                                Modifier.size(64.dp),
                                shape = RoundedCornerShape(22.dp),
                                colors =
                                    IconButtonDefaults.filledIconButtonColors(
                                        containerColor = palette.primary,
                                        contentColor = palette.onPrimary,
                                    ),
                            ) {
                                Icon(
                                    Icons.Default.PlayArrow,
                                    strings[R.string.play_album],
                                    Modifier.size(32.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                val favoriteCount = tracks.count { it.favorite }
                QuickCollection(
                    strings[R.string.favourites],
                    strings.plural(R.plurals.tracks, favoriteCount, favoriteCount),
                    Icons.Default.Favorite,
                    Modifier.weight(1f),
                ) {
                    onCollection(strings[R.string.favourites], tracks.filter { it.favorite })
                }
                QuickCollection(
                    strings[R.string.new_music],
                    strings[R.string.in_your_library],
                    Icons.Default.AddCircleOutline,
                    Modifier.weight(1f),
                ) {
                    onCollection(
                        strings[R.string.recently_added],
                        tracks.sortedByDescending { it.addedAt },
                    )
                }
            }
        }
        if (recent.isNotEmpty()) {
            item { SectionTitle(strings[R.string.recently_played]) }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    items(recent, key = { it.id }) { t ->
                        Column(
                            Modifier.width(140.dp).clickable {
                                vm.play(t, recent, strings[R.string.recently_played])
                            }
                        ) {
                            Artwork(t.artwork, t.title, Modifier.size(140.dp), 20)
                            Text(
                                strings.metadata(t.title),
                                Modifier.padding(top = 8.dp),
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                strings.metadata(t.artist),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
        if (frequent.isNotEmpty()) {
            item { SectionTitle(strings[R.string.on_your_wavelength]) }
            items(frequent.take(3), key = { "top${it.id}" }) { t ->
                TrackRow(
                    t,
                    t.id == current,
                    { vm.play(t, frequent, strings[R.string.frequently_played]) },
                    { vm.favorite(t) },
                    { onAdd(t) },
                )
            }
        }
        if (playlists.isNotEmpty()) {
            item { SectionTitle(strings[R.string.your_collections]) }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(playlists.take(5), key = { it.id }) { p ->
                        Column(Modifier.width(140.dp).clickable { onPlaylist(p.id) }) {
                            Artwork(
                                p.cover ?: p.tracks.firstOrNull()?.let { byId[it]?.artwork },
                                p.name,
                                Modifier.size(140.dp),
                                20,
                            )
                            Text(
                                p.name,
                                Modifier.padding(top = 8.dp),
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
        item {
            Card(
                onClick = onDemo,
                shape = LadShapes.card,
                colors =
                    CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    ),
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Icon(
                        Icons.Default.AutoAwesome,
                        null,
                        Modifier.size(30.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Column(Modifier.weight(1f)) {
                        Text(
                            strings[R.string.your_year_in_music],
                            style = MaterialTheme.typography.titleLarge,
                        )
                        Text(
                            strings[R.string.demo_recap_10_stories],
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Icon(Icons.Default.ChevronRight, null)
                }
            }
        }
    }
}

@Composable
private fun QuickCollection(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = modifier,
        shape = LadShapes.card,
        colors =
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(icon, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary)
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun LibraryScreen(
    tracks: List<Track>,
    current: Long?,
    vm: LadViewModel,
    permitted: Boolean,
    scanning: Boolean,
    onPermission: () -> Unit,
    onCollection: (String, List<Track>) -> Unit,
    onAdd: (Track) -> Unit,
    onArtist: (String, List<Track>) -> Unit = onCollection,
) {
    val strings = rememberText()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
            ChoiceChips(
                tab.toString(),
                listOf(
                    "0" to strings[R.string.library_screens_tracks],
                    "1" to strings[R.string.albums],
                    "2" to strings[R.string.artists],
                ),
            ) {
                tab = it.toInt()
            }
        }
        if (!permitted)
            EmptyState(
                strings[R.string.allow_access],
                strings[R.string.access_to_audio_files_is_needed],
                strings[R.string.allow],
                onPermission,
            )
        else if (tracks.isEmpty())
            EmptyState(
                if (scanning) strings[R.string.finding_music] else strings[R.string.no_music_found],
                strings[R.string.add_files_to_the_music_folder],
                if (scanning) null else strings[R.string.refresh],
                vm::refresh,
            )
        else if (tab == 0)
            TrackListScreen(
                strings[R.string.all_tracks],
                tracks,
                current,
                vm,
                onAdd,
                header = {
                    Card(
                        onClick = {
                            onCollection(
                                strings[R.string.favourites],
                                tracks.filter { it.favorite },
                            )
                        },
                        shape = LadShapes.card,
                        colors =
                            CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                            ),
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = MaterialTheme.colorScheme.primaryContainer,
                            ) {
                                Box(Modifier.size(60.dp), contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Default.Favorite,
                                        null,
                                        Modifier.size(28.dp),
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                            Column(Modifier.weight(1f)) {
                                Text(
                                    strings[R.string.favourite_tracks],
                                    style = MaterialTheme.typography.titleLarge,
                                )
                                val favoriteCount = tracks.count { it.favorite }
                                Text(
                                    strings.plural(R.plurals.tracks, favoriteCount, favoriteCount),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Icon(Icons.Default.ChevronRight, null)
                        }
                    }
                },
            )
        else {
            val mergeArtists = LocalLadSettings.current.mergeArtists
            val groups =
                remember(tracks, tab, mergeArtists) {
                    if (tab == 1)
                        groupAlbums(tracks).map { rows ->
                            ArtistCredits.Group(rows.first().albumKey(), rows.first().album, rows)
                        }
                    else
                        ArtistCredits(tracks.map { it.artist }, mergeArtists).groups(tracks) {
                            it.artist
                        }
                }
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(groups, key = { it.key }) { group ->
                    val list = group.items
                    val first = list.first()
                    val name = group.name
                    val artist =
                        list.map { it.artist }.distinct().singleOrNull()
                            ?: strings[R.string.various_artists]
                    Card(
                        onClick = {
                            if (tab == 1) onCollection("$name • $artist", list)
                            else onArtist(name, list)
                        },
                        shape = LadShapes.card,
                        colors =
                            CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                            ),
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            if (tab == 1) Artwork(first.artwork, name, Modifier.size(76.dp), 18)
                            else ArtistPortrait(name, list, Modifier.size(76.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    name,
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    if (tab == 1) artist else strings[R.string.artist],
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    strings.plural(R.plurals.tracks, list.size, list.size),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                            FilledTonalIconButton(
                                { vm.play(first, list, name, true, false) },
                                Modifier.size(48.dp),
                            ) {
                                Icon(Icons.Default.PlayArrow, strings.format(R.string.play, name))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TrackListScreen(
    name: String,
    tracks: List<Track>,
    current: Long?,
    vm: LadViewModel,
    onAdd: (Track) -> Unit,
    showCover: Boolean = false,
    header: (@Composable () -> Unit)? = null,
    onAlbum: ((String, List<Track>) -> Unit)? = null,
    albumTracks: List<Track> = tracks,
) {
    val strings = rememberText()
    var sort by rememberSaveable(name) { mutableIntStateOf(0) }
    var menu by remember { mutableStateOf(false) }
    val historyFlow =
        remember(sort, vm) {
            if (sort == 5 || sort == 6) vm.statisticsState.history
            else kotlinx.coroutines.flow.flowOf(emptyList())
        }
    val history by historyFlow.collectAsStateWithLifecycle(emptyList())
    val playCounts =
        remember(history) { history.filter { it.counted }.groupingBy { it.trackId }.eachCount() }
    val lastPlayed =
        remember(history) {
            history.groupBy { it.trackId }.mapValues { (_, rows) -> rows.maxOf { it.startedAt } }
        }
    val sorted =
        remember(tracks, sort, playCounts, lastPlayed) {
            when (sort) {
                1 -> tracks.sortedBy { it.artist.lowercase() }
                2 -> tracks.sortedByDescending { it.addedAt }
                3 -> tracks.sortedBy { it.trackNumber }
                4 -> tracks.sortedBy { it.durationMs }
                5 -> tracks.sortedByDescending { playCounts[it.id] ?: 0 }
                6 -> tracks.sortedByDescending { lastPlayed[it.id] ?: 0 }
                7 -> tracks.sortedByDescending { it.fileSize }
                8 -> tracks.sortedBy { it.title.lowercase() }
                else -> tracks
            }
        }
    if (sorted.isEmpty()) {
        EmptyState(
            strings[R.string.nothing_here_yet],
            strings[R.string.add_your_favourite_music_here],
        )
        return
    }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        header?.let { item { it() } }
        if (showCover)
            item {
                val first = sorted.first()
                val seed = artworkSeed(first.artwork, LocalLadSettings.current.artworkColor)
                val colors =
                    seed?.let { coverScheme(it, MaterialTheme.colorScheme) }
                        ?: MaterialTheme.colorScheme
                Column(
                    Modifier.fillMaxWidth()
                        .clip(LadShapes.hero)
                        .background(
                            Brush.verticalGradient(listOf(colors.primaryContainer, colors.surface))
                        )
                        .padding(horizontal = 18.dp, vertical = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (onAlbum != null)
                        ArtistPortrait(name, albumTracks, Modifier.size(128.dp), openSource = true)
                    else
                        Artwork(
                            first.artwork,
                            name,
                            Modifier.fillMaxWidth(0.74f).aspectRatio(1f),
                            22,
                        )
                    Spacer(Modifier.height(20.dp))
                    Text(
                        name.substringBefore(" • "),
                        Modifier.fillMaxWidth(),
                        style = MaterialTheme.typography.headlineLarge,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (onAlbum == null)
                        Text(
                            sorted.map { it.artist }.distinct().take(4).joinToString(", "),
                            Modifier.fillMaxWidth(),
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    Text(
                        strings.plural(
                            R.plurals.library_screens_tracks_205,
                            sorted.size,
                            sorted.size,
                            duration(sorted.sumOf { it.durationMs }),
                        ),
                        Modifier.fillMaxWidth().padding(top = 8.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        if (onAlbum != null) item { ArtistAlbums(albumTracks, onAlbum) }
        item {
            Row(
                Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    strings.plural(R.plurals.tracks, sorted.size, sorted.size),
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Box {
                    IconButton({ menu = true }) {
                        Icon(Icons.AutoMirrored.Filled.Sort, strings[R.string.sort])
                    }
                    if (menu)
                        ExpressiveActions(
                            strings[R.string.sort],
                            strings.label(name),
                            { menu = false },
                            listOf(
                                    strings[R.string.list_order],
                                    strings[R.string.by_artist],
                                    strings[R.string.newest_first],
                                    strings[R.string.by_track_number],
                                    strings[R.string.by_duration],
                                    strings[R.string.most_played],
                                    strings[R.string.recently_played_first],
                                    strings[R.string.by_file_size],
                                    strings[R.string.by_title],
                                )
                                .mapIndexed { i, label ->
                                    PlayerAction(
                                        label,
                                        "",
                                        if (sort == i) Icons.Default.Check
                                        else Icons.AutoMirrored.Filled.Sort,
                                    ) {
                                        sort = i
                                        menu = false
                                    }
                                },
                        )
                }
                IconButton({ vm.play(sorted.random(), sorted, name, true, true) }) {
                    Icon(Icons.Default.Shuffle, strings[R.string.library_screens_shuffle])
                }
                FilledTonalButton(
                    { vm.play(sorted.first(), sorted, name, true, false) },
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    shapes = androidx.compose.material3.ButtonDefaults.shapes(),
                ) {
                    Icon(Icons.Default.PlayArrow, null, Modifier.size(20.dp))
                    Text(strings[R.string.library_screens_play])
                }
            }
        }
        items(sorted, key = { it.id }) { t ->
            TrackRow(
                t,
                t.id == current,
                { vm.play(t, sorted, name) },
                { vm.favorite(t) },
                { onAdd(t) },
            )
        }
    }
}

@Composable
fun SearchScreen(
    tracks: List<Track>,
    playlists: List<Playlist>,
    current: Long?,
    vm: LadViewModel,
    onCollection: (String, List<Track>) -> Unit,
    onAdd: (Track) -> Unit,
    onPlaylist: (Long) -> Unit,
    onArtist: (String, List<Track>) -> Unit = onCollection,
) {
    val strings = rememberText()
    val mergeArtists = LocalLadSettings.current.mergeArtists
    var query by rememberSaveable { mutableStateOf("") }
    val found =
        remember(query, tracks) {
            if (query.isBlank()) emptyList()
            else
                tracks.filter {
                    it.title.contains(query, true) ||
                        it.artist.contains(query, true) ||
                        it.album.contains(query, true)
                }
        }
    val foundPlaylists =
        remember(query, playlists) {
            if (query.isBlank()) emptyList() else playlists.filter { it.name.contains(query, true) }
        }
    val foundArtists =
        remember(query, tracks, mergeArtists) {
            if (query.isBlank()) emptyList()
            else
                ArtistCredits(tracks.map { it.artist }, mergeArtists)
                    .groups(tracks) { it.artist }
                    .filter { it.name.contains(query, true) }
                    .take(4)
        }
    val foundAlbums =
        remember(query, found) {
            if (query.isBlank()) emptyList()
            else
                found
                    .groupBy { "${it.album} • ${it.artist}" }
                    .filterKeys { it.contains(query, true) }
                    .entries
                    .take(4)
                    .toList()
        }
    Column(Modifier.padding(horizontal = 16.dp)) {
        OutlinedTextField(
            query,
            { query = it },
            placeholder = { Text(strings[R.string.track_album_artist]) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(28.dp),
            leadingIcon = { Icon(Icons.Default.Search, null) },
        )
        LazyColumn(contentPadding = PaddingValues(vertical = 12.dp)) {
            if (query.isBlank())
                item {
                    EmptyState(
                        strings[R.string.find_your_sound],
                        strings[R.string.search_music_and_playlists_on_your],
                    )
                }
            else {
                items(foundPlaylists, key = { "p${it.id}" }) { p ->
                    ListItem(
                        headlineContent = { Text(p.name) },
                        supportingContent = {
                            Text(
                                strings.plural(
                                    R.plurals.playlist_tracks,
                                    p.tracks.size,
                                    p.tracks.size,
                                )
                            )
                        },
                        modifier = Modifier.clickable { onPlaylist(p.id) },
                    )
                }
                items(foundArtists, key = { "a${it.key}" }) { group ->
                    val a = group.name
                    ListItem(
                        headlineContent = { Text(a) },
                        supportingContent = { Text(strings[R.string.artist]) },
                        leadingContent = { ArtistPortrait(a, group.items, Modifier.size(48.dp)) },
                        modifier = Modifier.clickable { onArtist(a, group.items) },
                    )
                }
                items(foundAlbums, key = { "album${it.key}" }) { (a, list) ->
                    ListItem(
                        headlineContent = { Text(a) },
                        supportingContent = { Text(strings[R.string.album]) },
                        leadingContent = { Icon(Icons.Default.Album, null) },
                        modifier = Modifier.clickable { onCollection(a, list) },
                    )
                }
                items(found, key = { it.id }) { t ->
                    TrackRow(
                        t,
                        t.id == current,
                        {
                            vm.play(
                                t,
                                found,
                                strings.format(R.string.library_screens_search, query),
                            )
                        },
                        { vm.favorite(t) },
                        { onAdd(t) },
                    )
                }
                if (found.isEmpty() && foundPlaylists.isEmpty())
                    item {
                        EmptyState(
                            strings[R.string.no_results],
                            strings[R.string.try_another_title],
                        )
                    }
            }
        }
    }
}
