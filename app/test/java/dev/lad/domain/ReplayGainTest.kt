package dev.lad.domain

import org.junit.Assert.*
import org.junit.Test

class ReplayGainTest {
    @Test
    fun parsesSignedDecibelsAndCapsPeak() {
        assertEquals(0.501f, ReplayGain.factor("-6.00 dB"), 0.001f)
        assertEquals(2f, ReplayGain.factor("6.02 dB"), 0.01f)
        assertEquals(1.25f, ReplayGain.factor("6 dB", "0.8"), 0.001f)
    }

    @Test
    fun missingOrInvalidTagsAreNeutral() {
        assertEquals(1f, ReplayGain.factor(null), 0f)
        assertEquals(1f, ReplayGain.factor("NaN dB"), 0f)
        assertEquals(1f, ReplayGain.factor("oops"), 0f)
    }
}
