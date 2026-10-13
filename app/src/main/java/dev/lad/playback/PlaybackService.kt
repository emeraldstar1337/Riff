package dev.lad.playback

import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import androidx.core.net.toUri
import androidx.media3.common.*
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.*
import androidx.room.withTransaction
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import dev.lad.LadApplication
import dev.lad.MainActivity
import dev.lad.R
import dev.lad.data.*
import dev.lad.domain.*
import dev.lad.i18n.AppLanguage
import dev.lad.i18n.TextResources
import dev.lad.util.attempt
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first

@androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
class PlaybackService : MediaSessionService() {
    private val strings
        get() = TextResources(AppLanguage.context(this))

    companion object {
        @Volatile internal var serviceTicks = 0L
        @Volatile internal var timerTicks = 0L
        @Volatile internal var incomingPrepared = false
    }

    private class Slot(val player: ExoPlayer, val gain: GainAudioProcessor) {
        var gainTag: String? = null
        var peakTag: String? = null
        var audioTrack: android.media.AudioTrack? = null
        var routingListener: android.media.AudioRouting.OnRoutingChangedListener? = null
    }

    private lateinit var active: Slot
    private val player
        get() = active.player

    private var spare: Slot? = null
    private var fadeListen: ListeningSession? = null
    private var fadeFraction = 0f
    private lateinit var session: MediaSession
    private val audioAttributes =
        AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val ready = CompletableDeferred<Unit>()
    private val queue = QueueManager()
    private val app
        get() = application as LadApplication

    private var tracks = emptyMap<Long, Track>()
    private var settings = Settings()
    private val liveProfiles = mutableMapOf<String, SoundProfile>()
    private var deviceKey = "speaker"
    private var deviceName = "Phone speaker"
    private val audioManager by lazy { getSystemService(android.media.AudioManager::class.java) }
    private val deviceCallback =
        object : android.media.AudioDeviceCallback() {
            override fun onAudioDevicesAdded(
                addedDevices: Array<out android.media.AudioDeviceInfo>
            ) {
                updateRoute()
            }

            override fun onAudioDevicesRemoved(
                removedDevices: Array<out android.media.AudioDeviceInfo>
            ) {
                updateRoute()
            }
        }
    private var listen: ListeningSession? = null
    private var sleepUntil = 0L
    private var syncing = false
    private var error: String? = null
    private var failures = 0
    private var previousTick = SystemClock.elapsedRealtime()
    private var wasPlaying = false
    private val journal = QueueHistory()
    private val wakeups = Channel<Unit>(Channel.CONFLATED)
    private val saves = Channel<Pair<QueueSnapshot, List<QueueRevision>>>(Channel.CONFLATED)
    private val soundWrites = Channel<Pair<String, SoundProfile>>(Channel.UNLIMITED)
    private var runId = UUID.randomUUID().toString()
    private var runStarted = System.currentTimeMillis()
    private var runTotal = 0L
    private var pausedAt = 0L
    private val listenWrites = Channel<Any?>(Channel.UNLIMITED)

