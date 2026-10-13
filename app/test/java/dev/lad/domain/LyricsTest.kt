package dev.lad.domain

import org.junit.Assert.*
import org.junit.Test

class LyricsTest {
    @Test
    fun multipleStampsOffsetsAndSortedLines() {
        val rows =
            LrcParser.parse(
                "[ar:Artist]\n[offset:-100]\n[00:12.50][00:20.005]Снова\n[00:01.2]Начало\nОбычный текст"
            )
        assertEquals(listOf(1100L, 12400L, 19905L), rows.map { it.timeMs })
        assertEquals(listOf("Начало", "Снова", "Снова"), rows.map { it.text })
        assertEquals(-1, LrcParser.current(rows, 0))
        assertEquals(1, LrcParser.current(rows, 12400))
        assertEquals(2, LrcParser.current(rows, Long.MAX_VALUE))
    }

    @Test
    fun emptyPlainAndLeadingZeroTimestamp() {
        assertTrue(LrcParser.parse("Просто текст песни").isEmpty())
        assertEquals(0L, LrcParser.parse("[offset:-1000]\n[00:00.05]Start").single().timeMs)
        assertEquals(-1, LrcParser.current(emptyList(), 50))
    }
}
