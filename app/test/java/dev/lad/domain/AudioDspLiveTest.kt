package dev.lad.domain

import kotlin.math.*
import org.junit.Assert.*
import org.junit.Test

class AudioDspLiveTest {
    private fun measure(dsp: AudioDsp, frequency: Double): Double {
        var energy = 0.0
        repeat(22050) { i ->
            val output = dsp.sample((.1 * sin(2 * PI * frequency * i / 44100)).toFloat(), 0)
            assertTrue(output.isFinite())
            if (i >= 11025) energy += output * output
        }
        return sqrt(energy / 11025)
    }

    @Test
    fun liveProfileChangesAffectRunningStreamWithoutReconfiguration() {
        val dsp = AudioDsp()
        dsp.configure(44100, 1)
        val flat = measure(dsp, 60.0)
        dsp.profile = SoundProfile(true, listOf(-12f, 0f, 0f, 0f, 0f))
        val cut = measure(dsp, 60.0)
        assertTrue("60 Hz cut must be audible", cut < flat * .3)
        dsp.profile = SoundProfile(enabled = false)
        assertEquals(flat, measure(dsp, 60.0), flat * .02)
    }

    @Test
    fun everyBandChangesItsCenterFrequencyWithSafeHeadroom() {
        listOf(60.0, 230.0, 910.0, 3600.0, 14000.0).forEachIndexed { band, frequency ->
            val dsp = AudioDsp()
            dsp.configure(44100, 1)
            val flat = measure(dsp, frequency)
            dsp.profile = SoundProfile(true, List(5) { if (it == band) -12f else 0f })
            assertTrue(
                "Band $band must attenuate its frequency",
                measure(dsp, frequency) < flat * .3,
            )
        }
    }
}