    override fun onCreate() {
        super.onCreate()
        active = createSlot(true)
        audioManager.registerAudioDeviceCallback(deviceCallback, android.os.Handler(mainLooper))
        updateRoute()
        val activity =
            PendingIntent.getActivity(
                this,
                0,
                Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        session =
            MediaSession.Builder(this, player)
                .setSessionActivity(activity)
                .setCallback(
                    object : MediaSession.Callback {
                        override fun onConnect(
                            session: MediaSession,
                            controller: MediaSession.ControllerInfo,
                        ): MediaSession.ConnectionResult {
                            val commands =
                                if (controller.packageName == packageName)
                                    MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS
                                        .buildUpon()
                                        .add(SessionCommand(COMMAND, Bundle.EMPTY))
                                        .build()
                                else MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS
                            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                                .setAvailableSessionCommands(commands)
                                .build()
                        }

                        override fun onCustomCommand(
                            session: MediaSession,
                            controller: MediaSession.ControllerInfo,
                            command: SessionCommand,
                            args: Bundle,
                        ): ListenableFuture<SessionResult> {
                            val result = SettableFuture.create<SessionResult>()
                            if (
                                command.customAction != COMMAND ||
                                    controller.packageName != packageName
                            ) {
                                result.set(SessionResult(SessionError.ERROR_NOT_SUPPORTED))
                                return result
                            }
                            scope.launch {
                                ready.await()
                                attempt { handle(args) }
                                    .onSuccess {
                                        result.set(SessionResult(SessionResult.RESULT_SUCCESS))
                                    }
                                    .onFailure {
                                        error = it.message ?: strings[R.string.playback_error]
                                        publish()
                                        result.set(SessionResult(SessionError.ERROR_UNKNOWN))
                                    }
                            }
                            return result
                        }
                    }
                )
                .build()
        app.persistenceScope.launch {
            for ((snapshot, history) in saves) persist { app.settings.saveQueue(snapshot, history) }
        }
        app.persistenceScope.launch {
            for (row in listenWrites) persist {
                when (row) {
                    null -> {
                        app.database.withTransaction {
                            app.music.dao.clearStatistics()
                            app.music.dao.clearRuns()
                        }
                    }
                    is Listen -> app.music.dao.saveListen(row)
                    is PlaybackRun -> app.music.dao.saveRun(row)
                }
            }
        }
        app.persistenceScope.launch {
            for (first in soundWrites) {
                delay(250)
                val pending = mutableMapOf(first.first to first.second)
                while (true) {
                    val next = soundWrites.tryReceive().getOrNull() ?: break
                    pending[next.first] = next.second
                }
                pending.forEach { (device, profile) ->
                    persist { app.settings.updateAudioProfile(device, profile) }
                }
            }
        }
        scope.launch {
            try {
                journal.load(app.settings.queueHistory.first())
                settings = app.settings.settings.first()
                player.setHandleAudioBecomingNoisy(settings.pauseOnDisconnect)
                player.setPlaybackSpeed(settings.speed)
                updateRoute()
                updateGain(active)
                tracks =
                    app.music.dao
                        .availableTracks()
                        .filter { eligible(it, settings) }
                        .associateBy { it.id }
                app.settings.queue.first()?.let { saved ->
                    val kept =
                        saved.entries.mapIndexedNotNull { i, e ->
                            if (e.trackId in tracks) i to e else null
                        }
                    if (kept.isNotEmpty()) {
                        val index =
                            kept
                                .indexOfFirst { it.first >= saved.index }
                                .let { if (it < 0) kept.lastIndex else it }
                        queue.restore(
                            saved.copy(
                                source = saved.source.filter { it in tracks },
                                entries = kept.map { it.second },
                                index = index,
                                playbackMs =
                                    if (kept[index].first == saved.index) saved.playbackMs else 0,
                            )
                        )
                        rebuild(false, queue.state.playbackMs)
                    }
                }
            } catch (e: Exception) {
                error = e.message
            } finally {
                ready.complete(Unit)
                publish()
            }
            launch {
                app.settings.settings.collect { next ->
                    val previous = settings
                    val fadeChanged = previous.crossfadeSeconds != next.crossfadeSeconds
                    if (!next.statistics && settings.statistics) {
                        flushListen()
                        listen = null
                        fadeListen = null
                    }
                    settings = next
                    if (previous.pauseOnDisconnect != next.pauseOnDisconnect)
                        player.setHandleAudioBecomingNoisy(next.pauseOnDisconnect)
                    spare?.player?.setHandleAudioBecomingNoisy(next.pauseOnDisconnect)
                    if (previous.speed != next.speed) {
                        player.setPlaybackSpeed(next.speed)
                        spare?.player?.setPlaybackSpeed(next.speed)
                    }
                    if (previous.audioProfiles != next.audioProfiles) updateRoute()
                    if (previous.replayGain != next.replayGain) {
                        updateGain(active)
                        spare?.let { updateGain(it) }
                    }
                    if (
                        previous.speed != next.speed ||
                            previous.replayGain != next.replayGain ||
                            fadeChanged ||
                            previous.visualizer != next.visualizer
                    ) {
                        applyAudioPolicy()
                        wakeups.trySend(Unit)
                    }
                    if (fadeChanged) {
                        cancelFade()
                        prepareFade()
                    }
                    if (next.statistics && listen == null && player.isPlaying) beginListen()
                }
            }
            launch {
                combine(
                        app.music.dao.tracks(),
                        app.settings.settings.distinctUntilChanged { old, next ->
                            old.minimumSeconds == next.minimumSeconds &&
                                old.includedFolders == next.includedFolders &&
                                old.excludedFolders == next.excludedFolders
                        },
                    ) { rows, rules ->
                        rows.filter { eligible(it, rules) }
                    }
                    .distinctUntilChanged()
                    .collect { rows ->
                        val allowed = rows.associateBy { it.id }
                        val old = queue.state
                        val kept = old.entries.withIndex().filter { it.value.trackId in allowed }
                        val removed =
                            old.entries.any { it.trackId !in allowed } ||
                                old.source.any { it !in allowed }
                        val currentRemoved = old.current?.trackId?.let { it !in allowed } == true
                        if (currentRemoved) {
                            cancelFade()
                            flushListen()
                            listen = null
                        }
                        tracks = allowed
                        if (removed) {
                            val currentKept = kept.indexOfFirst { it.index == old.index }
                            val next = kept.indexOfFirst { it.index > old.index }
                            queue.restore(
                                old.copy(
                                    source = old.source.filter { it in allowed },
                                    entries = kept.map { it.value },
                                    index =
                                        if (kept.isEmpty()) 0
                                        else if (currentKept >= 0) currentKept
                                        else if (next >= 0) next else kept.lastIndex,
                                    playbackMs =
                                        if (currentKept >= 0) player.currentPosition else 0,
                                )
                            )
                            cancelFade()
                            if (kept.isEmpty()) {
                                player.stop()
                                player.clearMediaItems()
                            } else rebuild(player.playWhenReady, queue.state.playbackMs)
                            if (currentRemoved && kept.isNotEmpty()) beginListen()
                            save()
                        }
                        publish()
                    }
            }
            launch {
                combine(
                        app.playback.uiVisible,
                        app.playback.meterVisible,
                        app.playback.lyricsVisible,
                        app.playback.systemSaving,
                    ) { _, _, _, _ ->
                        Unit
                    }
                    .collect {
                        applyAudioPolicy()
                        wakeups.trySend(Unit)
                    }
            }
            launch { app.playback.lyricsDeadline.collect { wakeups.trySend(Unit) } }
            var checkpoint = SystemClock.elapsedRealtime()
            var publication = checkpoint
            while (isActive) {
                val nowBefore = SystemClock.elapsedRealtime()
                val fade =
                    PlaybackPowerPolicy.fadeWindow(
                        settings.crossfadeSeconds,
                        player.duration,
                        settings.speed,
                    )
                val remaining =
                    if (player.duration > 0) player.duration - player.currentPosition
                    else Long.MAX_VALUE
                val preparation =
                    if (
                        fade > 0 &&
                            fadeListen == null &&
                            queue.state.repeat != RepeatMode.ONE &&
                            queue.state.index + 1 < queue.state.entries.size &&
                            remaining != Long.MAX_VALUE
                    )
                        (remaining / settings.speed.coerceAtLeast(0.25f) -
                                fade -
                                if (spare == null) 2500 else 0)
                            .toLong()
                    else null
                val nextLyric = app.playback.lyricsDeadline.value
                val lyricsDelay =
                    if (nextLyric == Long.MAX_VALUE) 1000L
                    else
                        ((nextLyric - player.currentPosition) / settings.speed.coerceAtLeast(0.25f))
                            .toLong()
                            .coerceIn(50, 1000)
                val interval =
                    PlaybackPowerPolicy.waitMs(
                        player.isPlaying,
                        app.playback.uiVisible.value,
                        app.playback.lyricsVisible.value,
                        saving(),
                        fadeListen != null,
                        nowBefore - checkpoint,
                        if (sleepUntil > 0) sleepUntil - nowBefore else null,
                        preparation,
                        lyricsDelay,
                    )
                val timedOut =
                    if (interval == Long.MAX_VALUE) {
                        wakeups.receive()
                        false
                    } else
                        withTimeoutOrNull(interval) {
                            wakeups.receive()
                            true
                        } == null
                serviceTicks++
                if (timedOut) timerTicks++
                tick()
                prepareFade()
                updateFade()
                val now = SystemClock.elapsedRealtime()
                if (sleepUntil > 0 && now >= sleepUntil) {
                    player.pause()
                    sleepUntil = 0
                    publish()
                }
                if (player.isPlaying && now - checkpoint >= 30_000) {
                    flushListen()
                    save()
                    failures = 0
                    error = null
                    checkpoint = now
                }
                if (
                    player.isPlaying &&
                        app.playback.uiVisible.value &&
                        now - publication >=
                            if (
                                app.playback.lyricsVisible.value &&
                                    !saving() &&
                                    player.currentPosition >= app.playback.lyricsDeadline.value
                            )
                                50
                            else 1000
                ) {
                    publish()
                    publication = now
                }
            }
        }
    }

    private fun createSlot(focus: Boolean): Slot {
        val processor = GainAudioProcessor()
        lateinit var slot: Slot
        val renderers =
            object : androidx.media3.exoplayer.DefaultRenderersFactory(this) {
                override fun buildAudioSink(
                    context: android.content.Context,
                    enableFloatOutput: Boolean,
                    enableAudioTrackPlaybackParams: Boolean,
                ): androidx.media3.exoplayer.audio.AudioSink =
                    androidx.media3.exoplayer.audio.DefaultAudioSink.Builder(context)
                        .setEnableFloatOutput(false)
                        .setAudioTrackProvider { config, attributes, sessionId, sinkContext ->
                            val audioTrack =
                                androidx.media3.exoplayer.audio.DefaultAudioSink.AudioTrackProvider
                                    .DEFAULT
                                    .getAudioTrack(config, attributes, sessionId, sinkContext)
                            val listener =
                                android.media.AudioRouting.OnRoutingChangedListener {
                                    if (::active.isInitialized && slot === active) updateRoute()
                                }
                            audioTrack.addOnRoutingChangedListener(
                                listener,
                                android.os.Handler(mainLooper),
                            )
                            scope.launch {
                                slot.audioTrack?.let { old ->
                                    slot.routingListener?.let {
                                        old.removeOnRoutingChangedListener(it)
                                    }
                                }
                                slot.audioTrack = audioTrack
                                slot.routingListener = listener
                                if (::active.isInitialized && slot === active) updateRoute()
                            }
                            audioTrack
                        }
                        .setAudioProcessors(arrayOf(processor))
                        .build()
            }
        val p =
            ExoPlayer.Builder(this, renderers)
                .setAudioAttributes(audioAttributes, focus)
                .setHandleAudioBecomingNoisy(settings.pauseOnDisconnect)
                .build()

        p.trackSelectionParameters =
            p.trackSelectionParameters
                .buildUpon()
                .setAudioOffloadPreferences(
                    TrackSelectionParameters.AudioOffloadPreferences.Builder()
                        .setAudioOffloadMode(
                            TrackSelectionParameters.AudioOffloadPreferences
                                .AUDIO_OFFLOAD_MODE_DISABLED
                        )
                        .build()
                )
                .build()
        p.setWakeMode(C.WAKE_MODE_LOCAL)
        slot = Slot(p, processor)
        processor.onLevels = { levels ->
            if (::active.isInitialized && slot === active) app.playback.publishLevels(levels)
        }
        processor.dsp.profile =
            liveProfiles[deviceKey] ?: SoundProfiles.read(settings.audioProfiles, deviceKey)
        p.addListener(
            object : Player.Listener {
                override fun onMetadata(metadata: Metadata) {
                    updateGainTags(slot, metadata)
                }

                override fun onTracksChanged(tracks: Tracks) {
                    tracks.groups.forEach { g ->
                        for (i in 0 until g.length) g.getTrackFormat(i).metadata?.let {
                            updateGainTags(slot, it)
                        }
                    }
                }

                override fun onMediaItemTransition(item: MediaItem?, reason: Int) {

                    if (!::active.isInitialized || slot !== active || syncing) return
                    if (
                        reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO &&
                            fadeListen != null &&
                            spare?.player?.currentMediaItem?.mediaId == item?.mediaId
                    ) {
                        commitFade()
                        return
                    }
                    cancelFade()
                    flushListen()
                    slot.gainTag = null
                    slot.peakTag = null
                    slot.gain.gain = 1f
                    if (p.currentMediaItemIndex in queue.state.entries.indices)
                        queue.setIndex(p.currentMediaItemIndex)
                    beginListen()
                    extendQueue()
                    prepareFade()
                    save()
                    publish()
                }

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    if (!::active.isInitialized || slot !== active || syncing) return
                    tick()
                    wasPlaying = isPlaying
                    if (isPlaying) {
                        if (pausedAt > 0 && SystemClock.elapsedRealtime() - pausedAt >= 300000) {
                            flushRun()
                            runId = UUID.randomUUID().toString()
                            runTotal = 0
                            runStarted = System.currentTimeMillis()
                        }
                        if (runTotal == 0L) runStarted = System.currentTimeMillis()
                        pausedAt = 0
                    } else if (!p.playWhenReady) pausedAt = SystemClock.elapsedRealtime()
                    if (isPlaying && listen == null) beginListen()
                    if (fadeListen != null) spare?.player?.playWhenReady = isPlaying
                    if (!isPlaying) {
                        if (fadeListen == null) cancelFade()
                        app.playback.publishLevels(List(24) { 0f })
                        flushListen()
                        save()
                    }
                    publish()
                }

                override fun onPositionDiscontinuity(
                    oldPosition: Player.PositionInfo,
                    newPosition: Player.PositionInfo,
                    reason: Int,
                ) {
                    if (!::active.isInitialized || slot !== active || syncing) return
                    if (reason == Player.DISCONTINUITY_REASON_SEEK) {
                        tick()
                        cancelFade()
                        prepareFade()
                    }
                    save()
                    publish()
                }

                override fun onPlayerError(e: PlaybackException) {
                    if (!::active.isInitialized || slot !== active) return
                    cancelFade()
                    error = strings.format(R.string.could_not_open_the_track, e.errorCodeName)
                    publish()
                    failures++
                    if (failures < minOf(queue.state.source.size, 10) && p.hasNextMediaItem()) {
                        p.seekToNextMediaItem()
                        p.prepare()
                    } else p.pause()
                }

                override fun onPlaybackStateChanged(state: Int) {
                    if (::active.isInitialized && slot === active) {
                        if (state == Player.STATE_READY && error == null) failures = 0
                        publish()
                    }
                }
            }
        )
        return slot
    }

