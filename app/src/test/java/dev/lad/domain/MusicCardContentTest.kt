package dev.lad.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class MusicCardContentTest {
    @Test
    fun selectsCurrentLineAndRespectsTimingCorrection() {
        val text = "[00:01]First\n[00:04]Second\n[00:08]Third"
        assertEquals("", MusicCardContent.currentLine(text, 500))
        assertEquals("First", MusicCardContent.currentLine(text, 3999))
        assertEquals("Second", MusicCardContent.currentLine(text, 4000))
        assertEquals("Third", MusicCardContent.currentLine(text, 7500, 500))
        assertEquals("First", MusicCardContent.currentLine(text, 4000, -500))
    }

    @Test
    fun omitsTextWhenNoCurrentSynchronizedLineExists() {
        assertEquals("", MusicCardContent.currentLine("", 10000))
        assertEquals("", MusicCardContent.currentLine("Plain lyrics without timing", 10000))
        assertEquals("", MusicCardContent.currentLine("[00:01]Words\n[00:05]", 6000))
    }
}
