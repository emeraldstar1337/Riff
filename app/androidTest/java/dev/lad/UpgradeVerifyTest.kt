package dev.lad

import androidx.test.platform.app.InstrumentationRegistry
import dev.lad.domain.PlaybackPowerPolicy
import dev.lad.domain.SoundProfile
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class UpgradeVerifyTest {
    @Test
    fun version09KeepsUserDataAndMigratesAudioDefaults() = runBlocking {
        val app =
            InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
                as LadApplication
        val dao = app.music.dao
        assertTrue(dao.availableTracks().any { it.favorite })
        assertTrue(
            dao.playlists().first().any { it.name == "Upgrade preservation" && it.tracks.size == 3 }
        )
        val saved = dao.listens().first().first { it.sessionId == "upgrade-preservation" }
        assertEquals(31000L, saved.listenedMs)
        assertTrue(dao.lyrics(saved.trackId)?.text?.contains("Saved before the update") == true)
        val queue = app.settings.queue.first()!!
        assertEquals(saved.trackId, queue.current?.trackId)
        assertEquals(4567L, queue.playbackMs)
        val settings = app.settings.settings.first()
        assertEquals(0, settings.crossfadeSeconds)
        assertFalse(settings.visualizer)
        assertFalse(settings.glowMotion)
        assertTrue(
            PlaybackPowerPolicy.offload(
                SoundProfile(),
                settings.replayGain,
                settings.crossfadeSeconds,
                settings.speed,
                false,
            )
        )
    }
}
