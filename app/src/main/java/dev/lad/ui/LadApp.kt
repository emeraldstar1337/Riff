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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.*
import dev.lad.R
import dev.lad.data.*
import dev.lad.i18n.rememberText
import dev.lad.statistics.*
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@Composable
fun LadApp(vm: LadViewModel = viewModel()) {
    val strings = rememberText()
    val storedSettings by vm.settings.collectAsStateWithLifecycle()
    val tracks by vm.tracks.collectAsStateWithLifecycle()
    val playlists by vm.playlists.collectAsStateWithLifecycle()
    val history by vm.history.collectAsStateWithLifecycle()
    val runs by vm.runs.collectAsStateWithLifecycle()
    val stablePlayback =
        remember(vm) {
            vm.playback.state
                .map { it.copy(positionMs = 0, positionTimestampMs = 0) }
                .distinctUntilChanged()
        }
    val initialPlayback =
        remember(vm) { vm.playback.state.value.copy(positionMs = 0, positionTimestampMs = 0) }
    val playback by stablePlayback.collectAsStateWithLifecycle(initialPlayback)
    val visible by vm.playback.uiVisible.collectAsStateWithLifecycle()
    val systemSaving by vm.playback.systemSaving.collectAsStateWithLifecycle()
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
    val scanning by vm.scanning.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val nav = rememberNavController()
    val backDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route ?: "home"
    var permitted by remember { mutableStateOf(vm.hasPermission()) }
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
    var collectionName by rememberSaveable { mutableStateOf("") }
    var collectionIds by rememberSaveable { mutableStateOf(longArrayOf()) }
    val lookup = remember(tracks) { tracks.associateBy { it.id } }
    val collection =
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
                                    onSearch = { nav.navigate("search") },
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
                    )
                } else
                    NavHost(
                        navController = nav,
                        startDestination = "home",
                        enterTransition = {
                            fadeIn(
                                tween(
                                    LadMotion.duration(LadMotion.expressive, settings.animations),
                                    easing = BitsEase,
                                )
                            ) +
                                slideInVertically(
                                    tween(
                                        LadMotion.duration(
                                            LadMotion.expressive,
                                            settings.animations,
                                        ),
                                        easing = BitsEase,
                                    )
                                ) {
                                    it / 14
                                }
                        },
                        exitTransition = {
                            fadeOut(tween(LadMotion.duration(LadMotion.quick, settings.animations)))
                        },
                        modifier = Modifier.padding(padding),
                    ) {
                        composable("home") {
                            HomeScreen(
                                tracks,
                                playlists,
                                history,
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
                            val p = playlists.firstOrNull {
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
                            StatisticsScreen(
                                history,
                                tracks,
                                runs = runs,
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
                                onPlaylist = { nav.navigate("playlist/$it") },
                            )
                        }
                    }
            }
            if (!playerOpen) {
                detail?.let {
                    TrackDetailsPanel(it, history, vm) { vm.selectedDetails.value = null }
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
