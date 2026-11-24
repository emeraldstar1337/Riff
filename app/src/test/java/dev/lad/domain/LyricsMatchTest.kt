package dev.lad.domain

import org.junit.Assert.*
import org.junit.Test

class LyricsMatchTest {
    private fun candidate(
        title: String = "Песня",
        artist: String = "Артист",
        seconds: Int = 180,
        text: String = "[00:05.00]Первая строка",
        synced: Boolean = true,
    ) = OnlineLyrics(title, artist, "Альбом", seconds, text, synced)

    @Test
    fun rejectsPlainAndInvalidTimestamps() {
        assertEquals(
            1,
            LyricsMatch.rank(
                    listOf(
                        candidate(),
                        candidate(synced = false),
                        candidate(text = "Без таймкодов"),
                        candidate(text = "[00:90]Неверно"),
                    ),
                    "Песня",
                    "Артист",
                    180000,
                )
                .size,
        )
    }

    @Test
    fun prefersCorrectRecordingAndDeduplicatesLyrics() {
        val live = candidate(seconds = 300, text = "[00:12]Live")
        val wrong = candidate(artist = "Другой", text = "[00:01]Другая песня")
        val exact = candidate()
        assertEquals(
            listOf(exact, live, wrong),
            LyricsMatch.rank(
                listOf(wrong, live, exact, exact.copy(album = "Сборник")),
                "Песня",
                "Артист",
                180000,
            ),
        )
    }

    @Test
    fun automaticMatchNeedsMetadataAndTwoSecondTolerance() {
        assertTrue(LyricsMatch.exact(candidate("ПЕСНЯ!", "Артист"), "Песня", "артист", 182000))
        assertFalse(LyricsMatch.exact(candidate(), "Песня", "Артист", 182001))
        assertFalse(LyricsMatch.exact(candidate(artist = "Гость"), "Песня", "Артист", 180000))
        assertFalse(LyricsMatch.exact(candidate(seconds = 0), "Песня", "Артист", 180000))
    }
}