    private suspend fun handle(args: Bundle) {
        val action = args.getString("action")
        if (action !in listOf("sleep", "clearStats", "sound")) cancelFade()
        if (action in listOf("start", "insert", "shuffle", "repeat", "remove", "move")) {
            if (action == "remove")
                require(
                    args.getInt("index") > queue.state.index &&
                        args.getInt("index") in queue.state.entries.indices
                )
            if (action == "move")
                require(
                    args.getInt("from") > queue.state.index &&
                        args.getInt("to") > queue.state.index &&
                        args.getInt("from") in queue.state.entries.indices &&
                        args.getInt("to") in queue.state.entries.indices
                )
            queue.savePosition(player.currentPosition)
            journal.record(
                when (action) {
                    "remove" -> strings[R.string.remove_track]
                    "move" -> strings[R.string.reorder_tracks]
                    "shuffle" -> strings[R.string.shuffle]
                    "repeat" -> strings[R.string.repeat_mode]
                    "insert" -> strings[R.string.manual_selection]
                    else -> strings[R.string.new_queue]
                },
                queue.state,
            )
        }
        when (action) {
            "restoreQueue" -> {
                val revision = args.getLong("revision")
                val saved =
                    journal.revisions.firstOrNull { it.id == revision }?.snapshot
                        ?: error(strings[R.string.the_queue_history_has_changed])
                tracks =
                    app.music.dao
                        .availableTracks()
                        .filter { eligible(it, settings) }
                        .associateBy { it.id }
                val kept =
                    saved.entries.mapIndexedNotNull { index, entry ->
                        if (entry.trackId in tracks) index to entry else null
                    }
                require(kept.isNotEmpty()) {
                    strings[R.string.tracks_in_this_queue_are_unavailable]
                }
                val index =
                    kept
                        .indexOfFirst { it.first >= saved.index }
                        .let { if (it < 0) kept.lastIndex else it }
                val play = player.playWhenReady
                flushListen()
                journal.restore(revision)
                queue.restore(
                    saved.copy(
                        source = saved.source.filter { it in tracks },
                        entries = kept.map { it.second },
                        index = index,
                        playbackMs = if (kept[index].first == saved.index) saved.playbackMs else 0,
                    )
                )
                rebuild(play, queue.state.playbackMs)
                beginListen()
            }
            "sound" -> {
                val device = args.getString("device") ?: deviceKey
                val bands = args.getFloatArray("bands")
                fun Float.safe(min: Float, max: Float, fallback: Float = 0f) =
                    if (isFinite()) coerceIn(min, max) else fallback
                val profile =
                    SoundProfile(
                        args.getBoolean("enabled"),
                        List(5) { (bands?.getOrNull(it) ?: 0f).safe(-12f, 12f) },
                        args.getFloat("balance").safe(-1f, 1f),
                        args.getFloat("volume", 1f).safe(0f, 1f, 1f),
                    )
                liveProfiles[device] = profile
                if (device == deviceKey) {
                    active.gain.dsp.profile = profile
                    spare?.gain?.dsp?.profile = profile
                }
                soundWrites.trySend(device to profile)
                applyAudioPolicy()
            }
            "start" -> {
                tracks =
                    app.music.dao
                        .availableTracks()
                        .filter { eligible(it, settings) }
                        .associateBy { it.id }
                val ids =
                    (args.getLongArray("ids")?.toList() ?: emptyList()).filter { it in tracks }
                val selected = args.getLong("selected")
                require(selected in ids) { strings[R.string.this_track_is_no_longer_available] }
                flushListen()
                queue.start(
                    ids,
                    selected,
                    args.getBoolean("shuffle"),
                    args.getString("context") ?: strings[R.string.all_tracks],
                )
                error = null
                failures = 0
                rebuild(true)
                beginListen()
            }
            "restart" -> {
                flushListen()
                syncing = true
                player.seekTo(0)
                syncing = false
                beginListen()
                player.play()
            }
            "insert" -> {
                val id = args.getLong("id")
                app.music.dao
                    .track(id)
                    ?.takeIf { it.available && eligible(it, settings) }
                    ?.let { tracks = tracks + (id to it) }
                    ?: error(strings[R.string.track_unavailable])
                flushListen()
                queue.insertNow(id)
                rebuild(true)
                player.play()
                beginListen()
            }
            "shuffle" -> {
                queue.toggleShuffle(args.getBoolean("enabled"))
                rebuild(player.playWhenReady, player.currentPosition)
            }
            "repeat" -> {
                val next =
                    RepeatMode.entries[(queue.state.repeat.ordinal + 1) % RepeatMode.entries.size]
                queue.setRepeat(next)
                if (next != RepeatMode.QUEUE) {
                    val s = queue.state
                    val last = s.entries.indexOfLast { it.cycle == s.current?.cycle }
                    if (last >= 0 && last < s.entries.lastIndex) {
                        queue.restore(s.copy(entries = s.entries.take(last + 1)))
                        player.removeMediaItems(last + 1, player.mediaItemCount)
                    }
                }
                player.repeatMode =
                    if (next == RepeatMode.ONE) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
            }
            "clearStats" -> {
                runId = UUID.randomUUID().toString()
                runTotal = 0
                runStarted = System.currentTimeMillis()
                listen = null
                fadeListen = null
                listenWrites.trySend(null)
                beginListen()
            }
            "sleep" ->
                sleepUntil =
                    if (args.getInt("minutes") > 0)
                        SystemClock.elapsedRealtime() + args.getInt("minutes") * 60_000L
                    else 0
            "remove" -> {
                val index = args.getInt("index")
                queue.removeUpcoming(index)
                player.removeMediaItem(index)
            }
            "move" -> {
                val from = args.getInt("from")
                val to = args.getInt("to")
                queue.moveUpcoming(from, to)
                player.moveMediaItem(from, to)
            }
        }
        extendQueue()
        prepareFade()
        save()
        publish()
    }

