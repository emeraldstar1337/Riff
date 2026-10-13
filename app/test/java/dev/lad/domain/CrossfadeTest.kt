package dev.lad.domain

import org.junit.Assert.*
import org.junit.Test

class CrossfadeTest {
    @Test
    fun timingRespectsPlaybackSpeed() {
        assertEquals(0.5f, Crossfade.fraction(2000, 2f, 2000), 0.001f)
        assertEquals(0f, Crossfade.fraction(3000, 1f, 2000), 0f)
        assertEquals(1f, Crossfade.fraction(0, 1f, 2000), 0f)
    }

    @Test
    fun overlapNeverDoubleCountsListeningTime() {
        for (i in 0..100) {
            val (a, b) = Crossfade.split(1000, i / 100f)
            assertEquals(1000, a + b)
            assertTrue(a >= 0 && b >= 0)
        }
    }
}
