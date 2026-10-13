@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package dev.lad.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import dev.lad.R
import dev.lad.data.*
import dev.lad.domain.RepeatMode
import dev.lad.i18n.rememberText
import dev.lad.playback.PlaybackState
import kotlin.math.abs
import kotlinx.coroutines.launch

@Composable
fun MiniPlayer(
    state: PlaybackState,
    onOpen: () -> Unit,
    onToggle: () -> Unit,
    onNext: () -> Unit,
    vm: LadViewModel? = null,
) {
    val strings = rememberText()
    val track = state.current ?: return
    val settings = LocalLadSettings.current
    val seed = artworkSeed(track.artwork, settings.artworkColor)
    val palette =
        seed?.let { coverScheme(it, MaterialTheme.colorScheme) } ?: MaterialTheme.colorScheme
    val slim = settings.miniStyle == "slim"
    Surface(
        Modifier.fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .testTag("mini-player")
            .clickable(onClick = onOpen),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
    ) {
        Column(Modifier.padding(horizontal = 12.dp)) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = if (slim) 56.dp else LadSpacing.mini),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (!slim)
                    Crossfade(
                        track.artwork,
                        animationSpec =
                            tween(LadMotion.duration(LadMotion.standard, settings.animations)),
                        label = "mini cover",
                    ) { uri ->
                        Artwork(uri, track.title, Modifier.size(44.dp), 12)
                    }
                Column(Modifier.weight(1f)) {
                    Text(
                        strings.metadata(track.title),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        strings.metadata(track.artist),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (settings.miniStyle == "expanded") MiniTime(state, palette, vm)
                }
                FilledIconButton(
                    onToggle,
                    Modifier.size(48.dp),
                    colors =
                        IconButtonDefaults.filledIconButtonColors(
                            containerColor = palette.primary,
                            contentColor = palette.onPrimary,
                        ),
                ) {
                    if (state.loading)
                        CircularProgressIndicator(
                            Modifier.size(20.dp),
                            color = palette.onPrimary,
                            strokeWidth = 2.dp,
                        )
                    else
                        Icon(
                            if (state.playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                            if (state.playing) strings[R.string.pause]
                            else strings[R.string.library_screens_play],
                        )
                }
                IconButton(onNext, Modifier.size(48.dp)) {
                    Icon(Icons.Default.SkipNext, strings[R.string.next])
                }
            }
            MiniProgress(state, palette, vm)
        }
    }
}

@Composable
private fun LiveMeter(vm: LadViewModel) {
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        fun update() {
            vm.playback.meterVisible.value =
                owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        }
        val observer = LifecycleEventObserver { _, _ -> update() }
        owner.lifecycle.addObserver(observer)
        update()
        onDispose {
            owner.lifecycle.removeObserver(observer)
            vm.playback.meterVisible.value = false
        }
    }
    val levels by vm.playback.levels.collectAsStateWithLifecycle()
    SoundWave(levels, true, Modifier.size(24.dp, 14.dp))
}

@Composable
fun SoundWave(levels: List<Float>, playing: Boolean, modifier: Modifier = Modifier) {
    val color = MaterialTheme.colorScheme.primary
    val enabled = LocalLadSettings.current.animations
    val bars =
        (0 until 7).map { i ->
            val target = if (playing) levels.drop(i * 3).take(3).maxOrNull() ?: 0f else 0f
            val value by
                animateFloatAsState(
                    target,
                    tween(if (enabled) LadMotion.quick else 0),
                    label = "PCM envelope",
                )
            value
        }
    Canvas(modifier) {
        if ((bars.maxOrNull() ?: 0f) <= 0.001f) return@Canvas
        val gap = size.width / bars.size
        bars.forEachIndexed { i, value ->
            val h = size.height * value.coerceIn(0.12f, 1f)
            drawRoundRect(
                color,
                Offset(i * gap, (size.height - h) / 2),
                androidx.compose.ui.geometry.Size(gap * 0.55f, h),
                androidx.compose.ui.geometry.CornerRadius(gap / 2),
            )
        }
    }
}

