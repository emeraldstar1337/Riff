package dev.lad

import android.content.ComponentName
import android.os.Bundle
import androidx.media3.session.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.lad.data.*
import dev.lad.domain.SoundProfile
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LiveSoundIntegrationTest {
    @Test
    fun equalizerChangesActualDecodedPcmAndPersistsWithoutChangingQueue() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext.applicationContext as LadApplication
        app.music.scan(Settings(minimumSeconds = 0))
        val track = app.music.dao.availableTracks().first { it.title == "Lad Test 1" }
        var future: com.google.common.util.concurrent.ListenableFuture<MediaController>? = null
        instrumentation.runOnMainSync {
            future =
                MediaController.Builder(
                        app,
                        SessionToken(
                            app,
                            ComponentName(app, dev.lad.playback.PlaybackService::class.java),
                        ),
                    )
                    .buildAsync()
        }
        val controller = future!!.get(30, TimeUnit.SECONDS)
        fun command(bundle: Bundle) {
            var result: com.google.common.util.concurrent.ListenableFuture<SessionResult>? = null
            instrumentation.runOnMainSync {
                result =
                    controller.sendCustomCommand(
                        SessionCommand("dev.lad.CONTROL", Bundle.EMPTY),
                        bundle,
                    )
            }
            assertEquals(0, result!!.get(30, TimeUnit.SECONDS).resultCode)
        }
        fun profile(value: SoundProfile) {
            command(
                Bundle().apply {
                    putString("action", "sound")
                    putString("device", "speaker")
                    putBoolean("enabled", value.enabled)
                    putFloatArray("bands", value.bands.toFloatArray())
                    putFloat("volume", value.volume)
                    putFloat("balance", value.balance)
                }
            )
        }
        fun awaitLevels(predicate: (Float) -> Boolean): Float {
            val deadline = System.currentTimeMillis() + 15000
            do {
                Thread.sleep(100)
                val level = app.playback.levels.value.average().toFloat()
                if (predicate(level)) return level
            } while (System.currentTimeMillis() < deadline)
            fail("PCM level condition was not reached: ${app.playback.levels.value}")
            return 0f
        }
        app.playback.uiVisible.value = true
        app.playback.meterVisible.value = true
        val initial = app.settings.settings.first()
        val before = SoundProfiles.read(initial.audioProfiles, "speaker")
        try {
            app.settings.update { it.copy(visualizer = true) }
            kotlinx.coroutines.delay(500)
            profile(SoundProfile())
            command(
                Bundle().apply {
                    putString("action", "start")
                    putLongArray("ids", longArrayOf(track.id))
                    putLong("selected", track.id)
                    putString("context", "PCM test")
                }
            )
            val startDeadline = System.currentTimeMillis() + 30000
            while (
                app.playback.state.value.current?.id != track.id ||
                    !app.playback.state.value.playing
            ) {
                check(System.currentTimeMillis() < startDeadline) {
                    "The PCM fixture did not start"
                }
                Thread.sleep(100)
            }

            Thread.sleep(600)
            val flat = awaitLevels { it > 0.12f }
            val queue = app.playback.state.value.queue.entries
            val cut = SoundProfile(true, listOf(0f, -12f, 0f, 0f, 0f))
            profile(cut)
            val filtered = awaitLevels { it > 0.001f && it < flat * 0.4f }
            println("Decoded PCM: flat=$flat, EQ cut=$filtered, ratio=${filtered / flat}")
            kotlinx.coroutines.withTimeout(5000) {
                app.settings.settings.first {
                    SoundProfiles.read(it.audioProfiles, "speaker") == cut
                }
            }
            assertEquals(queue, app.playback.state.value.queue.entries)
            profile(SoundProfile(volume = 0f))
            awaitLevels { it < 0.001f }
            profile(SoundProfile())
            awaitLevels { it > flat * 0.8f }
            assertNull(app.playback.state.value.error)
        } finally {
            profile(before)
            app.settings.update { it.copy(visualizer = initial.visualizer) }
            app.playback.meterVisible.value = false
            app.playback.uiVisible.value = false
            instrumentation.runOnMainSync {
                controller.pause()
                controller.release()
            }
        }
    }
}
