package dev.lad.data

import org.junit.Assert.*
import org.junit.Test

class AppearanceAndArtworkTest {
    @Test
    fun coverKeysSeparateFilesAndRefreshVersions() {
        val a = trackArtworkUri(1, "content://media/1", 123, 456, 0)
        assertNotEquals(a, trackArtworkUri(2, "content://media/2", 123, 456, 0))
        assertNotEquals(a, trackArtworkUri(1, "content://media/1", 124, 456, 0))
        assertNotEquals(a, trackArtworkUri(1, "content://media/1", 123, 456, 999))
        assertEquals(a, trackArtworkUri(1, "content://media/1", 123, 456, 0))
    }

    @Test
    fun presetsDoNotOverwritePlaybackOrOtherAppearance() {
        val s = Settings(crossfadeSeconds = 8, includedFolders = "Music", lyricsSize = 33f)
        AppearancePresets.glow.forEach { p ->
            val next = p.apply(s)
            assertEquals(8, next.crossfadeSeconds)
            assertEquals("Music", next.includedFolders)
            assertEquals(33f, next.lyricsSize)
            assertEquals(p.strength, next.glowStrength)
        }
        AppearancePresets.lyrics.forEach { p ->
            val next = p.apply(s)
            assertEquals(s.glowStrength, next.glowStrength)
            assertEquals(8, next.crossfadeSeconds)
            assertEquals(p.alignment, next.lyricsAlignment)
        }
    }
}
