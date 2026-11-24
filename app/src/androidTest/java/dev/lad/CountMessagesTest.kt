package dev.lad

import androidx.test.platform.app.InstrumentationRegistry
import dev.lad.i18n.AppLanguage
import dev.lad.i18n.TextResources
import org.junit.Assert.assertEquals
import org.junit.Test

class CountMessagesTest {
    private fun text(language: String) =
        TextResources(
            AppLanguage.context(
                InstrumentationRegistry.getInstrumentation().targetContext,
                language,
            )
        )

    @Test
    fun russianTrackCountsRespectTeenExceptionsAndCompoundNumbers() {
        val strings = text("ru")
        for ((count, expected) in
            listOf(
                0 to "0 треков",
                1 to "1 трек",
                2 to "2 трека",
                5 to "5 треков",
                11 to "11 треков",
                12 to "12 треков",
                21 to "21 трек",
                22 to "22 трека",
                101 to "101 трек",
                111 to "111 треков",
            )) {
            assertEquals(expected, strings.plural(R.plurals.tracks, count, count))
        }
    }

    @Test
    fun englishCountsAndCompoundLabelsUseTheCorrectQuantityArgument() {
        assertEquals("1 track", text("en").plural(R.plurals.tracks, 1, 1))
        assertEquals("21 tracks", text("en").plural(R.plurals.tracks, 21, 21))
        assertEquals(
            "Song · 1 play",
            text("en").plural(R.plurals.statistics_screens_plays, 1, "Song", 1),
        )
        assertEquals(
            "Песня · 21 прослушивание",
            text("ru").plural(R.plurals.statistics_screens_plays, 21, "Песня", 21),
        )
        assertEquals("Через 1 минуту", text("ru").plural(R.plurals.minutes, 1, 1))
        assertEquals(
            "1 файл сейчас недоступен",
            text("ru").plural(R.plurals.unavailable_files, 1, 1),
        )
        assertEquals("час", text("ru").plural(R.plurals.statistics_screens_hours, 21L))
    }
}