    private fun updateGainTags(slot: Slot, metadata: Metadata) {
        for (i in 0 until metadata.length()) {
            val pair =
                when (val entry = metadata[i]) {
                    is androidx.media3.extractor.metadata.vorbis.VorbisComment ->
                        entry.key to entry.value
                    is androidx.media3.extractor.metadata.id3.TextInformationFrame ->
                        (entry.description ?: entry.id) to (entry.values.firstOrNull() ?: "")
                    else -> null
                } ?: continue
            when (pair.first.uppercase()) {
                "REPLAYGAIN_TRACK_GAIN" -> slot.gainTag = pair.second
                "REPLAYGAIN_TRACK_PEAK" -> slot.peakTag = pair.second
            }
        }
        updateGain(slot)
    }

    private fun updateRoute() {
        val outputs = audioManager.getDevices(android.media.AudioManager.GET_DEVICES_OUTPUTS)
        val routed = if (::active.isInitialized) active.audioTrack?.routedDevice else null
        val preferred =
            routed
                ?: outputs.firstOrNull {
                    it.type in
                        listOf(
                            android.media.AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
                            if (android.os.Build.VERSION.SDK_INT >= 31)
                                android.media.AudioDeviceInfo.TYPE_BLE_HEADSET
                            else -1,
                        )
                }
                ?: outputs.firstOrNull {
                    it.type in
                        listOf(
                            android.media.AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
                            android.media.AudioDeviceInfo.TYPE_WIRED_HEADSET,
                            android.media.AudioDeviceInfo.TYPE_USB_HEADSET,
                            android.media.AudioDeviceInfo.TYPE_USB_DEVICE,
                        )
                }
        val external = preferred?.takeUnless {
            it.type == android.media.AudioDeviceInfo.TYPE_BUILTIN_SPEAKER ||
                it.type == android.media.AudioDeviceInfo.TYPE_BUILTIN_EARPIECE
        }
        deviceKey =
            external?.let {
                val legacy = "${it.type}:${it.productName}"
                val address =
                    if (android.os.Build.VERSION.SDK_INT >= 28)
                        attempt { it.address }.getOrNull().orEmpty()
                    else ""
                if (address.isBlank()) legacy else "$legacy@$address"
            } ?: "speaker"
        deviceName = external?.productName?.toString() ?: strings[R.string.phone_speaker]
        if (::active.isInitialized)
            active.gain.dsp.profile =
                liveProfiles[deviceKey] ?: SoundProfiles.read(settings.audioProfiles, deviceKey)
        spare?.gain?.dsp?.profile =
            liveProfiles[deviceKey] ?: SoundProfiles.read(settings.audioProfiles, deviceKey)
        if (::active.isInitialized) {
            applyAudioPolicy()
            publish()
        } else wakeups.trySend(Unit)
    }

