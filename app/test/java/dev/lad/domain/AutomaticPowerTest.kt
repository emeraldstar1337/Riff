package dev.lad.domain

import dev.lad.data.Settings
import org.junit.Assert.*
import org.junit.Test

class AutomaticPowerTest {
    @Test
    fun freshInstallAllowsOffloadWithoutDisablingUserEffects() {
        val settings = Settings()
        assertEquals(0, settings.crossfadeSeconds)
        assertFalse(settings.visualizer)
        assertFalse(settings.glowMotion)
        assertFalse(settings.glowShapes)
        assertTrue(
            PlaybackPowerPolicy.offload(
                SoundProfile(),
                settings.replayGain,
                settings.crossfadeSeconds,
                settings.speed,
                false,
            )
        )
        assertFalse(PlaybackPowerPolicy.offload(SoundProfile(balance = 0.2f), false, 0, 1f, false))
    }

    @Test
    fun ordinaryLyricsDoNotNeedWordRatePolling() {
        assertEquals(
            1000L,
            PlaybackPowerPolicy.waitMs(true, true, true, false, false, 0, null, null, 1000),
        )
        assertEquals(
            70L,
            PlaybackPowerPolicy.waitMs(true, true, true, false, false, 0, null, null, 70),
        )
        assertEquals(
            Long.MAX_VALUE,
            PlaybackPowerPolicy.waitMs(false, false, true, false, false, 0, null, null, 70),
        )
    }
}
