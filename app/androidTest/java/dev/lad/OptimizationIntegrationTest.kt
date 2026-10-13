package dev.lad

import android.content.ComponentName
import android.os.Bundle
import androidx.media3.session.*
import androidx.test.platform.app.InstrumentationRegistry
import dev.lad.data.*
import dev.lad.playback.PlaybackService
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class OptimizationIntegrationTest {
    @Test
    fun presetsAndQueueHistorySurviveDataStoreRoundTrip() = runBlocking {
        val app =
            InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
                as LadApplication
        val before = app.settings.settings.first()
        val next =
            AppearancePresets.lyrics
                .last()
                .apply(AppearancePresets.glow.last().apply(before))
                .copy(crossfadeSeconds = 0)
        try {
            app.settings.update(next)
            assertEquals(next, app.settings.settings.first())
            val snapshot =
                dev.lad.domain.QueueSnapshot(
                    entries = listOf(dev.lad.domain.QueueEntry(99, 0)),
                    playbackMs = 1234,
                )
            val rows = listOf(dev.lad.domain.QueueRevision(123, "Удаление трека", 456, snapshot))
            app.settings.saveQueue(snapshot, rows)
            assertEquals(rows, app.settings.queueHistory.first())
        } finally {
            app.settings.update(before)
            app.settings.saveQueue(dev.lad.domain.QueueSnapshot(), emptyList())
        }
    }

    @Test
    fun undoRestoresRemovedTrackAndPausedServiceStopsPolling() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext.applicationContext as LadApplication
        app.music.scan(Settings(minimumSeconds = 0))
        val tracks = app.music.dao.availableTracks().take(3)
        assertEquals(3, tracks.size)
        var future: com.google.common.util.concurrent.ListenableFuture<MediaController>? = null
        instrumentation.runOnMainSync {
            future =
                MediaController.Builder(
                        app,
                        SessionToken(app, ComponentName(app, PlaybackService::class.java)),
                    )
                    .buildAsync()
        }
        val controller = future!!.get(60, TimeUnit.SECONDS)
        fun command(action: String, configure: Bundle.() -> Unit = {}) {
            var result: com.google.common.util.concurrent.ListenableFuture<SessionResult>? = null
            instrumentation.runOnMainSync {
                result =
                    controller.sendCustomCommand(
                        SessionCommand("dev.lad.CONTROL", Bundle.EMPTY),
                        Bundle().apply {
                            putString("action", action)
                            configure()
                        },
                    )
            }
            assertEquals(0, result!!.get(60, TimeUnit.SECONDS).resultCode)
        }
        try {
            command("start") {
                putLongArray("ids", tracks.map { it.id }.toLongArray())
                putLong("selected", tracks.first().id)
            }
            instrumentation.runOnMainSync { controller.pause() }
            val original = app.playback.state.value.queue.entries
            command("remove") { putInt("index", 1) }
            assertEquals(original.size - 1, app.playback.state.value.queue.entries.size)
            command("restoreQueue") {
                putLong("revision", app.playback.state.value.queueHistory.last().id)
            }
            assertEquals(original, app.playback.state.value.queue.entries)
            Thread.sleep(1500)
            val count = PlaybackService.timerTicks
            Thread.sleep(1500)
            assertEquals("Paused service still wakes on a timer", count, PlaybackService.timerTicks)
            assertFalse(PlaybackService.incomingPrepared)
            assertNull(app.playback.state.value.error)
        } finally {
            instrumentation.runOnMainSync {
                controller.pause()
                controller.release()
            }
        }
    }
}
