package dev.lad.domain

import kotlin.math.*
import org.junit.Assert.*
import org.junit.Test

class AudioDspTest {
    @Test
    fun flatProfilePreservesSignalAndBalanceAttenuatesOnlyOppositeChannel() {
        val dsp = AudioDsp()
        dsp.configure(44100, 2)
        assertEquals(0.4f, dsp.sample(0.4f, 0), 0.00001f)
        dsp.profile = SoundProfile(balance = 1f, volume = 0.5f)
        assertEquals(0f, dsp.sample(0.4f, 0), 0.00001f)
        assertEquals(0.2f, dsp.sample(0.4f, 1), 0.00001f)
        dsp.profile = SoundProfile(balance = -1f)
        assertEquals(0.4f, dsp.sample(0.4f, 0), 0.00001f)
        assertEquals(0f, dsp.sample(0.4f, 1), 0.00001f)
    }

    @Test
    fun monoIgnoresStereoBalanceAndClampsOutput() {
        val dsp = AudioDsp()
        dsp.configure(44100, 1)
        dsp.profile = SoundProfile(balance = 1f)
        assertEquals(0.4f, dsp.sample(0.4f, 0), 0.00001f)
        assertEquals(1f, dsp.sample(10f, 0), 0.00001f)
    }

    @Test
    fun fiveBandFilterIsStableAndChangesFrequencyResponse() {
        fun rms(freq: Double, profile: SoundProfile): Double {
            val dsp = AudioDsp()
            dsp.configure(44100, 1)
            dsp.profile = profile
            var sum = 0.0
            for (i in 0 until 44100) {
                val y = dsp.sample((.1 * sin(2 * PI * freq * i / 44100)).toFloat(), 0)
                assertTrue(y.isFinite())
                if (i > 22050) sum += y * y
            }
            return sqrt(sum / 22049)
        }
        val p = SoundProfile(true, listOf(6f, 0f, 0f, 0f, 0f))
        val low = rms(60.0, p)
        val high = rms(8000.0, p)
        assertTrue(low > high * 1.6)
        assertTrue(low < .1)
    }
}