@Composable
internal fun PlayerTabs(lyrics: Boolean, onChange: (Boolean) -> Unit) {
    val strings = rememberText()
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
        modifier =
            Modifier.widthIn(max = 280.dp).fillMaxWidth().height(48.dp).testTag("player-tabs"),
    ) {
        Row(
            Modifier.fillMaxSize().padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            listOf(false, true).forEach { value ->
                val selected = value == lyrics
                Row(
                    Modifier.weight(1f)
                        .fillMaxHeight()
                        .clip(CircleShape)
                        .background(
                            if (selected) MaterialTheme.colorScheme.primaryContainer
                            else Color.Transparent
                        )
                        .selectable(selected, onClick = { onChange(value) }, role = Role.Tab)
                        .testTag(if (value) "lyrics-tab" else "artwork-tab"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement =
                        Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                ) {
                    CompositionLocalProvider(
                        LocalContentColor provides
                            if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                            else MaterialTheme.colorScheme.onSurfaceVariant
                    ) {
                        Icon(
                            if (value) Icons.AutoMirrored.Filled.Notes else Icons.Default.Album,
                            null,
                            Modifier.size(16.dp),
                        )
                        Text(
                            if (value) strings[R.string.player_screen_lyrics]
                            else strings[R.string.player_screen_artwork],
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun FullPlayer(
    state: PlaybackState,
    tracks: List<Track>,
    settings: Settings,
    vm: LadViewModel,
    onClose: () -> Unit,
) {
    val strings = rememberText()
    val track = tracks.firstOrNull { it.id == state.current?.id } ?: state.current ?: return
    val seed = artworkSeed(track.artwork, settings.artworkColor)
    val base = MaterialTheme.colorScheme
    val target = seed?.let { coverScheme(it, base) } ?: base
    val background by
        animateColorAsState(
            target.surface,
            tween(LadMotion.duration(LadMotion.palette, settings.animations)),
            label = "album surface",
        )
    val accent by
        animateColorAsState(
            target.primary,
            tween(LadMotion.duration(LadMotion.palette, settings.animations)),
            label = "album accent",
        )
    val scheme = target.copy(surface = background, primary = accent)
    var queueOpen by remember { mutableStateOf(false) }
    val audioDialog by vm.audioOpen.collectAsStateWithLifecycle()
    val detailDialog by vm.selectedDetails.collectAsStateWithLifecycle()
    var lyricsTab by rememberSaveable { mutableStateOf(false) }
    var sleepOpen by remember { mutableStateOf(false) }
    var options by remember { mutableStateOf(false) }
    val mediaVisible = !queueOpen && !audioDialog && detailDialog == null && !options && !sleepOpen
    val owner = LocalLifecycleOwner.current
    var lifecycle by remember(owner) { mutableStateOf(owner.lifecycle.currentState) }
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, _ -> lifecycle = owner.lifecycle.currentState }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    val rotation = remember { Animatable(0f) }
    LaunchedEffect(
        settings.vinyl,
        settings.animations,
        state.playing,
        lyricsTab,
        queueOpen,
        audioDialog,
        detailDialog,
        options,
        sleepOpen,
        lifecycle,
    ) {
        if (
            settings.vinyl &&
                settings.animations &&
                state.playing &&
                !lyricsTab &&
                mediaVisible &&
                lifecycle.isAtLeast(Lifecycle.State.STARTED)
        )
            while (true) {
                if (
                    (kotlinx.coroutines
                        .currentCoroutineContext()[androidx.compose.ui.MotionDurationScale]
                        ?.scaleFactor ?: 1f) == 0f
                )
                    break
                rotation.animateTo(
                    360f,
                    tween(
                        ((360f - rotation.value) / 360 * 22000).toInt().coerceAtLeast(1),
                        easing = LinearEasing,
                    ),
                )
                rotation.snapTo(0f)
            }
    }
    val reveal = remember { Animatable(if (settings.animations) 0f else 1f) }
    val scope = rememberCoroutineScope()
    var closing by remember { mutableStateOf(false) }
    val closePlayer: () -> Unit = {
        if (!closing) {
            closing = true
            scope.launch {
                reveal.animateTo(
                    0f,
                    tween(
                        LadMotion.duration(LadMotion.quick, settings.animations),
                        easing = LadMotion.easing,
                    ),
                )
                onClose()
            }
        }
    }
    LaunchedEffect(Unit) {
        reveal.animateTo(
            1f,
            tween(
                LadMotion.duration(LadMotion.container, settings.animations),
                easing = LadMotion.easing,
            ),
        )
    }
    val playCorner by
        animateDpAsState(
            if (state.playing) 24.dp else 38.dp,
            tween(LadMotion.duration(LadMotion.quick, settings.animations)),
            label = "play shape",
        )
    Dialog(
        onDismissRequest = onClose,
        properties =
            DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        val view = LocalView.current
        SideEffect {
            (view.parent as? DialogWindowProvider)?.window?.let { window ->
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = background.luminance() > 0.5f
                    isAppearanceLightNavigationBars = background.luminance() > 0.5f
                }
                window.statusBarColor = android.graphics.Color.TRANSPARENT
                window.navigationBarColor = android.graphics.Color.TRANSPARENT
                window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            }
        }
        CompositionLocalProvider(LocalLadSettings provides settings) {
            MaterialTheme(colorScheme = scheme) {
                Surface(
                    Modifier.fillMaxSize().graphicsLayer {
                        translationY = (1 - reveal.value) * size.height * 0.18f
                        alpha = reveal.value
                    },
                    color = background,
                ) {
                    AmbientBackdrop(
                        seed ?: accent,
                        artwork = track.artwork,
                        enabled = settings.ambientBackground,
                        animations = settings.animations && state.playing && mediaVisible,
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        BoxWithConstraints(
                            Modifier.widthIn(max = 640.dp)
                                .fillMaxSize()
                                .windowInsetsPadding(WindowInsets.safeDrawing)
                                .align(Alignment.Center)
                        ) {
                            val fontScale = LocalDensity.current.fontScale
                            val scroll = maxHeight < 620.dp || fontScale > 1.35f
                            val lyricsHeight = (maxHeight * 0.6f).coerceAtLeast(280.dp)
                            val compactFooter = maxWidth < 360.dp || fontScale > 1.2f
                            val mainButton = if (maxWidth < 360.dp) 64.dp else 76.dp
                            val mediaHeight =
                                minOf(maxWidth - 40.dp, (maxHeight * 0.32f).coerceAtLeast(168.dp))
                            Column(
                                Modifier.fillMaxSize()
                                    .then(
                                        if (scroll) Modifier.verticalScroll(rememberScrollState())
                                        else Modifier
                                    )
                                    .padding(horizontal = 20.dp, vertical = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Row(
                                    Modifier.fillMaxWidth().heightIn(min = 48.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    IconButton(closePlayer) {
                                        Icon(
                                            Icons.Default.KeyboardArrowDown,
                                            strings[R.string.minimize],
                                        )
                                    }
                                    Column(
                                        Modifier.weight(1f),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        ) {
                                            if (
                                                settings.visualizer && state.playing && mediaVisible
                                            )
                                                LiveMeter(vm)
                                            Text(
                                                if (state.loading) strings[R.string.loading_track]
                                                else if (state.playing)
                                                    strings[R.string.now_playing]
                                                else strings[R.string.paused],
                                                style = MaterialTheme.typography.labelSmall,
                                                color = accent,
                                            )
                                        }
                                        Text(
                                            strings.label(state.queue.context),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            style = MaterialTheme.typography.labelLarge,
                                        )
                                    }
                                    Box {
                                        IconButton({ options = true }) {
                                            Icon(
                                                Icons.Default.Tune,
                                                strings[R.string.player_actions],
                                            )
                                        }
                                        if (options)
                                            ExpressiveActions(
                                                strings[R.string.your_music],
                                                strings[R.string.current_track_controls],
                                                { options = false },
                                                listOf(
                                                    PlayerAction(
                                                        strings[
                                                            R.string.track_information_and_tags],
                                                        strings[
                                                            R.string.file_artwork_and_statistics],
                                                        Icons.Default.Info,
                                                    ) {
                                                        options = false
                                                        vm.showDetails(track)
                                                    },
                                                    PlayerAction(
                                                        strings[R.string.sleep_timer],
                                                        strings[
                                                            R.string
                                                                .music_stops_when_the_timer_ends],
                                                        Icons.Default.Bedtime,
                                                    ) {
                                                        options = false
                                                        sleepOpen = true
                                                    },
                                                    PlayerAction(
                                                        strings[R.string.sound_and_equalizer],
                                                        strings[R.string.current_device_profile],
                                                        Icons.Default.Tune,
                                                    ) {
                                                        options = false
                                                        vm.audioOpen.value = true
                                                    },
                                                ),
                                            )
                                    }
                                }
                                PlayerTabs(lyricsTab) { lyricsTab = it }
                                AnimatedContent(
                                    targetState = lyricsTab,
                                    modifier =
                                        Modifier.fillMaxWidth()
                                            .then(
                                                if (scroll)
                                                    Modifier.height(
                                                        if (lyricsTab) lyricsHeight else mediaHeight
                                                    )
                                                else Modifier.weight(1f)
                                            ),
                                    transitionSpec = {
                                        (fadeIn(
                                                tween(
                                                    LadMotion.duration(
                                                        LadMotion.standard,
                                                        settings.animations,
                                                    )
                                                )
                                            ) +
                                                slideInVertically(
                                                    tween(
                                                        LadMotion.duration(
                                                            LadMotion.standard,
                                                            settings.animations,
                                                        )
                                                    )
                                                ) {
                                                    it / 24
                                                })
                                            .togetherWith(
                                                fadeOut(
                                                    tween(
                                                        LadMotion.duration(
                                                            LadMotion.quick,
                                                            settings.animations,
                                                        )
                                                    )
                                                )
                                            )
                                            .using(SizeTransform(clip = true))
                                    },
                                    label = "cover lyrics tab",
                                ) { lyrics ->
                                    if (lyrics)
                                        key(track.id) {
                                            LyricsContent(
                                                track,
                                                state,
                                                vm,
                                                Modifier.fillMaxSize(),
                                                visible = mediaVisible,
                                            )
                                        }
                                    else
                                        BoxWithConstraints(
                                            Modifier.fillMaxSize(),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            val limit =
                                                when (settings.playerStyle) {
                                                    "minimal" -> 112.dp
                                                    "classic",
                                                    "compact" -> 242.dp
                                                    else -> maxWidth
                                                }
                                            val side =
                                                minOf(maxWidth, maxHeight, limit)
                                                    .coerceAtLeast(0.dp)
                                            Box(
                                                Modifier.size(side)
                                                    .testTag("player-artwork")
                                                    .pointerInput(track.id) {
                                                        var distance = Offset.Zero
                                                        val threshold = 60.dp.toPx()
                                                        detectDragGestures(
                                                            onDragStart = {
                                                                distance = Offset.Zero
                                                            },
                                                            onDrag = { change, amount ->
                                                                change.consume()
                                                                distance += amount
                                                            },
                                                            onDragEnd = {
                                                                if (
                                                                    distance.y > threshold &&
                                                                        distance.y > abs(distance.x)
                                                                )
                                                                    closePlayer()
                                                                else if (distance.x < -threshold)
                                                                    vm.playback.next()
                                                                else if (distance.x > threshold)
                                                                    vm.playback.previous()
                                                            },
                                                        )
                                                    }
                                                    .pointerInput(track.id) {
                                                        detectTapGestures(
                                                            onDoubleTap = { vm.favorite(track) }
                                                        )
                                                    }
                                            ) {
                                                Crossfade(
                                                    track.artwork,
                                                    animationSpec =
                                                        tween(
                                                            LadMotion.duration(
                                                                LadMotion.expressive,
                                                                settings.animations,
                                                            )
                                                        ),
                                                    label = "player cover",
                                                ) { uri ->
                                                    Box(
                                                        Modifier.fillMaxSize().graphicsLayer {
                                                            rotationZ =
                                                                if (settings.vinyl) rotation.value
                                                                else 0f
                                                        }
                                                    ) {
                                                        Artwork(
                                                            uri,
                                                            track.title,
                                                            Modifier.fillMaxSize(),
                                                            if (settings.vinyl) 1000 else 24,
                                                        )
                                                        if (settings.vinyl)
                                                            Box(
                                                                Modifier.align(Alignment.Center)
                                                                    .size(20.dp)
                                                                    .background(
                                                                        background,
                                                                        CircleShape,
                                                                    )
                                                            )
                                                    }
                                                }
                                            }
                                        }
                                }
                                Row(
                                    Modifier.fillMaxWidth().padding(top = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            strings.metadata(track.title),
                                            style = MaterialTheme.typography.headlineLarge,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Text(
                                            strings.metadata(track.artist),
                                            style = MaterialTheme.typography.titleMedium,
                                            color = scheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                    IconButton({ vm.favorite(track) }) {
                                        Icon(
                                            if (track.favorite) Icons.Default.Favorite
                                            else Icons.Default.FavoriteBorder,
                                            strings[R.string.favourites],
                                            tint = accent,
                                        )
                                    }
                                }
                                FullPlayerTimeline(track, state, vm)
                                Surface(
                                    shape = LadShapes.hero,
                                    color = scheme.surfaceContainerLow.copy(alpha = 0.88f),
                                    border =
                                        BorderStroke(
                                            1.dp,
                                            scheme.outlineVariant.copy(alpha = 0.5f),
                                        ),
                                ) {
                                    Row(
                                        Modifier.fillMaxWidth()
                                            .padding(horizontal = 4.dp, vertical = 12.dp),
                                        horizontalArrangement = Arrangement.SpaceEvenly,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        IconToggleButton(
                                            state.queue.shuffle,
                                            { vm.playback.shuffle(it) },
                                            Modifier.size(48.dp),
                                        ) {
                                            Icon(
                                                Icons.Default.Shuffle,
                                                strings[R.string.library_screens_shuffle],
                                                tint =
                                                    if (state.queue.shuffle) accent
                                                    else scheme.onSurfaceVariant,
                                            )
                                        }
                                        IconButton(
                                            { vm.playback.previous() },
                                            Modifier.size(48.dp),
                                        ) {
                                            Icon(
                                                Icons.Default.SkipPrevious,
                                                strings[R.string.previous],
                                                Modifier.size(28.dp),
                                            )
                                        }
                                        FilledIconButton(
                                            { vm.playback.toggle() },
                                            Modifier.size(mainButton),
                                            shape = RoundedCornerShape(playCorner),
                                        ) {
                                            if (state.loading)
                                                CircularProgressIndicator(
                                                    Modifier.size(28.dp),
                                                    color = scheme.onPrimary,
                                                    strokeWidth = 2.dp,
                                                )
                                            else
                                                Crossfade(
                                                    state.playing,
                                                    animationSpec =
                                                        tween(
                                                            LadMotion.duration(
                                                                LadMotion.quick,
                                                                settings.animations,
                                                            )
                                                        ),
                                                    label = "play state",
                                                ) { playing ->
                                                    Icon(
                                                        if (playing) Icons.Default.Pause
                                                        else Icons.Default.PlayArrow,
                                                        if (playing) strings[R.string.pause]
                                                        else strings[R.string.library_screens_play],
                                                        Modifier.size(36.dp),
                                                    )
                                                }
                                        }
                                        IconButton({ vm.playback.next() }, Modifier.size(48.dp)) {
                                            Icon(
                                                Icons.Default.SkipNext,
                                                strings[R.string.next],
                                                Modifier.size(28.dp),
                                            )
                                        }
                                        IconToggleButton(
                                            state.queue.repeat != RepeatMode.OFF,
                                            { vm.playback.repeat() },
                                            Modifier.size(48.dp),
                                        ) {
                                            Icon(
                                                if (state.queue.repeat == RepeatMode.ONE)
                                                    Icons.Default.RepeatOne
                                                else Icons.Default.Repeat,
                                                strings.format(
                                                    R.string.repeat,
                                                    strings[
                                                        when (state.queue.repeat) {
                                                            RepeatMode.OFF -> R.string.off
                                                            RepeatMode.ONE -> R.string.repeat_one
                                                            RepeatMode.QUEUE -> R.string.queue
                                                        }],
                                                ),
                                                tint =
                                                    if (state.queue.repeat != RepeatMode.OFF) accent
                                                    else scheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                }
                                Column(Modifier.fillMaxWidth()) {
                                    Row(
                                        Modifier.fillMaxWidth().heightIn(min = 52.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        if (!compactFooter)
                                            IconButton({
                                                vm.playback.seek(
                                                    (vm.playback.state.value.positionMs - 10000)
                                                        .coerceAtLeast(0)
                                                )
                                            }) {
                                                Icon(
                                                    Icons.Default.Replay10,
                                                    strings[R.string.back_10_seconds],
                                                )
                                            }
                                        TextButton(
                                            { vm.audioOpen.value = true },
                                            Modifier.weight(1f),
                                        ) {
                                            Text(strings[R.string.sound])
                                        }
                                        TextButton({ queueOpen = true }, Modifier.weight(1f)) {
                                            Column(
                                                horizontalAlignment = Alignment.CenterHorizontally
                                            ) {
                                                Text(strings[R.string.queue])
                                                Text(
                                                    "${state.queue.position} / ${state.queue.size}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                )
                                            }
                                        }
                                        if (!compactFooter)
                                            IconButton({
                                                vm.playback.seek(
                                                    (vm.playback.state.value.positionMs + 10000)
                                                        .coerceAtMost(state.durationMs)
                                                )
                                            }) {
                                                Icon(
                                                    Icons.Default.Forward10,
                                                    strings[R.string.forward_10_seconds],
                                                )
                                            }
                                    }
                                    if (compactFooter)
                                        Row(
                                            Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceEvenly,
                                        ) {
                                            IconButton({
                                                vm.playback.seek(
                                                    (vm.playback.state.value.positionMs - 10000)
                                                        .coerceAtLeast(0)
                                                )
                                            }) {
                                                Icon(
                                                    Icons.Default.Replay10,
                                                    strings[R.string.back_10_seconds],
                                                )
                                            }
                                            IconButton({
                                                vm.playback.seek(
                                                    (vm.playback.state.value.positionMs + 10000)
                                                        .coerceAtMost(state.durationMs)
                                                )
                                            }) {
                                                Icon(
                                                    Icons.Default.Forward10,
                                                    strings[R.string.forward_10_seconds],
                                                )
                                            }
                                        }
                                }
                                if (state.sleepUntil > 0)
                                    SleepChip(state.sleepUntil) { sleepOpen = true }
                            }
                        }
                    }
                }
                if (queueOpen)
                    QueueSheet(
                        state,
                        tracks,
                        vm.playback::select,
                        vm.playback::move,
                        vm.playback::remove,
                        vm.playback::restoreQueue,
                    ) {
                        queueOpen = false
                    }
                val details by vm.selectedDetails.collectAsStateWithLifecycle()
                val audio by vm.audioOpen.collectAsStateWithLifecycle()
                val history by vm.history.collectAsStateWithLifecycle()
                details?.let {
                    TrackDetailsPanel(it, history, vm) { vm.selectedDetails.value = null }
                }
                if (audio) AudioPanel(settings, state, vm) { vm.audioOpen.value = false }
                if (sleepOpen)
                    AlertDialog(
                        onDismissRequest = { sleepOpen = false },
                        title = { Text(strings[R.string.sleep_timer]) },
                        text = {
                            Column {
                                listOf(15, 30, 45, 60, 90).forEach { n ->
                                    TextButton({
                                        vm.playback.sleep(n)
                                        sleepOpen = false
                                    }) {
                                        Text(strings.format(R.string.minutes, n))
                                    }
                                }
                            }
                        },
                        confirmButton = {
                            TextButton({
                                vm.playback.sleep(0)
                                sleepOpen = false
                            }) {
                                Text(strings[R.string.turn_off])
                            }
                        },
                        dismissButton = {
                            TextButton({ sleepOpen = false }) { Text(strings[R.string.close]) }
                        },
                    )
            }
        }
    }
}

@Composable
internal fun QueueSheet(
    state: PlaybackState,
    tracks: List<Track>,
    onSelect: (Long) -> Unit,
    onMove: (Int, Int) -> Unit,
    onRemove: (Int) -> Unit,
    onRestore: (Long) -> Unit = {},
    onClose: () -> Unit,
) {
    val strings = rememberText()
    val lookup = remember(tracks) { tracks.associateBy { it.id } }
    val upcoming = state.queue.entries.drop(state.queue.index + 1)
    var historyOpen by remember { mutableStateOf(false) }
    if (historyOpen)
        AlertDialog(
            onDismissRequest = { historyOpen = false },
            title = { Text(strings[R.string.queue_history]) },
            text = {
                LazyColumn(Modifier.heightIn(max = 440.dp)) {
                    items(state.queueHistory.asReversed(), key = { it.id }) { revision ->
                        TextButton({
                            onRestore(revision.id)
                            historyOpen = false
                        }) {
                            Column(Modifier.fillMaxWidth()) {
                                Text(strings.label(revision.label))
                                Text(
                                    strings.format(
                                        R.string.library_screens_tracks_205,
                                        revision.snapshot.entries.size,
                                        java.text.DateFormat.getTimeInstance(
                                                java.text.DateFormat.SHORT
                                            )
                                            .format(java.util.Date(revision.createdAt)),
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton({ historyOpen = false }) { Text(strings[R.string.close]) }
            },
        )
    PlayerSheet(
        strings[R.string.queue],
        strings.format(
            R.string.up_next,
            upcoming.size,
            if (state.queue.shuffle) strings[R.string.shuffled] else strings[R.string.in_order],
        ),
        "queue",
        onClose,
    ) {
        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    { state.queueHistory.lastOrNull()?.let { onRestore(it.id) } },
                    enabled = state.queueHistory.isNotEmpty(),
                ) {
                    Icon(Icons.AutoMirrored.Filled.Undo, null, Modifier.size(18.dp))
                    Text(strings[R.string.undo])
                }
                TextButton({ historyOpen = true }, enabled = state.queueHistory.isNotEmpty()) {
                    Icon(Icons.Default.History, null, Modifier.size(18.dp))
                    Text(strings[R.string.player_screen_history])
                }
            }
            state.queue.entries.getOrNull(state.queue.index)?.let { entry ->
                lookup[entry.trackId]?.let { current ->
                    Surface(
                        Modifier.fillMaxWidth()
                            .padding(top = 16.dp, bottom = 12.dp)
                            .testTag("queue-current"),
                        shape = RoundedCornerShape(24.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        border =
                            BorderStroke(
                                1.dp,
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                            ),
                    ) {
                        Row(
                            Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Artwork(current.artwork, current.title, Modifier.size(56.dp), 16)
                            Column(
                                Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(3.dp),
                            ) {
                                Text(
                                    if (state.playing) strings[R.string.now_playing]
                                    else strings[R.string.current_track],
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                Text(
                                    current.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    current.artist,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Text(
                                duration(current.durationMs),
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
                }
            }
            if (upcoming.isEmpty())
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.QueueMusic,
                            null,
                            Modifier.size(36.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            strings[R.string.nothing_up_next],
                            style = MaterialTheme.typography.titleLarge,
                        )
                        Text(
                            strings[R.string.choose_a_track_in_the_library],
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            else
                LazyColumn(
                    Modifier.fillMaxWidth().weight(1f).clipToBounds().testTag("queue-list"),
                    contentPadding = PaddingValues(top = 4.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    itemsIndexed(
                        upcoming,
                        key = { n, entry -> "${entry.cycle}:${entry.trackId}:${entry.manual}" },
                    ) { n, entry ->
                        val index = state.queue.index + n + 1
                        lookup[entry.trackId]?.let { t ->
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                border =
                                    BorderStroke(
                                        1.dp,
                                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                                    ),
                            ) {
                                Row(
                                    Modifier.fillMaxWidth()
                                        .padding(
                                            start = 12.dp,
                                            end = 4.dp,
                                            top = 10.dp,
                                            bottom = 10.dp,
                                        ),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    Row(
                                        Modifier.weight(1f).clickable { onSelect(t.id) },
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    ) {
                                        Artwork(t.artwork, t.title, Modifier.size(46.dp), 14)
                                        Column(
                                            Modifier.weight(1f),
                                            verticalArrangement = Arrangement.spacedBy(3.dp),
                                        ) {
                                            Text(
                                                strings.metadata(t.title),
                                                style = MaterialTheme.typography.titleMedium,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                            Text(
                                                strings.metadata(t.artist),
                                                style = MaterialTheme.typography.bodySmall,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                            if (entry.manual)
                                                Text(
                                                    strings[R.string.manual_once],
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.primary,
                                                )
                                        }
                                    }
                                    var menu by remember { mutableStateOf(false) }
                                    Box {
                                        IconButton({ menu = true }) {
                                            Icon(
                                                Icons.Default.MoreVert,
                                                strings.format(R.string.queue_actions, t.title),
                                            )
                                        }
                                        DropdownMenu(menu, { menu = false }) {
                                            if (
                                                index > state.queue.index + 1 &&
                                                    state.queue.entries[index - 1].cycle ==
                                                        entry.cycle
                                            )
                                                DropdownMenuItem(
                                                    text = { Text(strings[R.string.move_up]) },
                                                    leadingIcon = {
                                                        Icon(Icons.Default.KeyboardArrowUp, null)
                                                    },
                                                    onClick = {
                                                        menu = false
                                                        onMove(index, index - 1)
                                                    },
                                                )
                                            if (
                                                index < state.queue.entries.lastIndex &&
                                                    state.queue.entries[index + 1].cycle ==
                                                        entry.cycle
                                            )
                                                DropdownMenuItem(
                                                    text = { Text(strings[R.string.move_down]) },
                                                    leadingIcon = {
                                                        Icon(Icons.Default.KeyboardArrowDown, null)
                                                    },
                                                    onClick = {
                                                        menu = false
                                                        onMove(index, index + 1)
                                                    },
                                                )
                                            DropdownMenuItem(
                                                text = {
                                                    Text(strings[R.string.remove_from_queue])
                                                },
                                                leadingIcon = { Icon(Icons.Default.Close, null) },
                                                onClick = {
                                                    menu = false
                                                    onRemove(index)
                                                },
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
}

@Composable
private fun FullPlayerTimeline(track: Track, state: PlaybackState, vm: LadViewModel) {
    val strings = rememberText()
    val clock by vm.playback.state.collectAsStateWithLifecycle()
    var dragging by remember(track.id) { mutableStateOf(false) }
    var seek by remember(track.id) { mutableFloatStateOf(0f) }
    val scheme = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth()) {
        SeekBar(
            if (dragging) seek
            else
                clock.positionMs
                    .toFloat()
                    .coerceIn(0f, state.durationMs.coerceAtLeast(1).toFloat()),
            state.durationMs.coerceAtLeast(1).toFloat(),
            {
                dragging = true
                seek = it
            },
            {
                vm.playback.seek(seek.toLong())
                dragging = false
            },
            playing = clock.playing,
            positionTimestampMs = clock.positionTimestampMs,
            speed = LocalLadSettings.current.speed,
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                duration(if (dragging) seek.toLong() else clock.positionMs),
                style = MaterialTheme.typography.labelMedium,
                color = scheme.onSurfaceVariant,
            )
            Text(
                track.mimeType.substringAfter('/').uppercase().ifBlank { strings[R.string.audio] },
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurfaceVariant,
            )
            Text(
                duration(state.durationMs),
                style = MaterialTheme.typography.labelMedium,
                color = scheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun MiniProgress(state: PlaybackState, palette: ColorScheme, vm: LadViewModel?) {
    val clock = if (vm != null) vm.playback.state.collectAsStateWithLifecycle().value else state
    val progress =
        if (clock.durationMs > 0) (clock.positionMs.toFloat() / clock.durationMs).coerceIn(0f, 1f)
        else 0f
    Canvas(Modifier.fillMaxWidth().height(2.dp)) {
        drawLine(
            palette.primary.copy(alpha = 0.13f),
            Offset.Zero,
            Offset(size.width, 0f),
            strokeWidth = size.height,
        )
        drawLine(
            palette.primary,
            Offset.Zero,
            Offset(size.width * progress, 0f),
            strokeWidth = size.height,
        )
    }
}

@Composable
private fun MiniTime(state: PlaybackState, palette: ColorScheme, vm: LadViewModel?) {
    val clock = if (vm != null) vm.playback.state.collectAsStateWithLifecycle().value else state
    Text(
        "${duration(clock.positionMs)} · ${duration(clock.durationMs)}",
        style = MaterialTheme.typography.labelSmall,
        color = palette.primary,
    )
}

@Composable
private fun SleepChip(until: Long, onClick: () -> Unit) {
    val strings = rememberText()
    val owner = LocalLifecycleOwner.current
    val remaining by
        produceState(
            (until - android.os.SystemClock.elapsedRealtime()).coerceAtLeast(0),
            until,
            owner,
        ) {
            owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) {
                    value = (until - android.os.SystemClock.elapsedRealtime()).coerceAtLeast(0)
                    if (value == 0L) break
                    kotlinx.coroutines.delay(1000)
                }
            }
        }
    TextButton(onClick) {
        Icon(Icons.Default.Bedtime, null, Modifier.size(16.dp))
        Text(strings.format(R.string.timer, duration(remaining)))
    }
}
