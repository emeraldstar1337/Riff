package dev.lad.playback

import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import androidx.core.content.ContextCompat
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionToken
import dev.lad.R
import dev.lad.data.Track
import dev.lad.domain.QueueSnapshot
import dev.lad.i18n.AppLanguage
import dev.lad.i18n.TextResources
import dev.lad.util.attempt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

internal const val COMMAND = "dev.lad.CONTROL"

data class PlaybackState(
    val current: Track? = null,
    val queue: QueueSnapshot = QueueSnapshot(),
    val playing: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val loading: Boolean = false,
    val error: String? = null,
    val sleepUntil: Long = 0,
    val deviceKey: String = "speaker",
    val deviceName: String = "Phone speaker",
    val queueHistory: List<dev.lad.domain.QueueRevision> = emptyList(),
    val positionTimestampMs: Long = android.os.SystemClock.elapsedRealtime(),
)

class PlaybackRepository(private val context: Context) {
    private val strings
        get() = TextResources(AppLanguage.context(context))

    private val mutable = MutableStateFlow(PlaybackState())
    val state = mutable.asStateFlow()
    val uiVisible = MutableStateFlow(false)
    val meterVisible = MutableStateFlow(false)
    val lyricsVisible = MutableStateFlow(false)
    val lyricsDeadline = MutableStateFlow(Long.MAX_VALUE)
    val systemSaving = MutableStateFlow(false)

    fun restoreQueue(id: Long) = command("restoreQueue") { putLong("revision", id) }

    val levels = MutableStateFlow(List(24) { 0f })

    internal fun publishLevels(value: List<Float>) {
        levels.value = value
    }

    private var controllerFuture:
        com.google.common.util.concurrent.ListenableFuture<MediaController>? =
        null

    internal fun reportError(message: String) {
        mutable.value = mutable.value.copy(error = message)
    }

    internal fun publish(state: PlaybackState) {
        mutable.value = state
    }

    fun connect() {
        withController {}
    }

    private fun withController(action: (MediaController) -> Unit) {
        val executor = ContextCompat.getMainExecutor(context)
        executor.execute {
            val future =
                controllerFuture
                    ?: MediaController.Builder(
                            context,
                            SessionToken(
                                context,
                                ComponentName(context, PlaybackService::class.java),
                            ),
                        )
                        .setListener(
                            object : MediaController.Listener {
                                override fun onDisconnected(controller: MediaController) {
                                    controllerFuture = null
                                }
                            }
                        )
                        .buildAsync()
                        .also { controllerFuture = it }
            future.addListener(
                {
                    val controller =
                        try {
                            future.get()
                        } catch (e: Exception) {
                            if (controllerFuture === future) controllerFuture = null
                            mutable.value =
                                mutable.value.copy(
                                    error =
                                        e.message
                                            ?: strings[R.string.could_not_connect_to_the_player]
                                )
                            return@addListener
                        }
                    attempt { action(controller) }
                        .onFailure {
                            mutable.value =
                                mutable.value.copy(
                                    error =
                                        it.message
                                            ?: strings[
                                                R.string.could_not_execute_the_playback_command]
                                )
                        }
                },
                executor,
            )
        }
    }

    fun command(action: String, configure: Bundle.() -> Unit = {}) {
        withController {
            it.sendCustomCommand(
                SessionCommand(COMMAND, Bundle.EMPTY),
                Bundle().apply {
                    putString("action", action)
                    configure()
                },
            )
        }
    }

    fun start(ids: List<Long>, selected: Long, shuffle: Boolean, contextName: String) =
        command("start") {
            putLongArray("ids", ids.toLongArray())
            putLong("selected", selected)
            putBoolean("shuffle", shuffle)
            putString("context", contextName)
        }

    fun select(id: Long) = command("insert") { putLong("id", id) }

    fun toggle() = withController {
        if (it.playWhenReady) it.pause()
        else if (it.playbackState == androidx.media3.common.Player.STATE_ENDED) command("restart")
        else {
            if (it.playbackState == androidx.media3.common.Player.STATE_IDLE) it.prepare()
            it.play()
        }
    }

    fun next() = withController { it.seekToNextMediaItem() }

    fun previous() = withController {
        if (it.currentPosition > 3000) it.seekTo(0) else it.seekToPreviousMediaItem()
    }

    fun seek(ms: Long) = withController { it.seekTo(ms) }

    fun shuffle(enabled: Boolean) = command("shuffle") { putBoolean("enabled", enabled) }

    fun repeat() = command("repeat")

    fun soundProfile(device: String, profile: dev.lad.domain.SoundProfile) =
        command("sound") {
            putString("device", device)
            putBoolean("enabled", profile.enabled)
            putFloatArray("bands", profile.bands.toFloatArray())
            putFloat("balance", profile.balance)
            putFloat("volume", profile.volume)
        }

    fun sleep(minutes: Int) = command("sleep") { putInt("minutes", minutes) }

    fun remove(index: Int) = command("remove") { putInt("index", index) }

    fun move(from: Int, to: Int) =
        command("move") {
            putInt("from", from)
            putInt("to", to)
        }
}
