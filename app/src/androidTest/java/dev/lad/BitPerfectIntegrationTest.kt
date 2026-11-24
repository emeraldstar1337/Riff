package dev.lad

import android.content.ComponentName
import android.media.AudioManager
import android.os.Build
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.platform.app.InstrumentationRegistry
import dev.lad.domain.BitPerfectStatus
import dev.lad.i18n.AppLanguage
import dev.lad.i18n.TextResources
import dev.lad.playback.PlaybackService
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class BitPerfectIntegrationTest {
    @get:Rule val activity = ActivityScenarioRule(MainActivity::class.java)
    private val instrumentation
        get() = InstrumentationRegistry.getInstrumentation()

    private val app
        get() = instrumentation.targetContext.applicationContext as LadApplication

    private fun await(condition: () -> Boolean) {
        val until = System.currentTimeMillis() + 90000
        while (!condition() && System.currentTimeMillis() < until) Thread.sleep(100)
        assertTrue(app.playback.state.value.toString(), condition())
    }

    @Test
    fun unsupportedOutputNeverClaimsBitPerfectAndPreferencesSurvive() = runBlocking {
        val original = app.settings.settings.first()
        val manager = app.getSystemService(AudioManager::class.java)
        assertFalse(
            manager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any {
                it.type == android.media.AudioDeviceInfo.TYPE_USB_DEVICE ||
                    it.type == android.media.AudioDeviceInfo.TYPE_USB_HEADSET
            }
        )
        try {
            app.settings.update {
                it.copy(bitPerfect = true, replayGain = true, speed = 1.25f, crossfadeSeconds = 3)
            }
            instrumentation.runOnMainSync { app.playback.connect() }
            await {
                app.playback.state.value.bitPerfect.status ==
                    if (Build.VERSION.SDK_INT >= 34) BitPerfectStatus.CONNECT_USB
                    else BitPerfectStatus.ANDROID_REQUIRED
            }
            val saved = app.settings.settings.first()
            assertTrue(saved.bitPerfect)
            assertTrue(saved.replayGain)
            assertEquals(1.25f, saved.speed)
            assertEquals(3, saved.crossfadeSeconds)
            app.settings.update { it.copy(bitPerfect = false) }
            await { app.playback.state.value.bitPerfect.status == BitPerfectStatus.OFF }
        } finally {
            app.settings.update(original)
        }
    }

    @Test
    fun enablingModeDuringPlaybackPreservesPositionAndRestoresSpeed() = runBlocking {
        val original = app.settings.settings.first()
        var controller: MediaController? = null
        try {
            app.settings.update {
                it.copy(
                    bitPerfect = false,
                    speed = 1.25f,
                    replayGain = true,
                    crossfadeSeconds = 3,
                    minimumSeconds = 0,
                )
            }
            app.music.scan(app.settings.settings.first())
            val tracks = app.music.dao.availableTracks()
            assertTrue(tracks.isNotEmpty())
            val future =
                arrayOfNulls<com.google.common.util.concurrent.ListenableFuture<MediaController>>(1)
            instrumentation.runOnMainSync {
                future[0] =
                    MediaController.Builder(
                            app,
                            SessionToken(app, ComponentName(app, PlaybackService::class.java)),
                        )
                        .buildAsync()
            }
            controller = future[0]!!.get(20, TimeUnit.SECONDS)
            instrumentation.runOnMainSync {
                app.playback.start(
                    tracks.map { it.id },
                    tracks.first().id,
                    false,
                    "Integration Fixtures",
                )
            }
            await { app.playback.state.value.playing }
            instrumentation.runOnMainSync { controller!!.seekTo(12000) }
            await { app.playback.state.value.positionMs >= 11000 }
            app.settings.update { it.copy(bitPerfect = true) }
            await {
                app.playback.state.value.playing &&
                    app.playback.state.value.bitPerfect.status != BitPerfectStatus.OFF
            }
            instrumentation.runOnMainSync {
                assertEquals(1f, controller!!.playbackParameters.speed)
            }
            assertTrue(app.playback.state.value.positionMs >= 11000)
            assertNotEquals(BitPerfectStatus.ACTIVE, app.playback.state.value.bitPerfect.status)
            app.settings.update { it.copy(bitPerfect = false) }
            await {
                app.playback.state.value.playing &&
                    app.playback.state.value.bitPerfect.status == BitPerfectStatus.OFF
            }
            instrumentation.runOnMainSync {
                assertEquals(1.25f, controller!!.playbackParameters.speed)
            }
            assertTrue(app.settings.settings.first().replayGain)
            assertEquals(3, app.settings.settings.first().crossfadeSeconds)
        } finally {
            instrumentation.runOnMainSync {
                controller?.pause()
                controller?.release()
            }
            app.settings.update(original)
        }
    }

    @Test
    fun bitPerfectStatusAndFormatAreLocalized() {
        val en = TextResources(AppLanguage.context(app, "en"))
        val ru = TextResources(AppLanguage.context(app, "ru"))
        assertEquals("Connect a USB DAC", en[R.string.bit_perfect_connect_usb])
        assertEquals("Подключи USB-ЦАП", ru[R.string.bit_perfect_connect_usb])
        assertEquals(
            "96000 Hz · 24-bit · 2 ch",
            en.format(R.string.bit_perfect_format, 96000, "24-bit", 2),
        )
        assertEquals(
            "96000 Гц · 24 бит · 2 кан.",
            ru.format(R.string.bit_perfect_format, 96000, "24 бит", 2),
        )
    }
}
