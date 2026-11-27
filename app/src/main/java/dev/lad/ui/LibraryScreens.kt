@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class,
)

package dev.lad.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.carousel.HorizontalMultiBrowseCarousel
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.text.font.FontWeight
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
    onSeasonRecap: (dev.lad.statistics.MusicStats, String) -> Unit = { _, _ -> },
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
    val albums = remember(tracks) { groupAlbums(tracks).take(10) }
    val featured = byId[current] ?: recent.firstOrNull() ?: frequent.firstOrNull() ?: tracks.first()
    val album =
        remember(tracks, featured.albumKey()) {
            tracks.filter { it.albumKey() == featured.albumKey() }.sortedBy { it.trackNumber }
        }
    val seed = artworkSeed(featured.artwork, LocalLadSettings.current.artworkColor)
    val baseScheme = MaterialTheme.colorScheme
    val palette = remember(seed, baseScheme) { seed?.let { coverScheme(it, baseScheme) } ?: baseScheme }
    val pullState = rememberPullToRefreshState()

    PullToRefreshBox(
        isRefreshing = scanning,
        onRefresh = { vm.refresh(false) },
        state = pullState,
        indicator = {
            PullToRefreshDefaults.LoadingIndicator(
                state = pullState,
                isRefreshing = scanning,
                modifier = Modifier.align(Alignment.TopCenter),
            )
        },
        modifier = Modifier.fillMaxSize(),
    ) {
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
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        strings.plural(R.plurals.tracks, tracks.size, tracks.size),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item("season-recap-prompt") {
                SeasonRecapPromptCard(
                    history = history,
                    settings = LocalLadSettings.current,
                    onOpen = { stats, label, key ->
                        vm.updateSettings { it.copy(seenSeasonRecap = key) }
                        onSeasonRecap(stats, label)
                    },
                    onDismiss = { key ->
                        vm.updateSettings { it.copy(seenSeasonRecap = key) }
                    },
                )
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
                                20,
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
                                    shape = RoundedCornerShape(20.dp),
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
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    val favoriteCount = tracks.count { it.favorite }
                    QuickCollection(
                        title = strings[R.string.favourites],
                        subtitle = strings.plural(R.plurals.tracks, favoriteCount, favoriteCount),
                        icon = Icons.Default.Favorite,
                        badgeShape = FlowerShape,
                        badgeColor = MaterialTheme.colorScheme.tertiaryContainer,
                        badgeContentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.weight(1f),
                    ) {
                        onCollection(strings[R.string.favourites], tracks.filter { it.favorite })
                    }
                    QuickCollection(
                        title = strings[R.string.new_music],
                        subtitle = strings[R.string.in_your_library],
                        icon = Icons.Default.AutoAwesome,
                        badgeShape = CloverShape,
                        badgeColor = MaterialTheme.colorScheme.secondaryContainer,
                        badgeContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.weight(1f),
                    ) {
                        onCollection(
                            strings[R.string.recently_added],
                            tracks.sortedByDescending { it.addedAt },
                        )
                    }
                }
            }
            // M3 Expressive Multi-Browse Carousel: Recently played (or Albums showcase when history is fresh)
            if (recent.isNotEmpty()) {
                item { SectionTitle(strings[R.string.recently_played]) }
                item("recent-carousel") {
                    val state = rememberCarouselState { recent.size }
                    HorizontalMultiBrowseCarousel(
                        state = state,
                        preferredItemWidth = 172.dp,
                        itemSpacing = 10.dp,
                        modifier = Modifier.fillMaxWidth().height(184.dp),
                    ) { idx ->
                        val t = recent[idx]
                        Box(
                            Modifier.fillMaxSize()
                                .maskClip(RoundedCornerShape(28.dp))
                                .clickable {
                                    vm.play(t, recent, strings[R.string.recently_played])
                                }
                        ) {
                            Artwork(t.artwork, t.title, Modifier.fillMaxSize(), 0)
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
                                    strings.metadata(t.title),
                                    color = Color.White,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    strings.metadata(t.artist),
                                    color = Color.White.copy(alpha = 0.80f),
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            } else if (albums.size >= 2) {
                item { SectionTitle(strings[R.string.albums]) }
                item("albums-carousel") {
                    val state = rememberCarouselState { albums.size }
                    HorizontalMultiBrowseCarousel(
                        state = state,
                        preferredItemWidth = 172.dp,
                        itemSpacing = 10.dp,
                        modifier = Modifier.fillMaxWidth().height(184.dp),
                    ) { idx ->
                        val rows = albums[idx]
                        val first = rows.first()
                        Box(
                            Modifier.fillMaxSize()
                                .maskClip(RoundedCornerShape(28.dp))
                                .clickable {
                                    onCollection("${first.album} • ${first.artist}", rows)
                                }
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
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    strings.metadata(first.artist),
                                    color = Color.White.copy(alpha = 0.80f),
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
            if (frequent.isNotEmpty()) {
                val topTracks = frequent.take(3)
                item { SectionTitle(strings[R.string.on_your_wavelength]) }
                item("top-wavelength-group") {
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        topTracks.forEachIndexed { idx, t ->
                            TrackRow(
                                t,
                                t.id == current,
                                { vm.play(t, frequent, strings[R.string.frequently_played]) },
                                { vm.favorite(t) },
                                { onAdd(t) },
                                shape = segmentedItemShape(idx, topTracks.size),
                            )
                        }
                    }
                }
            }
            if (playlists.isNotEmpty()) {
                val topPlaylists = playlists.take(8)
                item { SectionTitle(strings[R.string.your_collections]) }
                item("playlists-carousel") {
                    val state = rememberCarouselState { topPlaylists.size }
                    HorizontalMultiBrowseCarousel(
                        state = state,
                        preferredItemWidth = 172.dp,
                        itemSpacing = 10.dp,
                        modifier = Modifier.fillMaxWidth().height(176.dp),
                    ) { idx ->
                        val p = topPlaylists[idx]
                        Box(
                            Modifier.fillMaxSize()
                                .maskClip(RoundedCornerShape(28.dp))
                                .clickable { onPlaylist(p.id) }
                        ) {
                            Artwork(
                                p.cover ?: p.tracks.firstOrNull()?.let { byId[it]?.artwork },
                                p.name,
                                Modifier.fillMaxSize(),
                                0,
                            )
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
                                    p.name,
                                    color = Color.White,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    strings.plural(R.plurals.tracks, p.tracks.size, p.tracks.size),
                                    color = Color.White.copy(alpha = 0.80f),
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
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
    badgeShape: Shape,
    badgeColor: Color,
    badgeContentColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = modifier,
        shape = LadShapes.artwork,
        colors =
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Surface(
                shape = badgeShape,
                color = badgeColor,
                contentColor = badgeContentColor,
                modifier = Modifier.size(44.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, null, Modifier.size(22.dp))
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
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
    val pullState = rememberPullToRefreshState()
    PullToRefreshBox(
        isRefreshing = scanning,
        onRefresh = { vm.refresh(false) },
        state = pullState,
        indicator = {
            PullToRefreshDefaults.LoadingIndicator(
                state = pullState,
                isRefreshing = scanning,
                modifier = Modifier.align(Alignment.TopCenter),
            )
        },
        modifier = Modifier.fillMaxSize(),
    ) {
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
                    if (scanning) strings[R.string.finding_music]
                    else strings[R.string.no_music_found],
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
                            shape = LadShapes.artwork,
                            colors =
                                CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                                ),
                        ) {
                            Row(
                                Modifier.fillMaxWidth().padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                            ) {
                                Surface(
                                    shape = FlowerShape,
                                    color = MaterialTheme.colorScheme.tertiaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                                ) {
                                    Box(Modifier.size(54.dp), contentAlignment = Alignment.Center) {
                                        Icon(Icons.Default.Favorite, null, Modifier.size(26.dp))
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
                                ArtistCredits.Group(
                                    rows.first().albumKey(),
                                    rows.first().album,
                                    rows,
                                )
                            }
                        else
                            ArtistCredits(tracks.map { it.artist }, mergeArtists).groups(tracks) {
                                it.artist
                            }
                    }
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    itemsIndexed(groups, key = { _, g -> g.key }) { idx, group ->
                        val list = group.items
                        val first = list.first()
                        val name = group.name
                        val artist =
                            if (tab == 1)
                                list.map { it.artist }.distinct().singleOrNull()
                                    ?: strings[R.string.various_artists]
                            else name
                        Surface(
                            onClick = {
                                if (tab == 1) onCollection("$name • $artist", list)
                                else onArtist(name, list)
                            },
                            shape = segmentedItemShape(idx, groups.size),
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                        ) {
                            Row(
                                Modifier.fillMaxWidth().padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                            ) {
                                if (tab == 1) Artwork(first.artwork, name, Modifier.size(64.dp), 12)
                                else ArtistPortrait(name, list, Modifier.size(64.dp), round = 12)
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        name,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
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
                                    Icon(
                                        Icons.Default.PlayArrow,
                                        strings.format(R.string.play, name),
                                    )
                                }
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
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        header?.let {
            item {
                Box(Modifier.padding(bottom = 8.dp)) { it() }
            }
        }
        if (showCover)
            item {
                val first = sorted.first()
                val seed = artworkSeed(first.artwork, LocalLadSettings.current.artworkColor)
                val colors =
                    seed?.let { coverScheme(it, MaterialTheme.colorScheme) }
                        ?: MaterialTheme.colorScheme
                Column(
                    Modifier.fillMaxWidth()
                        .padding(bottom = 10.dp)
                        .clip(LadShapes.hero)
                        .background(
                            Brush.verticalGradient(listOf(colors.primaryContainer, colors.surface))
                        )
                        .padding(horizontal = 18.dp, vertical = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (onAlbum != null)
                        ArtistPortrait(
                            name,
                            albumTracks,
                            Modifier.size(128.dp),
                            openSource = true,
                            round = 28,
                        )
                    else
                        Artwork(
                            first.artwork,
                            name,
                            Modifier.fillMaxWidth(0.74f).aspectRatio(1f),
                            20,
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
                Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    strings.plural(R.plurals.tracks, sorted.size, sorted.size),
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Box {
                    FilledTonalIconButton({ menu = true }, Modifier.size(44.dp)) {
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
                // Official M3 Expressive Split Button: Play (leading) | Shuffle (trailing)
                SplitButtonLayout(
                    leadingButton = {
                        SplitButtonDefaults.LeadingButton(
                            onClick = { vm.play(sorted.first(), sorted, name, true, false) },
                        ) {
                            Icon(Icons.Default.PlayArrow, null, Modifier.size(20.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(strings[R.string.library_screens_play])
                        }
                    },
                    trailingButton = {
                        SplitButtonDefaults.TrailingButton(
                            checked = false,
                            onCheckedChange = {
                                vm.play(sorted.random(), sorted, name, true, true)
                            },
                        ) {
                            Icon(
                                Icons.Default.Shuffle,
                                strings[R.string.library_screens_shuffle],
                                Modifier.size(20.dp),
                            )
                        }
                    },
                )
            }
        }
        itemsIndexed(sorted, key = { _, t -> t.id }) { idx, t ->
            TrackRow(
                t,
                t.id == current,
                { vm.play(t, sorted, name) },
                { vm.favorite(t) },
                { onAdd(t) },
                shape = segmentedItemShape(idx, sorted.size),
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
    var filter by rememberSaveable { mutableStateOf("all") }
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
                    .take(5)
        }
    val foundAlbums =
        remember(query, found) {
            if (query.isBlank()) emptyList()
            else
                found
                    .groupBy { "${it.album} • ${it.artist}" }
                    .filterKeys { it.contains(query, true) }
                    .entries
                    .take(5)
                    .toList()
        }
    val filters =
        listOf(
            "all" to strings[R.string.search_filter_all],
            "tracks" to strings[R.string.library_screens_tracks],
            "albums" to strings[R.string.albums],
            "artists" to strings[R.string.artists],
            "playlists" to strings[R.string.playlists],
        )
    Column(Modifier.fillMaxSize()) {
        // M3 Expressive Filled Pill Search Container
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).height(56.dp),
        ) {
            Row(
                Modifier.fillMaxSize().padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    Icons.Default.Search,
                    null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (query.isEmpty()) {
                        Text(
                            strings[R.string.track_album_artist],
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        textStyle =
                            MaterialTheme.typography.bodyLarge.copy(
                                color = MaterialTheme.colorScheme.onSurface
                            ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (query.isNotEmpty()) {
                    IconButton(onClick = { query = "" }, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.Close, strings[R.string.clear], Modifier.size(20.dp))
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(
            Modifier.fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            filters.forEach { (id, label) ->
                val selected = filter == id
                FilterChip(
                    selected = selected,
                    onClick = { filter = id },
                    label = { Text(label) },
                    leadingIcon =
                        if (selected) {
                            { Icon(Icons.Default.Check, null, Modifier.size(16.dp)) }
                        } else null,
                )
            }
        }
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            if (query.isBlank())
                item {
                    EmptyState(
                        strings[R.string.find_your_sound],
                        strings[R.string.search_music_and_playlists_on_your],
                    )
                }
            else {
                val showPlaylists = filter in setOf("all", "playlists") && foundPlaylists.isNotEmpty()
                val showArtists = filter in setOf("all", "artists") && foundArtists.isNotEmpty()
                val showAlbums = filter in setOf("all", "albums") && foundAlbums.isNotEmpty()
                val showTracks = filter in setOf("all", "tracks") && found.isNotEmpty()

                if (showPlaylists) {
                    item { SectionTitle(strings[R.string.playlists]) }
                    itemsIndexed(foundPlaylists, key = { _, p -> "p${p.id}" }) { idx, p ->
                        Surface(
                            onClick = { onPlaylist(p.id) },
                            shape = segmentedItemShape(idx, foundPlaylists.size),
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                        ) {
                            ListItem(
                                headlineContent = { Text(p.name, fontWeight = FontWeight.SemiBold) },
                                supportingContent = {
                                    Text(
                                        strings.plural(
                                            R.plurals.playlist_tracks,
                                            p.tracks.size,
                                            p.tracks.size,
                                        )
                                    )
                                },
                                leadingContent = {
                                    Icon(
                                        Icons.AutoMirrored.Filled.QueueMusic,
                                        null,
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            )
                        }
                    }
                }
                if (showArtists) {
                    item { SectionTitle(strings[R.string.artists]) }
                    itemsIndexed(foundArtists, key = { _, g -> "a${g.key}" }) { idx, group ->
                        val a = group.name
                        Surface(
                            onClick = { onArtist(a, group.items) },
                            shape = segmentedItemShape(idx, foundArtists.size),
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                        ) {
                            ListItem(
                                headlineContent = { Text(a, fontWeight = FontWeight.SemiBold) },
                                supportingContent = { Text(strings[R.string.artist]) },
                                leadingContent = {
                                    ArtistPortrait(a, group.items, Modifier.size(48.dp), round = 12)
                                },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            )
                        }
                    }
                }
                if (showAlbums) {
                    item { SectionTitle(strings[R.string.albums]) }
                    itemsIndexed(foundAlbums, key = { _, e -> "album${e.key}" }) { idx, (a, list) ->
                        Surface(
                            onClick = { onCollection(a, list) },
                            shape = segmentedItemShape(idx, foundAlbums.size),
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                        ) {
                            ListItem(
                                headlineContent = { Text(a, fontWeight = FontWeight.SemiBold) },
                                supportingContent = { Text(strings[R.string.album]) },
                                leadingContent = {
                                    Artwork(
                                        list.firstOrNull()?.artwork,
                                        a,
                                        Modifier.size(48.dp),
                                        12,
                                    )
                                },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            )
                        }
                    }
                }
                if (showTracks) {
                    item { SectionTitle(strings[R.string.library_screens_tracks]) }
                    itemsIndexed(found, key = { _, t -> t.id }) { idx, t ->
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
                            shape = segmentedItemShape(idx, found.size),
                        )
                    }
                }
                if (!showPlaylists && !showArtists && !showAlbums && !showTracks)
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
