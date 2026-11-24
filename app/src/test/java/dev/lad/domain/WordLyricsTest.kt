package dev.lad.domain

import org.junit.Assert.*
import org.junit.Test

class WordLyricsTest {
    @Test
    fun enhancedLrcStripsTagsAndPreservesWordRanges() {
        val line = LrcParser.parse("[00:10.00]<00:10.00>Один <00:10.50>два <00:11.00>три").single()
        assertEquals("Один два три", line.text)
        assertEquals(listOf(10000L, 10500L, 11000L), line.words.map { it.timeMs })
        assertEquals(
            listOf("Один", "два", "три"),
            line.words.map { line.text.substring(it.start, it.end) },
        )
        assertEquals(-1, LrcParser.currentWord(line, 9999))
        assertEquals(0, LrcParser.currentWord(line, 10000))
        assertEquals(1, LrcParser.currentWord(line, 10500))
        assertEquals(2, LrcParser.currentWord(line, 11200))
    }

    @Test
    fun offsetAppliesEquallyToLinesAndWords() {
        val line =
            LrcParser.parse("[offset:-200]\n[00:02.00]<00:02.00>Свет <00:02.30>рядом").single()
        assertEquals(1800L, line.timeMs)
        assertEquals(listOf(1800L, 2100L), line.words.map { it.timeMs })
    }

    @Test
    fun repeatedLineTimestampsShiftWordTiming() {
        val lines = LrcParser.parse("[00:10.00][00:20.00]<00:10.00>Раз <00:10.50>два")
        assertEquals(listOf(10000L, 20000L), lines.map { it.timeMs })
        assertEquals(listOf(20000L, 20500L), lines[1].words.map { it.timeMs })
    }

    @Test
    fun lineOnlyTextDoesNotInventWordTiming() {
        val line = LrcParser.parse("[00:01.00]Три обычных слова").single()
        assertTrue(line.words.isEmpty())
        assertEquals(-1, LrcParser.currentWord(line, 2000))
    }

    @Test
    fun invalidOrDecreasingWordTimingFallsBackToLine() {
        for (text in
            listOf(
                "[00:01.00]<00:61.00>Раз <00:03.00>два",
                "[00:01.00]<00:03.00>Раз <00:02.00>два",
            )) {
            val line = LrcParser.parse(text).single()
            assertEquals("Раз два", line.text)
            assertTrue(line.words.isEmpty())
        }
    }

    @Test
    fun whitespaceAndEndMarkerDoNotHighlightAnEmptyWord() {
        val line =
            LrcParser.parse("[00:01.00]  <00:01.00>Первое  <00:02.00>слово <00:03.00>  ").single()
        assertEquals("Первое  слово", line.text)
        assertEquals(2, line.words.size)
        assertEquals("слово", line.text.substring(line.words[1].start, line.words[1].end))
        assertEquals(1, LrcParser.currentWord(line, 2999))
        assertEquals(-1, LrcParser.currentWord(line, 3000))
    }

    @Test
    fun unicodeRangesUseTheSameOffsetsAsCompose() {
        val line = LrcParser.parse("[00:01.00]<00:01.00>🎵 <00:02.00>музыка").single()
        assertEquals("музыка", line.text.substring(line.words[1].start, line.words[1].end))
    }
}
