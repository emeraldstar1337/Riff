package dev.lad.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
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
    val lines = remember(record?.text) { LrcParser.parse(record?.text.orEmpty()) }

    val progress =
        remember(lines, vm) {
            vm.playback.state
                .map { clock ->
                    val index = LrcParser.current(lines, clock.positionMs)
                    val word =
                        lines.getOrNull(index)?.let { LrcParser.currentWord(it, clock.positionMs) }
                            ?: -1
                    Triple(index, word, clock.positionMs)
                }
                .distinctUntilChanged { previous, next ->
                    previous.first == next.first && previous.second == next.second
                }
        }
    val initial =
        remember(lines) { Triple(LrcParser.current(lines, state.positionMs), -1, state.positionMs) }
    val timing = if (visible) progress.collectAsStateWithLifecycle(initial).value else initial
    val position = timing.third
    val current = timing.first
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
    DisposableEffect(lines, current, timing.second, visible) {
        val line = lines.getOrNull(current)
        val nextWord =
            line
                ?.words
                ?.flatMap { listOfNotNull(it.timeMs, it.endTimeMs) }
                ?.filter { it > position }
                ?.minOrNull()
        val nextLine = lines.getOrNull(current + 1)?.timeMs
        vm.playback.lyricsDeadline.value =
            if (visible) minOf(nextWord ?: Long.MAX_VALUE, nextLine ?: Long.MAX_VALUE)
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
            val offset = -list.layoutInfo.viewportSize.height / 3
            if (animations) list.animateScrollToItem(current, offset)
            else list.scrollToItem(current, offset)
        }
    }
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
            TextButton({ search = true }) { Text(strings[R.string.find]) }
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
        when {
            loading ->
                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 2.dp)
                        Text(
                            strings[R.string.finding_synced_lyrics],
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            lines.isNotEmpty() ->
                LazyColumn(
                    state = list,
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentPadding = PaddingValues(top = 28.dp, bottom = 80.dp),
                    verticalArrangement =
                        Arrangement.spacedBy(
                            ((LocalLadSettings.current.lyricsSpacing - 1f) * 48f).dp
                        ),
                ) {
                    itemsIndexed(lines, key = { index, line -> "$index:${line.timeMs}" }) {
                        index,
                        line ->
                        LyricLineText(
                            line,
                            index == current,
                            position,
                            {
                                vm.playback.seek(line.timeMs)
                                following = true
                            },
                            Modifier.testTag("lyric-line-$index"),
                        )
                    }
                }
            !record?.text.isNullOrBlank() ->
                Column(
                    Modifier.fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = 20.dp)
                ) {
                    Text(
                        strings[R.string.no_timestamps_you_can_replace_this],
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        record!!.text,
                        Modifier.fillMaxWidth(),
                        style = lyricTextStyle(LocalLadSettings.current),
                        textAlign =
                            if (LocalLadSettings.current.lyricsAlignment == "center")
                                TextAlign.Center
                            else TextAlign.Start,
                    )
                }
            else ->
                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
                        TextButton({ search = true }) {
                            Text(strings[R.string.find_another_version])
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
                TextButton({
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
                }) {
                    Text(strings[R.string.save])
                }
            },
            dismissButton = { TextButton({ editing = false }) { Text(strings[R.string.cancel]) } },
        )
    }
    if (search) {
        var title by remember(track.id) { mutableStateOf(track.title) }
        var artist by remember(track.id) { mutableStateOf(track.artist) }
        var busy by remember { mutableStateOf(false) }
        var searchError by remember { mutableStateOf<String?>(null) }
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
        LaunchedEffect(Unit) { searchOnline() }
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
                        { scope.launch { searchOnline() } },
                        enabled = !busy && title.isNotBlank(),
                    ) {
                        if (busy)
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
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
                        OutlinedCard(
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
            confirmButton = { TextButton({ search = false }) { Text(strings[R.string.close]) } },
        )
    }
}

@Composable
internal fun LyricLineText(
    line: dev.lad.domain.LyricLine,
    active: Boolean,
    positionMs: Long,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val animations = LocalLadSettings.current.animations
    val appearance = LocalLadSettings.current
    val timedWords = line.words.isNotEmpty()
    val activeWord = if (active) LrcParser.currentWord(line, positionMs) else -1
    val color by
        animateColorAsState(
            MaterialTheme.colorScheme.onSurface.copy(
                alpha = if (active) 1f else appearance.lyricsDim.coerceAtMost(0.6f)
            ),
            tween(LadMotion.duration(LadMotion.standard, animations)),
            label = "lyric highlight",
        )
    val primary = MaterialTheme.colorScheme.primary
    val emphasis by
        androidx.compose.animation.core.animateFloatAsState(
            if (active) 1.03f else 1f,
            androidx.compose.animation.core.tween(if (animations) 180 else 0),
            label = "active lyric",
        )
    val text = buildAnnotatedString {
        append(line.text.ifBlank { "♪" })
        line.words.getOrNull(activeWord)?.let { word ->
            addStyle(
                SpanStyle(color = primary, fontWeight = FontWeight.Black),
                word.start,
                word.end,
            )
        }
    }
    Text(
        text,
        modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = emphasis
                scaleY = emphasis
            }
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        style = lyricTextStyle(appearance),
        textAlign =
            if (appearance.lyricsAlignment == "center") TextAlign.Center else TextAlign.Start,
        fontWeight = if (active) FontWeight.Black else FontWeight.Medium,
        color = color,
    )
}

@Composable
internal fun lyricTextStyle(appearance: Settings) =
    MaterialTheme.typography.headlineSmall.copy(
        fontSize = appearance.lyricsSize.coerceIn(16f, 34f).sp,
        lineHeight =
            (appearance.lyricsSize.coerceIn(16f, 34f) *
                    appearance.lyricsSpacing.coerceIn(1.1f, 1.7f))
                .sp,
    )