    private fun eligible(track: Track, rules: Settings) =
        track.durationMs >= rules.minimumSeconds * 1000L &&
            FolderRules.accepts(
                FolderRules.folder(track.path),
                rules.includedFolders,
                rules.excludedFolders,
            )

    private fun saving() = app.playback.systemSaving.value

    private fun applyAudioPolicy() {
        if (!::active.isInitialized) return
        val meter =
            app.playback.uiVisible.value &&
                app.playback.meterVisible.value &&
                settings.visualizer &&
                !saving()
        active.gain.meterEnabled = meter
        spare?.gain?.meterEnabled = false
        val offload =
            PlaybackPowerPolicy.offload(
                active.gain.dsp.profile,
                settings.replayGain,
                settings.crossfadeSeconds,
                settings.speed,
                meter,
            )
        val preferences =
            TrackSelectionParameters.AudioOffloadPreferences.Builder()
                .setAudioOffloadMode(
                    if (offload)
                        TrackSelectionParameters.AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_ENABLED
                    else
                        TrackSelectionParameters.AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_DISABLED
                )
                .setIsGaplessSupportRequired(false)
                .build()
        val next =
            player.trackSelectionParameters
                .buildUpon()
                .setAudioOffloadPreferences(preferences)
                .build()
        if (player.trackSelectionParameters != next) player.trackSelectionParameters = next
    }

