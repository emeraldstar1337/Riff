package dev.lad

import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.platform.app.InstrumentationRegistry
import dev.lad.playback.PlaybackState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class QueuePlaybackRegressionTest {
    @get:Rule val activity = ActivityScenarioRule(MainActivity::class.java)

    @Test
    fun disablingShuffleRestoresActualPlaybackOrderWithoutRestartingTrack() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext.applicationContext as LadApplication
        val original = app.settings.settings.first()
        fun await(check: (PlaybackState) -> Boolean) {
            val until = System.currentTimeMillis() + 90000
            while (!check(app.playback.state.value) && System.currentTimeMillis() < until) Thread
                .sleep(100)
            assertTrue(app.playback.state.value.toString(), check(app.playback.state.value))
        }
        try {
            app.settings.update {
                it.copy(bitPerfect = false, speed = 1f, minimumSeconds = 0, crossfadeSeconds = 0)
            }
            app.music.scan(app.settings.settings.first())
            val tracks = app.music.dao.availableTracks().sortedBy { it.id }.take(6)
            assertTrue(tracks.size >= 4)
            val ids = tracks.map { it.id }
            val selected = ids[1]
            instrumentation.runOnMainSync {
                app.playback.start(ids, selected, true, "Queue regression")
            }
            await { it.playing && it.queue.shuffle && it.current?.id == selected }
            instrumentation.runOnMainSync { app.playback.seek(12000) }
            await { it.positionMs >= 11000 }
            instrumentation.runOnMainSync { app.playback.shuffle(false) }
            await { !it.queue.shuffle && it.playing && it.current?.id == selected }
            val restored = app.playback.state.value
            assertEquals(ids, restored.queue.currentCycle.map { it.trackId })
            assertTrue(restored.positionMs >= 11000)
            instrumentation.runOnMainSync { app.playback.next() }
            await { it.playing && it.current?.id == ids[2] }
        } finally {
            instrumentation.runOnMainSync {
                if (app.playback.state.value.playing) app.playback.toggle()
            }
            app.settings.update(original)
        }
    }

    @Test
    fun manualSecondTrackThenShuffleOffKeepsCounterAndNativeNextInSync() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext.applicationContext as LadApplication
        val original = app.settings.settings.first()
        fun await(check: (PlaybackState) -> Boolean) {
            val until = System.currentTimeMillis() + 90000
            while (!check(app.playback.state.value) && System.currentTimeMillis() < until) Thread
                .sleep(100)
            assertTrue(app.playback.state.value.toString(), check(app.playback.state.value))
        }
        var controller: androidx.media3.session.MediaController? = null
        try {
            app.settings.update {
                it.copy(bitPerfect = false, speed = 1f, minimumSeconds = 0, crossfadeSeconds = 0)
            }
            app.music.scan(app.settings.settings.first())
            val ids = app.music.dao.availableTracks().sortedBy { it.id }.take(6).map { it.id }
            assertTrue(ids.size >= 5)
            instrumentation.runOnMainSync {
                app.playback.start(ids, ids.first(), true, "Queue regression")
            }
            await { it.current?.id == ids.first() && it.queue.shuffle && it.playing }
            instrumentation.runOnMainSync { app.playback.select(ids[1]) }
            await { it.current?.id == ids[1] && it.queue.current?.manual == true }
            instrumentation.runOnMainSync { app.playback.shuffle(false) }
            await { !it.queue.shuffle && it.current?.id == ids[1] && it.queue.position == 2 }
            assertFalse(app.playback.state.value.queue.current!!.manual)
            var future:
                com.google.common.util.concurrent.ListenableFuture<
                    androidx.media3.session.MediaController
                >? =
                null
            instrumentation.runOnMainSync {
                future =
                    androidx.media3.session.MediaController.Builder(
                            app,
                            androidx.media3.session.SessionToken(
                                app,
                                android.content.ComponentName(
                                    app,
                                    dev.lad.playback.PlaybackService::class.java,
                                ),
                            ),
                        )
                        .buildAsync()
            }
            controller = future!!.get(30, java.util.concurrent.TimeUnit.SECONDS)
            repeat(3) { n ->
                instrumentation.runOnMainSync {
                    if (n == 1) controller!!.seekToNextMediaItem() else app.playback.next()
                }
                await {
                    it.current?.id == ids[n + 2] &&
                        it.queue.current?.trackId == ids[n + 2] &&
                        it.queue.position == n + 3
                }
                instrumentation.runOnMainSync {
                    assertEquals(ids[n + 2].toString(), controller!!.currentMediaItem?.mediaId)
                    assertEquals(n + 2, controller!!.currentMediaItemIndex)
                }
            }
            instrumentation.runOnMainSync { app.playback.jump(1) }
            await { it.current?.id == ids[1] && it.queue.position == 2 }
            assertFalse(app.playback.state.value.queue.current!!.manual)
            instrumentation.runOnMainSync { app.playback.next() }
            await { it.current?.id == ids[2] && it.queue.position == 3 }
            instrumentation.runOnMainSync { app.playback.select(ids[4]) }
            await { it.current?.id == ids[4] && it.queue.position == 5 }
            assertFalse(app.playback.state.value.queue.current!!.manual)
            instrumentation.runOnMainSync { app.playback.next() }
            await { it.current?.id == ids[5] && it.queue.position == 6 }
        } finally {
            instrumentation.runOnMainSync {
                controller?.release()
                if (app.playback.state.value.playing) app.playback.toggle()
            }
            app.settings.update(original)
        }
    }
}
