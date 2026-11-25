@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class,
)

package dev.lad.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
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
import coil.imageLoader
import coil.request.ImageRequest
import dev.lad.R
import dev.lad.data.*
import dev.lad.domain.RepeatMode
import dev.lad.i18n.rememberText
import dev.lad.playback.PlaybackState
import dev.lad.preferHighestRefreshRate
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
    val seed = trackSeed(track, settings.artworkColor)
    val base = MaterialTheme.colorScheme
    val palette = remember(seed, base) { seed?.let { coverScheme(it, base) } ?: base }
    val slim = settings.miniStyle == "slim"
    Surface(
        Modifier.fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .testTag("mini-player")
            .clickable(onClick = onOpen),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
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
                        animationSpec = LadMotion.defaultEffects(settings.animations),
                        label = "mini cover",
                    ) { uri ->
                        // Concentric nested radius: 28dp outer - 12dp padding = 16dp
                        Artwork(uri, track.title, Modifier.size(44.dp), 16)
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
private fun LiveMeter(vm: LadViewModel, playing: Boolean) {
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner, playing) {
        fun update() {
            vm.playback.meterVisible.value =
                playing && owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
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
    SoundWave(levels, playing, Modifier.size(30.dp, 14.dp))
}

@Composable
fun SoundWave(levels: List<Float>, playing: Boolean, modifier: Modifier = Modifier) {
    val color = MaterialTheme.colorScheme.primary
    val enabled = LocalLadSettings.current.animations
    val owner = LocalLifecycleOwner.current
    // 9 symmetrical center-weighted bars smoothed at native VSync (60/90/120Hz) with zero recomposition
    val smoothed = remember { FloatArray(9) { 0.14f } }
    val frameTick = remember { mutableIntStateOf(0) }
    val latestLevels by rememberUpdatedState(levels)
    val latestPlaying by rememberUpdatedState(playing)
    val latestEnabled by rememberUpdatedState(enabled)

    LaunchedEffect(owner) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            var prevNanos = 0L
            // Center-symmetric band mapping: edges = highs/mids, center = warm bass/low-mids
            val bandIndices = intArrayOf(16, 11, 7, 3, 1, 4, 8, 12, 17)
            val weights = floatArrayOf(0.52f, 0.68f, 0.84f, 0.96f, 1.0f, 0.96f, 0.84f, 0.68f, 0.52f)
            while (
                kotlinx.coroutines.currentCoroutineContext()[
                        androidx.compose.ui.MotionDurationScale]
                    ?.scaleFactor ?: 1f > 0f
            ) {
                withFrameNanos { now ->
                    val dt =
                        if (prevNanos == 0L) 0.016f
                        else ((now - prevNanos) / 1_000_000_000f).coerceIn(0.001f, 0.08f)
                    prevNanos = now
                    val raw = latestLevels
                    val active = latestPlaying && raw.isNotEmpty()
                    val attack = if (latestEnabled) (dt * 18f).coerceIn(0.08f, 0.48f) else 1f
                    val release = if (latestEnabled) (dt * 7.5f).coerceIn(0.04f, 0.24f) else 1f
                    for (i in 0 until 9) {
                        val b = bandIndices[i]
                        val sample =
                            if (active) {
                                val a = raw.getOrNull(b) ?: 0f
                                val c = raw.getOrNull((b + 1).coerceAtMost(raw.lastIndex)) ?: a
                                ((a + c) * 0.5f * weights[i]).coerceIn(0.14f, 1f)
                            } else 0.14f
                        val k = if (sample > smoothed[i]) attack else release
                        smoothed[i] += (sample - smoothed[i]) * k
                    }
                    frameTick.intValue++
                }
            }
        }
    }

    Canvas(modifier) {
        frameTick.intValue // subscribe draw scope only
        val count = 9
        val barWidth = (size.width / (count * 1.85f)).coerceAtLeast(1.5.dp.toPx())
        val totalBarsWidth = barWidth * count
        val gap = ((size.width - totalBarsWidth) / (count - 1)).coerceAtLeast(1.dp.toPx())
        val startX = (size.width - (totalBarsWidth + gap * (count - 1))) / 2f
        for (i in 0 until count) {
            val v = smoothed[i].coerceIn(0.14f, 1f)
            val h = (size.height * v).coerceAtLeast(barWidth)
            val x = startX + i * (barWidth + gap) + barWidth / 2f
            val topY = (size.height - h) / 2f
            drawLine(
                color = color.copy(alpha = (0.45f + v * 0.55f).coerceIn(0.45f, 1f)),
                start = Offset(x, topY + barWidth / 2f),
                end = Offset(x, topY + h - barWidth / 2f),
                strokeWidth = barWidth,
                cap = StrokeCap.Round,
            )
        }
    }
}

