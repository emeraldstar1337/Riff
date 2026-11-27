@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package dev.lad.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.spring
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.lad.R
import dev.lad.data.*
import dev.lad.domain.LrcParser
import dev.lad.domain.OnlineLyrics
import dev.lad.i18n.rememberText
import dev.lad.playback.PlaybackState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

private data class LyricsTiming(val index: Int, val word: Int, val clock: PlaybackState)

@Composable
fun LyricsContent(
    track: Track,
    state: PlaybackState,
    vm: LadViewModel,
    modifier: Modifier = Modifier,
    visible: Boolean = true,
) {
    val strings = rememberText()
    val scope = rememberCoroutineScope()
    var record by remember(track.id) { mutableStateOf<LyricsRecord?>(null) }
    var editing by remember(track.id) { mutableStateOf(false) }
    var search by remember(track.id) { mutableStateOf(false) }
    var loading by remember(track.id) { mutableStateOf(true) }
    var error by remember(track.id) { mutableStateOf<String?>(null) }
    var candidates by remember(track.id) { mutableStateOf<List<OnlineLyrics>>(emptyList()) }
    var following by remember(track.id) { mutableStateOf(true) }
    val list = rememberLazyListState()
    val userDragging by list.interactionSource.collectIsDraggedAsState()
    val appearanceRevision by vm.appearance.changes.collectAsStateWithLifecycle()
    val offset = remember(track.id, appearanceRevision) { vm.appearance.lyricsOffset(track.id) }
    var timingOpen by remember { mutableStateOf(false) }
    var shareOpen by remember { mutableStateOf(false) }
    val lines = remember(record?.text) { LrcParser.parse(record?.text.orEmpty()) }

    val progress =
        remember(lines, vm, offset) {
            vm.playback.state
                .map { rawClock ->
                    val clock = rawClock.copy(positionMs = rawClock.positionMs + offset)
                    val index = LrcParser.current(lines, clock.positionMs)
                    val word =
                        lines.getOrNull(index)?.let { LrcParser.currentWord(it, clock.positionMs) }
                            ?: -1
                    LyricsTiming(index, word, clock)
                }
                .distinctUntilChanged { previous, next ->
                    previous.index == next.index &&
                        previous.word == next.word &&
                        previous.clock.playing == next.clock.playing &&
                        previous.clock.speed == next.clock.speed &&
                        kotlin.math.abs(
                            next.clock.positionMs -
                                previous.clock.positionMs -
                                ((next.clock.positionTimestampMs -
                                        previous.clock.positionTimestampMs) *
                                        if (previous.clock.playing) previous.clock.speed else 0f)
                                    .toLong()
                        ) < 250
                }
        }
    val initial =
        remember(lines, offset) {
            LyricsTiming(
                LrcParser.current(lines, state.positionMs + offset),
                -1,
                state.copy(positionMs = state.positionMs + offset),
            )
        }
    val timing = if (visible) progress.collectAsStateWithLifecycle(initial).value else initial
    val position = timing.clock.positionMs
    val current = timing.index
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner, lines.isNotEmpty(), visible) {
        fun update() {
            vm.playback.lyricsVisible.value =
                visible &&
                    lines.isNotEmpty() &&
                    owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        }
        val observer = LifecycleEventObserver { _, _ -> update() }
        owner.lifecycle.addObserver(observer)
        update()
        onDispose {
            owner.lifecycle.removeObserver(observer)
            vm.playback.lyricsVisible.value = false
        }
    }
    DisposableEffect(lines, current, timing.word, visible, offset) {
        val line = lines.getOrNull(current)
        val nextWord =
            line
                ?.words
                ?.flatMap { listOfNotNull(it.timeMs, it.endTimeMs) }
                ?.filter { it > position }
                ?.minOrNull()
        val nextLine = lines.getOrNull(current + 1)?.timeMs
        vm.playback.lyricsDeadline.value =
            if (visible)
                minOf(nextWord ?: Long.MAX_VALUE, nextLine ?: Long.MAX_VALUE).let {
                    if (it == Long.MAX_VALUE) it else (it - offset).coerceAtLeast(0)
                }
            else Long.MAX_VALUE
        onDispose { vm.playback.lyricsDeadline.value = Long.MAX_VALUE }
    }
    val animations = LocalLadSettings.current.animations

    LaunchedEffect(track.id) {
        try {
            record = vm.lyrics.load(track)
            if (record?.text.isNullOrBlank()) {
                val found = vm.lyrics.findSynced(track)
                if (found != null) {
                    vm.lyrics.save(track.id, found.text, strings[R.string.lrclib_synced])
                    record = vm.lyrics.load(track)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = strings[R.string.could_not_find_lyrics_check_your]
        } finally {
            loading = false
        }
    }
    LaunchedEffect(userDragging) { if (userDragging) following = false }
    LaunchedEffect(current, lines, following, visible) {
        if (visible && following && current >= 0 && lines.isNotEmpty()) {
            withFrameNanos {}
            val viewport = list.layoutInfo.viewportSize.height
            if (viewport <= 0) return@LaunchedEffect
            val item = list.layoutInfo.visibleItemsInfo.firstOrNull { it.index == current }
            val anchor = (viewport * 0.30f).toInt()
            val targetOffset = -(anchor - (item?.size ?: 0) / 2).coerceAtLeast(0)
            if (!animations) list.scrollToItem(current, targetOffset)
            else if (item != null)
                list.animateScrollBy(
                    (item.offset + targetOffset).toFloat(),
                    spring(dampingRatio = 0.88f, stiffness = 140f),
                )
            else list.animateScrollToItem(current, targetOffset)
        }
    }
    if (timingOpen)
        AlertDialog(
            onDismissRequest = { timingOpen = false },
            title = { Text(strings[R.string.lyrics_timing]) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(strings[R.string.lyrics_timing_hint])
                    Text(
                        strings.format(R.string.offset_seconds, offset / 1000f),
                        style = MaterialTheme.typography.headlineMedium,
                    )
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedButton(
                            { vm.appearance.setLyricsOffset(track.id, offset - 100) },
                            modifier = Modifier.weight(1f),
                            enabled = offset > -30000,
                            shapes = androidx.compose.material3.ButtonDefaults.shapes(),
                        ) {
                            Text(strings[R.string.lyrics_later])
                        }
                        Button(
                            { vm.appearance.setLyricsOffset(track.id, offset + 100) },
                            modifier = Modifier.weight(1f),
                            enabled = offset < 30000,
                            shapes = androidx.compose.material3.ButtonDefaults.shapes(),
                        ) {
                            Text(strings[R.string.lyrics_earlier])
                        }
                    }
                    TextButton(
                        { vm.appearance.setLyricsOffset(track.id, 0) },
                        shapes = androidx.compose.material3.ButtonDefaults.shapes(),
                    ) {
                        Text(strings[R.string.reset])
                    }
                }
            },
            confirmButton = {
                TextButton(
                    { timingOpen = false },
                    shapes = androidx.compose.material3.ButtonDefaults.shapes(),
                ) {
                    Text(strings[R.string.close])
                }
            },
        )
    if (shareOpen)
        MusicSharePanel(track, vm, lines.getOrNull(current)?.text.orEmpty()) { shareOpen = false }
    val importer =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null)
                scope.launch {
                    try {
                        vm.lyrics.import(track.id, uri)
                        record = vm.lyrics.load(track)
                        following = true
                        error = null
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        error = e.message ?: strings[R.string.could_not_open_the_lyrics_file]
                    }
                }
        }
    Column(modifier.testTag("player-lyrics").padding(horizontal = 8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    if (lines.isNotEmpty()) strings[R.string.synced_lyrics]
                    else strings[R.string.lyrics],
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    strings.label(record?.source ?: "LRCLIB"),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (lines.isNotEmpty())
                IconToggleButton(following, { following = it }) {
                    Icon(
                        Icons.Default.MyLocation,
                        if (following) strings[R.string.stop_following_lyrics]
                        else strings[R.string.follow_lyrics],
                    )
                }
            TextButton(
                { search = true },
                shapes = androidx.compose.material3.ButtonDefaults.shapes(),
            ) {
                Text(strings[R.string.find])
            }
            var menu by remember { mutableStateOf(false) }
            Box {
                IconButton({ menu = true }) {
                    Icon(Icons.Default.EditNote, strings[R.string.lyrics_actions])
                }
                if (menu)
                    ExpressiveActions(
                        strings[R.string.song_lyrics],
                        strings[R.string.synced_lines_and_your_own_files],
                        { menu = false },
                        listOf(
                            PlayerAction(
                                strings[R.string.lyrics_timing],
                                strings[R.string.lyrics_timing_hint],
                                Icons.Default.Sync,
                            ) {
                                menu = false
                                timingOpen = true
                            },
                            PlayerAction(
                                strings[R.string.share_card],
                                strings[R.string.share_card_hint],
                                Icons.Default.Share,
                            ) {
                                menu = false
                                shareOpen = true
                            },
                            PlayerAction(
                                strings[R.string.import_lrc_txt],
                                strings[R.string.choose_a_file],
                                Icons.Default.UploadFile,
                            ) {
                                menu = false
                                importer.launch(arrayOf("text/*", "application/octet-stream"))
                            },
                            PlayerAction(
                                strings[R.string.edit_lyrics],
                                strings[R.string.correct_words_or_timestamps],
                                Icons.Default.EditNote,
                            ) {
                                menu = false
                                editing = true
                            },
                        ),
                    )
            }
        }
        val viewState =
            when {
                loading -> 0
                lines.isNotEmpty() -> 1
                !record?.text.isNullOrBlank() -> 2
                else -> 3
            }
        AnimatedContent(
            targetState = viewState,
            modifier = Modifier.fillMaxWidth().weight(1f),
            transitionSpec = {
                (fadeIn(LadMotion.defaultEffects(animations)) +
                        slideInVertically(LadMotion.defaultSpatial(animations)) { it / 18 })
                    .togetherWith(fadeOut(LadMotion.fastEffects(animations)))
            },
            label = "lyrics content state",
        ) { mode ->
            when (mode) {
                0 ->
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            LoadingIndicator(Modifier.size(44.dp))
                            Text(
                                strings[R.string.finding_synced_lyrics],
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                1 ->
                    BoxWithConstraints(Modifier.fillMaxSize()) {
                        val topInset = (maxHeight * 0.28f).coerceAtLeast(48.dp)
                        val bottomInset = (maxHeight * 0.56f).coerceAtLeast(180.dp)
                        val gap =
                            ((LocalLadSettings.current.lyricsSpacing.coerceIn(1.1f, 1.7f) - 1f) *
                                    28f)
                                .coerceAtLeast(4f)
                                .dp
                        LazyColumn(
                            state = list,
                            modifier = Modifier.fillMaxSize().lyricEdges(),
                            contentPadding =
                                PaddingValues(
                                    start = 4.dp,
                                    end = 4.dp,
                                    top = topInset,
                                    bottom = bottomInset,
                                ),
                            verticalArrangement = Arrangement.spacedBy(gap),
                        ) {
                            itemsIndexed(lines, key = { index, line -> "$index:${line.timeMs}" }) {
                                index,
                                line ->
                                LyricLineText(
                                    line,
                                    index == current,
                                    position,
                                    {
                                        vm.playback.seek((line.timeMs - offset).coerceAtLeast(0))
                                        following = true
                                    },
                                    Modifier.testTag("lyric-line-$index"),
                                    clock = timing.clock.takeIf { visible },
                                    endTimeMs = lines.getOrNull(index + 1)?.timeMs,
                                    distance =
                                        if (current >= 0) kotlin.math.abs(index - current) else 0,
                                )
                            }
                        }
                    }
                2 ->
                    Column(
                        Modifier.fillMaxSize()
                            .lyricEdges()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 28.dp)
                    ) {
                        Text(
                            strings[R.string.no_timestamps_you_can_replace_this],
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            record?.text.orEmpty(),
                            Modifier.fillMaxWidth(),
                            style = lyricTextStyle(LocalLadSettings.current),
                            textAlign =
                                if (LocalLadSettings.current.lyricsAlignment == "center")
                                    TextAlign.Center
                                else TextAlign.Start,
                        )
                    }
                else ->
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(
                            Modifier.padding(horizontal = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.Notes,
                                null,
                                Modifier.size(32.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                strings[R.string.synced_lyrics_not_found_yet],
                                style = MaterialTheme.typography.titleLarge,
                            )
                            Text(
                                error ?: strings[R.string.find_another_recording_or_import_an],
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            TextButton(
                                { search = true },
                                shapes = androidx.compose.material3.ButtonDefaults.shapes(),
                            ) {
                                Text(strings[R.string.find_another_version])
                            }
                        }
                    }
            }
        }
    }
    if (editing) {
        var draft by remember(track.id) { mutableStateOf(record?.text.orEmpty()) }
        var saveError by remember { mutableStateOf<String?>(null) }
        AlertDialog(
            onDismissRequest = { editing = false },
            title = { Text(strings[R.string.song_lyrics]) },
            text = {
                Column {
                    OutlinedTextField(
                        draft,
                        { draft = it },
                        Modifier.heightIn(max = 350.dp),
                        minLines = 6,
                        label = { Text(strings[R.string.plain_text_or_lrc_with_timestamps]) },
                    )
                    saveError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {
                TextButton(
                    {
                        scope.launch {
                            try {
                                vm.lyrics.save(track.id, draft, strings[R.string.manual])
                                record = vm.lyrics.load(track)
                                following = true
                                editing = false
                                error = null
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                saveError = e.message
                            }
                        }
                    },
                    shapes = androidx.compose.material3.ButtonDefaults.shapes(),
                ) {
                    Text(strings[R.string.save])
                }
            },
            dismissButton = {
                TextButton(
                    { editing = false },
                    shapes = androidx.compose.material3.ButtonDefaults.shapes(),
                ) {
                    Text(strings[R.string.cancel])
                }
            },
        )
    }
    if (search) {
        val searchScope = rememberCoroutineScope()
        var title by remember(track.id) { mutableStateOf(track.title) }
        var artist by remember(track.id) { mutableStateOf(track.artist) }
        var busy by remember { mutableStateOf(false) }
        var searchError by remember { mutableStateOf<String?>(null) }
        var searchJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
        suspend fun searchOnline() {
            busy = true
            searchError = null
            candidates = emptyList()
            try {
                candidates = vm.lyrics.search(title, artist, track.durationMs, track.album)
                if (candidates.isEmpty())
                    searchError = strings[R.string.lrclib_has_no_synced_lyrics_for]
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                searchError = e.message ?: strings[R.string.could_not_search_for_lyrics]
            } finally {
                busy = false
            }
        }
        fun triggerSearch() {
            searchJob?.cancel()
            searchJob = searchScope.launch { searchOnline() }
        }
        LaunchedEffect(Unit) { triggerSearch() }
        AlertDialog(
            onDismissRequest = { search = false },
            title = { Text(strings[R.string.lyrics_panel_synced_lyrics]) },
            text = {
                Column(
                    Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        strings[R.string.lrclib_only_results_with_timestamps_the],
                        style = MaterialTheme.typography.bodySmall,
                    )
                    OutlinedTextField(
                        title,
                        { title = it },
                        label = { Text(strings[R.string.song]) },
                        singleLine = true,
                    )
                    OutlinedTextField(
                        artist,
                        { artist = it },
                        label = { Text(strings[R.string.artist]) },
                        singleLine = true,
                    )
                    FilledTonalButton(
                        { triggerSearch() },
                        enabled = !busy && title.isNotBlank(),
                        shapes = androidx.compose.material3.ButtonDefaults.shapes(),
                    ) {
                        if (busy) LoadingIndicator(Modifier.size(22.dp))
                        else Text(strings[R.string.lyrics_panel_search])
                    }
                    searchError?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    candidates.forEach { candidate ->
                        Card(
                            onClick = {
                                scope.launch {
                                    try {
                                        vm.lyrics.save(
                                            track.id,
                                            candidate.text,
                                            strings[R.string.lrclib_synced],
                                        )
                                        record = vm.lyrics.load(track)
                                        following = true
                                        search = false
                                        error = null
                                    } catch (e: CancellationException) {
                                        throw e
                                    } catch (e: Exception) {
                                        searchError = e.message
                                    }
                                }
                            }
                        ) {
                            Column(Modifier.fillMaxWidth().padding(12.dp)) {
                                Text(candidate.title, fontWeight = FontWeight.Bold)
                                Text(
                                    "${candidate.artist} · ${duration(candidate.duration * 1000L)}",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                if (candidate.album.isNotBlank())
                                    Text(
                                        candidate.album,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    { search = false },
                    shapes = androidx.compose.material3.ButtonDefaults.shapes(),
                ) {
                    Text(strings[R.string.close])
                }
            },
        )
    }
}

@Composable
internal fun lyricTextStyle(appearance: Settings) =
    MaterialTheme.typography.headlineSmall.copy(
        fontSize = appearance.lyricsSize.coerceIn(16f, 34f).sp,
        lineHeight =
            (appearance.lyricsSize.coerceIn(16f, 34f) *
                    appearance.lyricsSpacing.coerceIn(1.1f, 1.7f))
                .sp,
        platformStyle = PlatformTextStyle(includeFontPadding = false),
        lineHeightStyle =
            LineHeightStyle(
                alignment = LineHeightStyle.Alignment.Center,
                trim = LineHeightStyle.Trim.None,
            ),
    )

internal fun Modifier.lyricEdges(): Modifier =
    graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithCache {
            val edge = minOf(40.dp.toPx(), size.height / 4f)
            val fraction = if (size.height > 0) edge / size.height else 0f
            val mask =
                Brush.verticalGradient(
                    0f to Color.Transparent,
                    fraction to Color.Black,
                    (1f - fraction) to Color.Black,
                    1f to Color.Transparent,
                )
            onDrawWithContent {
                drawContent()
                drawRect(mask, blendMode = BlendMode.DstIn)
            }
        }