    private fun updateGain(slot: Slot) {
        slot.gain.gain =
            if (settings.replayGain) ReplayGain.factor(slot.gainTag, slot.peakTag) else 1f
    }

    private fun media(t: Track) =
        MediaItem.Builder()
            .setMediaId(t.id.toString())
            .setUri(t.uri)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(t.title)
                    .setArtist(t.artist)
                    .setAlbumTitle(t.album)
                    .setArtworkUri(t.customArtwork?.toUri())
                    .build()
            )
            .build()

    private fun rebuild(play: Boolean, position: Long = 0) {
        syncing = true
        try {
            if (player.currentMediaItem?.mediaId != queue.state.current?.trackId?.toString()) {
                active.gainTag = null
                active.peakTag = null
                active.gain.gain = 1f
            }
            player.setMediaItems(
                queue.state.entries.map { media(tracks.getValue(it.trackId)) },
                queue.state.index,
                position,
            )
            player.repeatMode =
                if (queue.state.repeat == RepeatMode.ONE) Player.REPEAT_MODE_ONE
                else Player.REPEAT_MODE_OFF
            player.shuffleModeEnabled = false
            player.setPlaybackSpeed(settings.speed)
            player.prepare()
            player.playWhenReady = play
        } finally {
            syncing = false
        }
        extendQueue()
    }

    private fun extendQueue() {
        val playedManual = queue.removePlayedManualEntries()
        if (playedManual.isNotEmpty()) {
            syncing = true
            playedManual.asReversed().forEach { player.removeMediaItem(it) }
            syncing = false
        }
        val appended = queue.appendCycleIfNeeded()
        if (appended.isNotEmpty())
            player.addMediaItems(appended.map { media(tracks.getValue(it.trackId)) })
        val remove = queue.trimHistory()
        if (remove > 0) {
            syncing = true
            player.removeMediaItems(0, remove)
            syncing = false
        }
    }

    private fun prepareFade() {
        if (
            settings.crossfadeSeconds <= 0 ||
                queue.state.repeat == RepeatMode.ONE ||
                queue.state.index + 1 >= queue.state.entries.size
        )
            return
        if (
            spare != null ||
                !PlaybackPowerPolicy.prepareIncoming(
                    player.isPlaying,
                    player.duration - player.currentPosition,
                    PlaybackPowerPolicy.fadeWindow(
                        settings.crossfadeSeconds,
                        player.duration,
                        settings.speed,
                    ),
                    settings.speed,
                )
        )
            return

        incomingPrepared = true
        spare =
            createSlot(false).also { slot ->
                slot.player.volume = 0f
                slot.player.setPlaybackSpeed(settings.speed)
                slot.player.setMediaItems(
                    queue.state.entries.map { media(tracks.getValue(it.trackId)) },
                    queue.state.index + 1,
                    0,
                )
                slot.player.prepare()
            }
    }

    private fun updateFade() {
        if (settings.crossfadeSeconds <= 0 || !player.isPlaying || player.duration <= 0) return
        val next = spare ?: return
        val fadeMs =
            minOf(
                settings.crossfadeSeconds * 1000L,
                (player.duration / settings.speed / 2).toLong().coerceAtLeast(1),
            )
        val fraction =
            Crossfade.fraction(player.duration - player.currentPosition, settings.speed, fadeMs)
        if (fraction <= 0f || next.player.playbackState != Player.STATE_READY) return
        if (fadeListen == null) {
            fadeListen = newListen(queue.state.entries.getOrNull(queue.state.index + 1)?.trackId)
            next.player.play()
        }
        fadeFraction = fraction
        player.volume = 1f - fraction
        next.player.volume = fraction
    }

    private fun commitFade() {
        val next = spare ?: return
        tick()
        writeListen(listen)
        writeListen(fadeListen)
        val old = active
        active = next
        spare = null
        incomingPrepared = false
        listen = fadeListen
        fadeListen = null
        fadeFraction = 0f

        syncing = true
        session.setPlayer(player)
        old.player.pause()
        releaseSlot(old)
        player.setAudioAttributes(audioAttributes, true)
        player.setHandleAudioBecomingNoisy(settings.pauseOnDisconnect)
        player.volume = 1f
        player.repeatMode = Player.REPEAT_MODE_OFF
        syncing = false
        updateRoute()
        applyAudioPolicy()
        queue.setIndex(player.currentMediaItemIndex)
        previousTick = SystemClock.elapsedRealtime()
        wasPlaying = player.isPlaying
        extendQueue()
        prepareFade()
        save()
        publish()
    }

    private fun cancelFade() {
        if (spare == null) return
        tick()
        writeListen(fadeListen)
        fadeListen = null
        fadeFraction = 0f
        val old = spare
        spare = null
        incomingPrepared = false
        old?.let(::releaseSlot)
        player.volume = 1f
    }

    private fun newListen(id: Long?): ListeningSession? = id?.let {
        ListeningSession(
            it,
            tracks[it]?.durationMs ?: 0,
            System.currentTimeMillis(),
            UUID.randomUUID().toString(),
        )
    }

    private fun beginListen() {
        listen = newListen(queue.state.current?.trackId)
        previousTick = SystemClock.elapsedRealtime()
        wasPlaying = player.isPlaying
    }

    private fun tick() {
        val now = SystemClock.elapsedRealtime()
        val elapsed = (now - previousTick).coerceAtLeast(0)
        if (wasPlaying && settings.statistics) runTotal += elapsed
        val (first, second) =
            Crossfade.split(
                elapsed,
                if (fadeListen != null && spare?.player?.isPlaying == true) fadeFraction else 0f,
            )
        listen?.accrue(first, wasPlaying && settings.statistics)
        fadeListen?.accrue(second, wasPlaying && settings.statistics)
        previousTick = now
        wasPlaying = player.isPlaying
    }

    private fun writeListen(s: ListeningSession?) {
        if (s == null || s.listenedMs <= 0 || !settings.statistics) return
        val t = tracks[s.trackId] ?: return
        s.segments.forEachIndexed { index, segment ->
            listenWrites.trySend(
                Listen(
                    "${s.sessionId}:${segment.bucket}",
                    t.id,
                    t.title,
                    t.artist,
                    t.album,
                    t.genre,
                    t.artwork,
                    segment.startedAt,
                    segment.listenedMs,
                    s.qualified && index == 0,
                )
            )
        }
    }

    private fun flushRun() {
        if (runTotal > 0 && settings.statistics)
            listenWrites.trySend(PlaybackRun(runId, runStarted, runTotal))
    }

    private fun flushListen() {
        tick()
        flushRun()
        writeListen(listen)
        writeListen(fadeListen)
    }

    private fun save() {
        queue.savePosition(player.currentPosition)
        saves.trySend(queue.state to journal.revisions)
    }

    private fun publish() {
        if (!::active.isInitialized) return
        wakeups.trySend(Unit)
        app.playback.publish(
            PlaybackState(
                queue.state.current?.trackId?.let { tracks[it] },
                queue.state,
                player.isPlaying,
                player.currentPosition,
                player.duration.takeIf { it > 0 }
                    ?: queue.state.current?.trackId?.let { tracks[it]?.durationMs }
                    ?: 0,
                player.playbackState == Player.STATE_BUFFERING,
                error,
                sleepUntil,
                deviceKey,
                deviceName,
                journal.revisions,
            )
        )
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession = session

    private fun releaseSlot(slot: Slot) {
        slot.audioTrack?.let { track ->
            slot.routingListener?.let { track.removeOnRoutingChangedListener(it) }
        }
        slot.routingListener = null
        slot.audioTrack = null
        slot.player.release()
    }

    private suspend fun persist(block: suspend () -> Unit) {
        var failure: Exception? = null
        repeat(3) { index ->
            try {
                block()
                return
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                failure = error
                if (index < 2) delay(500L * (index + 1))
            }
        }
        val message = strings[R.string.save_failed]
        android.util.Log.w("LadPersistence", message, failure)
        app.playback.reportError(message)
        scope.launch {
            error = message
            publish()
        }
    }

    override fun onDestroy() {
        flushListen()
        save()
        audioManager.unregisterAudioDeviceCallback(deviceCallback)
        spare?.let(::releaseSlot)
        releaseSlot(active)
        session.release()
        saves.close()
        listenWrites.close()
        soundWrites.close()
        scope.cancel()
        super.onDestroy()
    }
}
