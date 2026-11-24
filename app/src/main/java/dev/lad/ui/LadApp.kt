@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package dev.lad.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.*
import dev.lad.R
import dev.lad.data.*
import dev.lad.domain.albumKey
import dev.lad.i18n.rememberText
import dev.lad.statistics.*
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@Composable
fun LadApp(vm: LadViewModel = viewModel()) {
    val strings = rememberText()
    val storedSettings by vm.settingsState.settings.collectAsStateWithLifecycle()
    val tracks by vm.libraryState.tracks.collectAsStateWithLifecycle()
    val playlists by vm.playlistsState.playlists.collectAsStateWithLifecycle()
    val stablePlayback =
        remember(vm) {
            vm.playerState.playback.state
                .map { it.copy(positionMs = 0, positionTimestampMs = 0) }
                .distinctUntilChanged()
        }
    val initialPlayback =
        remember(vm) {
            vm.playerState.playback.state.value.copy(positionMs = 0, positionTimestampMs = 0)
        }
    val playback by stablePlayback.collectAsStateWithLifecycle(initialPlayback)
    val visible by vm.playerState.playback.uiVisible.collectAsStateWithLifecycle()
    val systemSaving by vm.playerState.playback.systemSaving.collectAsStateWithLifecycle()
    val saving = systemSaving
    val settings =
        storedSettings.copy(
            animations = storedSettings.animations && visible && !saving,
            vinyl = storedSettings.vinyl && visible && !saving,
            visualizer = storedSettings.visualizer && visible && !saving,
            glowMotion = storedSettings.glowMotion && visible && playback.playing && !saving,
            glowShapes = storedSettings.glowShapes && !saving,
            glowStrength =
                if (saving) minOf(0.35f, storedSettings.glowStrength)
                else storedSettings.glowStrength,
        )
    val scanning by vm.libraryState.scanning.collectAsStateWithLifecycle()
    val message by vm.libraryState.message.collectAsStateWithLifecycle()
    val nav = rememberNavController()
    val backDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route ?: "home"
    var permitted by remember { mutableStateOf(vm.hasPermission()) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, vm) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val nowPermitted = vm.hasPermission()
                val newlyGranted = !permitted && nowPermitted
                permitted = nowPermitted
                if (newlyGranted) vm.refresh(false)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val request =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
            permitted = it
            if (it) vm.refresh()
        }
    val permission = {
        request.launch(
            if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO
            else Manifest.permission.READ_EXTERNAL_STORAGE
        )
    }
    var playerOpen by rememberSaveable { mutableStateOf(false) }
    var addTrack by remember { mutableStateOf<Track?>(null) }
    var artistName by rememberSaveable { mutableStateOf("") }
    var artistIds by rememberSaveable { mutableStateOf(longArrayOf()) }
    var collectionName by rememberSaveable { mutableStateOf("") }
    var collectionIds by rememberSaveable { mutableStateOf(longArrayOf()) }
    val lookup = remember(tracks) { tracks.associateBy { it.id } }
    val collection =
        remember(collectionName, collectionIds, tracks, lookup, strings) {
            collectionName to
                (when (strings.label(collectionName)) {
                    strings[R.string.favourites] -> tracks.filter { it.favorite }
                    strings[R.string.recently_added] -> tracks.sortedByDescending { it.addedAt }
                    else ->
                        if (collectionIds.isNotEmpty())
                            collectionIds.asSequence().mapNotNull { lookup[it] }.toList()
                        else
                            tracks.filter {
                                it.artist == collectionName ||
                                    "${it.album} • ${it.artist}" == collectionName
                            }
                })
        }
    var recap by remember { mutableStateOf<Triple<MusicStats, String, Boolean>?>(null) }
    BackHandler(enabled = recap != null) { recap = null }
    val snack = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { if (permitted) vm.refresh(false) }
    LaunchedEffect(message, playback.error) {
        (message ?: playback.error)?.let {
            snack.showSnackbar(it)
            vm.message.value = null
        }
    }
    val detail by vm.selectedDetails.collectAsStateWithLifecycle()
    val audioOpen by vm.audioOpen.collectAsStateWithLifecycle()
    val tabs =
        settings.navigationTabs
            .split(',')
            .filter { it in listOf("home", "library", "playlists", "stats") }
            .distinct()
            .ifEmpty { listOf("home") }
    LaunchedEffect(tabs) {
        if (route in listOf("home", "library", "playlists", "stats") && route !in tabs)
            nav.navigate(tabs.first()) { launchSingleTop = true }
    }
    LadTheme(settings) {
        CompositionLocalProvider(LocalTrackDetails provides vm::showDetails) {
            val add: (Track) -> Unit = { addTrack = it }
            val showCollection: (String, List<Track>) -> Unit = { name, list ->
                collectionName = name
                collectionIds = list.map { it.id }.toLongArray()
                nav.navigate("collection")
            }
            val showArtist: (String, List<Track>) -> Unit = { name, list ->
                artistName = name
                artistIds = list.map { it.id }.toLongArray()
                nav.navigate("artist")
            }
            Scaffold(
                snackbarHost = { SnackbarHost(snack) },
                topBar = {
                    if (recap == null)
                        TopAppBar(
                            title = {
                                Text(
                                    when (route) {
                                        "home" -> strings[R.string.lad]
                                        "library" -> strings[R.string.library]
                                        "playlists" -> strings[R.string.playlists]
                                        "stats" -> strings[R.string.your_history]
                                        "settings" -> strings[R.string.settings]
                                        "search" -> strings[R.string.search]
                                        "collection" ->
                                            strings.label(collection.first).ifBlank {
                                                strings[R.string.music]
                                            }
                                        "artist" -> artistName
                                        "duplicates" -> strings[R.string.duplicates]
                                        else -> strings[R.string.playlist]
                                    },
                                    fontWeight = FontWeight.Black,
                                    style =
                                        if (route in tabs) MaterialTheme.typography.headlineLarge
                                        else MaterialTheme.typography.titleLarge,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                            navigationIcon = {
                                if (route !in listOf("home", "library", "playlists", "stats"))
                                    IconButton(
                                        onClick = {
                                            backDispatcher?.onBackPressed() ?: nav.popBackStack()
                                        }
                                    ) {
                                        Icon(
                                            Icons.AutoMirrored.Filled.ArrowBack,
                                            strings[R.string.back],
                                        )
                                    }
                            },
                            actions = {
                                if (route != "search" && route != "settings")
                                    IconButton(
                                        onClick = {
                                            nav.navigate("search") { launchSingleTop = true }
                                        }
                                    ) {
                                        Icon(Icons.Default.Search, strings[R.string.search])
                                    }
                                if (route != "settings")
                                    IconButton(
                                        onClick = {
                                            nav.navigate("settings") { launchSingleTop = true }
                                        }
                                    ) {
                                        Icon(Icons.Default.Settings, strings[R.string.settings])
                                    }
                            },
                            expandedHeight = if (route in tabs) 80.dp else 64.dp,
                            colors =
                                TopAppBarDefaults.topAppBarColors(
                                    containerColor = MaterialTheme.colorScheme.surface
                                ),
                        )
                },
                bottomBar = {
                    if (recap == null)
                        Column(Modifier.background(MaterialTheme.colorScheme.surface)) {
                            if (playback.current != null)
                                MiniPlayer(
                                    playback,
                                    onOpen = { playerOpen = true },
                                    onToggle = { vm.playback.toggle() },
                                    onNext = { vm.playback.next() },
                                    vm = vm,
                                )
                            if (route in listOf("home", "library", "playlists", "stats"))
                                FloatingDock(
                                    tabs,
                                    route,
                                    onSelect = { r ->
                                        nav.navigate(r) {
                                            popUpTo(nav.graph.startDestinationId) {
                                                saveState = true
                                            }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    },
                                )
                            else Spacer(Modifier.navigationBarsPadding())
                        }
                },
            ) { padding ->
                if (recap != null) {
                    val value = recap!!
                    RecapScreen(
                        value.first,
                        value.second,
                        value.third,
                        onClose = { recap = null },
                        animations = settings.animations,
                        settings = settings,
                        tracks = tracks,
                    )
                } else
                    NavHost(
                        navController = nav,
                        startDestination = "home",
                        enterTransition = {
                            fadeIn(LadMotion.defaultEffects(settings.animations)) +
                                slideInVertically(LadMotion.defaultSpatial(settings.animations)) {
                                    it / 16
                                }
                        },
                        exitTransition = {
                            fadeOut(LadMotion.fastEffects(settings.animations))
                        },
                        popEnterTransition = {
                            fadeIn(LadMotion.defaultEffects(settings.animations))
                        },
                        popExitTransition = {
                            fadeOut(LadMotion.fastEffects(settings.animations)) +
                                slideOutVertically(LadMotion.fastSpatial(settings.animations)) {
                                    it / 18
                                }
                        },
                        modifier = Modifier.padding(padding),
                    ) {
                        composable("home") {
                            val homeHistory by
                                vm.statisticsState.history.collectAsStateWithLifecycle()
                            HomeScreen(
                                tracks,
                                playlists,
                                homeHistory,
                                playback.current?.id,
                                vm,
                                permitted,
                                scanning,
                                permission,
                                onCollection = showCollection,
                                onAdd = add,
                                onPlaylist = { nav.navigate("playlist/$it") },
                                onDemo = {
                                    recap =
                                        Triple(
                                            RecapGenerator.demo(tracks),
                                            strings[R.string.demo_your_year],
                                            true,
                                        )
                                },
                            )
                        }
                        composable("library") {
                            LibraryScreen(
                                tracks,
                                playback.current?.id,
                                vm,
                                permitted,
                                scanning,
                                permission,
                                showCollection,
                                add,
                                onArtist = showArtist,
                            )
                        }
                        composable("playlists") {
                            PlaylistsScreen(
                                playlists,
                                tracks,
                                onSave = vm::savePlaylist,
                                onOpen = { nav.navigate("playlist/$it") },
                            )
                        }
                        composable("playlist/{id}") { e ->
                            val p =
                                playlists.firstOrNull {
                                    it.id == e.arguments?.getString("id")?.toLongOrNull()
                                }
                            if (p != null)
                                PlaylistScreen(
                                    p,
                                    tracks,
                                    playback.current?.id,
                                    vm,
                                    add,
                                    onDeleted = { nav.popBackStack() },
                                )
                        }
                        composable("artist") {
                            val rows = artistIds.asSequence().mapNotNull { lookup[it] }.toList()
                            val albums =
                                remember(rows, tracks) {
                                    val keys = rows.map { it.albumKey() }.toSet()
                                    tracks.filter { it.albumKey() in keys }
                                }
                            TrackListScreen(
                                artistName,
                                rows,
                                playback.current?.id,
                                vm,
                                add,
                                showCover = true,
                                onAlbum = showCollection,
                                albumTracks = albums,
                            )
                        }
                        composable("collection") {
                            collection.let { (name, list) ->
                                TrackListScreen(
                                    name,
                                    list.mapNotNull { old -> lookup[old.id] },
                                    playback.current?.id,
                                    vm,
                                    add,
                                    showCover = true,
                                )
                            }
                        }
                        composable("stats") {
                            val statsHistory by
                                vm.statisticsState.history.collectAsStateWithLifecycle()
                            val statsRuns by vm.statisticsState.runs.collectAsStateWithLifecycle()
                            StatisticsScreen(
                                statsHistory,
                                tracks,
                                runs = statsRuns,
                                onRecap = { s, label, year -> recap = Triple(s, label, year) },
                            )
                        }
                        composable("duplicates") { DuplicatesScreen(tracks, vm) }
                        composable("settings") {
                            SettingsScreen(
                                storedSettings,
                                vm,
                                onDuplicates = { nav.navigate("duplicates") },
                                onDemo = { year ->
                                    recap =
                                        Triple(
                                            RecapGenerator.demo(tracks),
                                            if (year) strings[R.string.demo_your_year]
                                            else strings[R.string.demo_your_summer],
                                            year,
                                        )
                                },
                            )
                        }
                        composable("search") {
                            SearchScreen(
                                tracks,
                                playlists,
                                playback.current?.id,
                                vm,
                                showCollection,
                                add,
                                onArtist = showArtist,
                                onPlaylist = { nav.navigate("playlist/$it") },
                            )
                        }
                    }
            }
            if (!playerOpen) {
                detail?.let {
                    val detailHistory by vm.statisticsState.history.collectAsStateWithLifecycle()
                    TrackDetailsPanel(it, detailHistory, vm) { vm.selectedDetails.value = null }
                }
                if (audioOpen) AudioPanel(settings, playback, vm) { vm.audioOpen.value = false }
            }
            if (playerOpen)
                FullPlayer(playback, tracks, settings, vm, onClose = { playerOpen = false })
            addTrack?.let { track ->
                AddPlaylistDialog(
                    track,
                    playlists,
                    onDismiss = { addTrack = null },
                    onAdd = { id ->
                        vm.addToPlaylist(id, listOf(track.id))
                        addTrack = null
                        scope.launch { snack.showSnackbar(strings[R.string.added_to_playlist]) }
                    },
                    onCreate = { name ->
                        vm.savePlaylist(Playlist(name = name, tracks = listOf(track.id)))
                        addTrack = null
                    },
                )
            }
        }
    }
}