@Composable
internal fun PlayerTabs(lyrics: Boolean, onChange: (Boolean) -> Unit) {
    val strings = rememberText()
    val animations = LocalLadSettings.current.animations
    val indicatorFraction by
        animateFloatAsState(
            if (lyrics) 1f else 0f,
            if (animations) MaterialTheme.motionScheme.defaultSpatialSpec() else snap(),
            label = "tab indicator",
        )
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier =
            Modifier.widthIn(max = 280.dp).fillMaxWidth().height(48.dp).testTag("player-tabs"),
    ) {
        BoxWithConstraints(Modifier.fillMaxSize().padding(4.dp)) {
            val halfWidth = (maxWidth - 4.dp) / 2
            Box(
                Modifier.width(halfWidth)
                    .fillMaxHeight()
                    .graphicsLayer {
                        translationX = indicatorFraction * (halfWidth.toPx() + 4.dp.toPx())
                    }
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer)
            )
            Row(
                Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                listOf(false, true).forEach { value ->
                    val selected = value == lyrics
                    val contentColor by
                        animateColorAsState(
                            if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            if (animations) MaterialTheme.motionScheme.fastEffectsSpec() else snap(),
                            label = "tab tint",
                        )
                    Row(
                        Modifier.weight(1f)
                            .fillMaxHeight()
                            .clip(CircleShape)
                            .selectable(selected, onClick = { onChange(value) }, role = Role.Tab)
                            .testTag(if (value) "lyrics-tab" else "artwork-tab"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement =
                            Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                    ) {
                        CompositionLocalProvider(LocalContentColor provides contentColor) {
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
    LaunchedEffect(state.current) { if (state.current == null) onClose() }
    val track = tracks.firstOrNull { it.id == state.current?.id } ?: state.current ?: return
    val seed = trackSeed(track, settings.artworkColor)
    val base = MaterialTheme.colorScheme
    // Keep MaterialTheme colorScheme stable per track (no per-frame recomposition of FullPlayer!)
    // Backdrop surface & glow animate purely in the draw phase inside AmbientBackdrop.
    val scheme =
        remember(seed, base, settings.artworkColor) {
            if (settings.artworkColor) coverScheme(seed ?: base.primary, base) else base
        }
    val targetSeed = seed ?: base.primary
    val background = scheme.surface
    val accent = scheme.primary
    // Prefetch adjacent queue artwork into Coil memory cache for zero-latency 120Hz transitions
    val context = LocalContext.current
    val prefetchEdge =
        with(LocalDensity.current) { LocalConfiguration.current.screenWidthDp.dp.roundToPx() }
            .coerceIn(256, 1024)
    val trackMap = remember(tracks) { tracks.associateBy { it.id } }
    LaunchedEffect(state.queue.index, state.queue.entries, prefetchEdge) {
        val loader = context.imageLoader
        listOf(state.queue.index + 1, state.queue.index - 1)
            .mapNotNull { idx -> state.queue.entries.getOrNull(idx)?.trackId }
            .mapNotNull { id -> trackMap[id]?.artwork }
            .filter { it.isNotBlank() }
            .distinct()
            .forEach { uri ->
                loader.enqueue(
                    ImageRequest.Builder(context)
                        .data(uri)
                        .size(prefetchEdge)
                        .allowHardware(false)
                        .build()
                )
            }
    }
    var queueOpen by remember { mutableStateOf(false) }
    val audioDialog by vm.audioOpen.collectAsStateWithLifecycle()
    val detailDialog by vm.selectedDetails.collectAsStateWithLifecycle()
    var lyricsTab by rememberSaveable { mutableStateOf(false) }
    var sleepOpen by remember { mutableStateOf(false) }
    var options by remember { mutableStateOf(false) }
    var accentPicker by remember { mutableStateOf<Track?>(null) }
    var shareCard by remember { mutableStateOf<Track?>(null) }
    val mediaVisible =
        !queueOpen &&
            !audioDialog &&
            detailDialog == null &&
            !options &&
            !sleepOpen &&
            accentPicker == null &&
            shareCard == null
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
        mediaVisible,
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
                reveal.animateTo(0f, LadMotion.fastEffects(settings.animations))
                onClose()
            }
        }
    }
    LaunchedEffect(Unit) {
        reveal.animateTo(1f, LadMotion.defaultSpatial(settings.animations))
    }
    // M3 Expressive spatial spring shape morph for primary play/pause button and cover silhouette
    val playCorner by
        animateDpAsState(
            if (state.playing) 20.dp else 38.dp,
            LadMotion.fastSpatial(settings.animations),
            label = "play shape",
        )
    val coverMorphFraction by
        animateFloatAsState(
            if (state.playing) 1f else 0f,
            if (settings.animations) spring(dampingRatio = 0.74f, stiffness = 260f) else snap(),
            label = "cover morph",
        )
    val coverPlayScale by
        animateFloatAsState(
            if (!settings.coverMorph || settings.vinyl || state.playing) 1f else 0.92f,
            if (settings.animations) spring(dampingRatio = 0.62f, stiffness = 280f) else snap(),
            label = "cover play scale",
        )
    val shapesDiffer = settings.pauseCoverShape != settings.coverShape
    val coverMorphTwist by
        animateFloatAsState(
            if (settings.coverMorph && !settings.vinyl && shapesDiffer && !state.playing) -6f
            else 0f,
            if (settings.animations) spring(dampingRatio = 0.64f, stiffness = 240f) else snap(),
            label = "cover morph twist",
        )
    val playInteraction = remember { MutableInteractionSource() }
    val favScale = remember { Animatable(1f) }
    LaunchedEffect(track.id, track.favorite) {
        if (settings.animations && track.favorite) {
            favScale.snapTo(0.78f)
            favScale.animateTo(1f, LadMotion.fastSpatial(true))
        } else {
            favScale.snapTo(1f)
        }
    }
    Dialog(
        onDismissRequest = onClose,
        properties =
            DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        val view = LocalView.current
        SideEffect {
            (view.parent as? DialogWindowProvider)?.window?.let { window ->
                window.preferHighestRefreshRate()
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = background.luminance() > 0.5f
                    isAppearanceLightNavigationBars = background.luminance() > 0.5f
                }
                if (android.os.Build.VERSION.SDK_INT < 35) {
                    @Suppress("DEPRECATION")
                    window.statusBarColor = android.graphics.Color.TRANSPARENT
                    @Suppress("DEPRECATION")
                    window.navigationBarColor = android.graphics.Color.TRANSPARENT
                }
                window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            }
        }
        CompositionLocalProvider(LocalLadSettings provides settings) {
            MaterialTheme(colorScheme = scheme) {
                Surface(
                    Modifier.fillMaxSize().graphicsLayer {
                        translationY = (1f - reveal.value) * size.height * 0.14f
                        alpha = reveal.value.coerceIn(0f, 1f)
                    },
                    color = background,
                ) {
                    AmbientBackdrop(
                        targetSeed,
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
                                        Surface(
                                            shape = CircleShape,
                                            color =
                                                if (
                                                    settings.visualizer &&
                                                        !settings.bitPerfect &&
                                                        mediaVisible
                                                )
                                                    scheme.surfaceContainerHigh.copy(alpha = 0.72f)
                                                else Color.Transparent,
                                        ) {
                                            Row(
                                                Modifier.padding(
                                                    horizontal =
                                                        if (
                                                            settings.visualizer &&
                                                                !settings.bitPerfect &&
                                                                mediaVisible
                                                        )
                                                            12.dp
                                                        else 0.dp,
                                                    vertical =
                                                        if (
                                                            settings.visualizer &&
                                                                !settings.bitPerfect &&
                                                                mediaVisible
                                                        )
                                                            4.dp
                                                        else 0.dp,
                                                ),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            ) {
                                                if (
                                                    settings.visualizer &&
                                                        !settings.bitPerfect &&
                                                        mediaVisible
                                                )
                                                    LiveMeter(vm, state.playing)
                                                Text(
                                                    if (state.loading)
                                                        strings[R.string.loading_track]
                                                    else if (state.playing)
                                                        strings[R.string.now_playing]
                                                    else strings[R.string.paused],
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = accent,
                                                )
                                            }
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
                                                        strings[R.string.album_accent],
                                                        strings[R.string.pick_cover_pixel],
                                                        Icons.Default.Colorize,
                                                    ) {
                                                        options = false
                                                        accentPicker = track
                                                    },
                                                    PlayerAction(
                                                        strings[R.string.share_card],
                                                        strings[R.string.share_card_hint],
                                                        Icons.Default.Share,
                                                    ) {
                                                        options = false
                                                        shareCard = track
                                                    },
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
                                        (fadeIn(LadMotion.defaultEffects(settings.animations)) +
                                                slideInVertically(
                                                    LadMotion.defaultSpatial(settings.animations)
                                                ) {
                                                    if (targetState) it / 20 else -it / 20
                                                })
                                            .togetherWith(
                                                fadeOut(LadMotion.fastEffects(settings.animations))
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
                                            val activeMorphShape =
                                                if (settings.coverMorph && !settings.vinyl)
                                                    MorphedCoverShape(
                                                        pauseShape = settings.pauseCoverShape,
                                                        playShape = settings.coverShape,
                                                        morphFraction = coverMorphFraction,
                                                    )
                                                else null
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
                                                ArtworkMotion(
                                                    track.artwork,
                                                    direction = state.artworkDirection,
                                                ) { uri ->
                                                    CompositionLocalProvider(
                                                        LocalCoverMorphShape provides
                                                            activeMorphShape
                                                    ) {
                                                        Box(
                                                            Modifier.fillMaxSize().graphicsLayer {
                                                                scaleX = coverPlayScale
                                                                scaleY = coverPlayScale
                                                                rotationZ =
                                                                    if (settings.vinyl)
                                                                        rotation.value
                                                                    else coverMorphTwist
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
                                }
                                Row(
                                    Modifier.fillMaxWidth().padding(top = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    AnimatedContent(
                                        targetState = track.id to (track.title to track.artist),
                                        modifier = Modifier.weight(1f),
                                        transitionSpec = {
                                            (fadeIn(LadMotion.defaultEffects(settings.animations)) +
                                                    slideInVertically(
                                                        LadMotion.fastSpatial(settings.animations)
                                                    ) {
                                                        it / 5
                                                    })
                                                .togetherWith(
                                                    fadeOut(
                                                        LadMotion.fastEffects(settings.animations)
                                                    )
                                                )
                                        },
                                        label = "track meta",
                                    ) { (_, meta) ->
                                        Column(Modifier.fillMaxWidth()) {
                                            Text(
                                                strings.metadata(meta.first),
                                                style = MaterialTheme.typography.headlineLarge,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                            Text(
                                                strings.metadata(meta.second),
                                                style = MaterialTheme.typography.titleMedium,
                                                color = scheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                    }
                                    IconButton({ vm.favorite(track) }) {
                                        Icon(
                                            if (track.favorite) Icons.Default.Favorite
                                            else Icons.Default.FavoriteBorder,
                                            strings[R.string.favourites],
                                            Modifier.graphicsLayer {
                                                scaleX = favScale.value
                                                scaleY = favScale.value
                                            },
                                            tint = accent,
                                        )
                                    }
                                }
                                FullPlayerTimeline(track, state, vm)
                                Surface(
                                    shape = LadShapes.hero,
                                    color = scheme.surfaceContainerLow.copy(alpha = 0.92f),
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
                                            onClick = { vm.playback.toggle() },
                                            modifier =
                                                Modifier.size(mainButton)
                                                    .expressivePress(playInteraction, 0.94f),
                                            shape = RoundedCornerShape(playCorner),
                                            interactionSource = playInteraction,
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
                                                        LadMotion.fastEffects(settings.animations),
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
                                                    (livePositionMs(vm.playback.state.value) - 10000)
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
                                            modifier = Modifier.weight(1f),
                                            shapes =
                                                androidx.compose.material3.ButtonDefaults.shapes(),
                                        ) {
                                            Text(strings[R.string.sound])
                                        }
                                        TextButton(
                                            { queueOpen = true },
                                            modifier = Modifier.weight(1f),
                                            shapes =
                                                androidx.compose.material3.ButtonDefaults.shapes(),
                                        ) {
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
                                                val maxMs =
                                                    state.durationMs.takeIf { it > 0 }
                                                        ?: Long.MAX_VALUE
                                                vm.playback.seek(
                                                    (livePositionMs(vm.playback.state.value) + 10000)
                                                        .coerceAtMost(maxMs)
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
                                                    (livePositionMs(vm.playback.state.value) - 10000)
                                                        .coerceAtLeast(0)
                                                )
                                            }) {
                                                Icon(
                                                    Icons.Default.Replay10,
                                                    strings[R.string.back_10_seconds],
                                                )
                                            }
                                            IconButton({
                                                val maxMs =
                                                    state.durationMs.takeIf { it > 0 }
                                                        ?: Long.MAX_VALUE
                                                vm.playback.seek(
                                                    (livePositionMs(vm.playback.state.value) + 10000)
                                                        .coerceAtMost(maxMs)
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
                        onJump = vm.playback::jump,
                    ) {
                        queueOpen = false
                    }
                detailDialog?.let {
                    val detailHistory by vm.statisticsState.history.collectAsStateWithLifecycle()
                    TrackDetailsPanel(it, detailHistory, vm) { vm.selectedDetails.value = null }
                }
                accentPicker?.let { selected ->
                    AlbumAccentPicker(selected, vm) { accentPicker = null }
                }
                shareCard?.let { selected ->
                    MusicSharePanel(selected, vm = vm) { shareCard = null }
                }
                if (audioDialog) AudioPanel(settings, state, vm) { vm.audioOpen.value = false }
                if (sleepOpen)
                    AlertDialog(
                        onDismissRequest = { sleepOpen = false },
                        title = { Text(strings[R.string.sleep_timer]) },
                        text = {
                            Column {
                                listOf(15, 30, 45, 60, 90).forEach { n ->
                                    TextButton(
                                        {
                                            vm.playback.sleep(n)
                                            sleepOpen = false
                                        },
                                        shapes = androidx.compose.material3.ButtonDefaults.shapes(),
                                    ) {
                                        Text(strings.plural(R.plurals.minutes, n, n))
                                    }
                                }
                            }
                        },
                        confirmButton = {
                            TextButton(
                                {
                                    vm.playback.sleep(0)
                                    sleepOpen = false
                                },
                                shapes = androidx.compose.material3.ButtonDefaults.shapes(),
                            ) {
                                Text(strings[R.string.turn_off])
                            }
                        },
                        dismissButton = {
                            TextButton(
                                { sleepOpen = false },
                                shapes = androidx.compose.material3.ButtonDefaults.shapes(),
                            ) {
                                Text(strings[R.string.close])
                            }
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
    onJump: ((Int) -> Unit)? = null,
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
                        TextButton(
                            {
                                onRestore(revision.id)
                                historyOpen = false
                            },
                            shapes = androidx.compose.material3.ButtonDefaults.shapes(),
                        ) {
                            Column(Modifier.fillMaxWidth()) {
                                Text(strings.label(revision.label))
                                Text(
                                    strings.plural(
                                        R.plurals.library_screens_tracks_205,
                                        revision.snapshot.entries.size,
                                        revision.snapshot.entries.size,
                                        AppDates.format(
                                            revision.createdAt,
                                            LocalLadSettings.current.dateFormat,
                                        ) +
                                            " · " +
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
                TextButton(
                    { historyOpen = false },
                    shapes = androidx.compose.material3.ButtonDefaults.shapes(),
                ) {
                    Text(strings[R.string.close])
                }
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
                    shapes = androidx.compose.material3.ButtonDefaults.shapes(),
                ) {
                    Icon(Icons.AutoMirrored.Filled.Undo, null, Modifier.size(18.dp))
                    Text(strings[R.string.undo])
                }
                TextButton(
                    { historyOpen = true },
                    enabled = state.queueHistory.isNotEmpty(),
                    shapes = androidx.compose.material3.ButtonDefaults.shapes(),
                ) {
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
                            shape = LadShapes.artwork,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        ) {
                            Row(
                                Modifier.padding(12.dp),
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
                                shape = LadShapes.card,
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                            ) {
                                Row(
                                    Modifier.fillMaxWidth()
                                        .padding(
                                            start = 10.dp,
                                            end = 4.dp,
                                            top = 10.dp,
                                            bottom = 10.dp,
                                        ),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    Row(
                                        Modifier.weight(1f).clickable {
                                            if (onJump != null) onJump(index) else onSelect(t.id)
                                        },
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    ) {
                                        Artwork(t.artwork, t.title, Modifier.size(46.dp), 12)
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
                                        if (menu)
                                            ExpressiveActions(
                                                t.title,
                                                strings.metadata(t.artist),
                                                { menu = false },
                                                buildList {
                                                    if (
                                                        index > state.queue.index + 1 &&
                                                            state.queue.entries.getOrNull(index - 1)
                                                                ?.cycle == entry.cycle
                                                    )
                                                        add(
                                                            PlayerAction(
                                                                strings[R.string.move_up],
                                                                "",
                                                                Icons.Default.KeyboardArrowUp,
                                                            ) {
                                                                menu = false
                                                                onMove(index, index - 1)
                                                            }
                                                        )
                                                    if (
                                                        index < state.queue.entries.lastIndex &&
                                                            state.queue.entries.getOrNull(index + 1)
                                                                ?.cycle == entry.cycle
                                                    )
                                                        add(
                                                            PlayerAction(
                                                                strings[R.string.move_down],
                                                                "",
                                                                Icons.Default.KeyboardArrowDown,
                                                            ) {
                                                                menu = false
                                                                onMove(index, index + 1)
                                                            }
                                                        )
                                                    add(
                                                        PlayerAction(
                                                            strings[R.string.remove_from_queue],
                                                            "",
                                                            Icons.Default.Close,
                                                        ) {
                                                            menu = false
                                                            onRemove(index)
                                                        }
                                                    )
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

private fun livePositionMs(state: PlaybackState): Long {
    val elapsed =
        if (state.playing)
            ((android.os.SystemClock.elapsedRealtime() - state.positionTimestampMs)
                    .coerceAtLeast(0) * state.speed)
                .toLong()
        else 0L
    val raw = state.positionMs + elapsed
    return if (state.durationMs > 0) raw.coerceIn(0, state.durationMs) else raw.coerceAtLeast(0)
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
                if (dragging) {
                    vm.playback.seek(seek.toLong())
                    dragging = false
                }
            },
            playing = clock.playing,
            positionTimestampMs = clock.positionTimestampMs,
            speed = clock.speed,
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                duration(if (dragging) seek.toLong() else clock.positionMs),
                style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                color = scheme.onSurfaceVariant,
            )
            Text(
                track.mimeType.substringAfter('/').uppercase().ifBlank { strings[R.string.audio] },
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurfaceVariant,
            )
            Text(
                duration(state.durationMs),
                style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
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
        style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
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
    TextButton(onClick, shapes = androidx.compose.material3.ButtonDefaults.shapes()) {
        Icon(Icons.Default.Bedtime, null, Modifier.size(16.dp))
        Text(strings.format(R.string.timer, duration(remaining)))
    }
}
