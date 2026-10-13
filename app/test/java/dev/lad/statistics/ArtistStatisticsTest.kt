package dev.lad.statistics

import dev.lad.data.Listen
import java.time.ZoneOffset
import org.junit.Assert.*
import org.junit.Test

class ArtistStatisticsTest {
    private val window = TimeWindow(0, 1000, "Test")
    private val names =
        listOf("PHARAOH", "pharaoh feat. Guest", "PHARAOH & Other", "PHARAOH feat. PHARAOH & Guest")
    private val history = names.mapIndexed { index, artist ->
        Listen(
            "session$index",
            index.toLong(),
            "Song $index",
            artist,
            "Album",
            "Genre",
            "",
            100,
            60000,
            true,
        )
    }

    @Test
    fun existingHistoryMergesCreditsAndCountsGlobalTimeOnlyOnce() {
        val stats = RecapGenerator.aggregate(history, window, ZoneOffset.UTC)
        assertEquals(240000L, stats.totalMs)
        assertEquals(4, stats.plays)
        assertEquals(3, stats.uniqueArtists)
        val pharaoh = stats.artists.first { it.name == "PHARAOH" }
        assertEquals(240000L, pharaoh.listenedMs)
        assertEquals(4, pharaoh.plays)
        assertEquals(120000L, stats.artists.first { it.name == "Guest" }.listenedMs)
        assertEquals(names, history.map { it.artist })
        assertEquals(1, stats.albums.size)
    }

    @Test
    fun disablingGroupingKeepsOriginalRankings() {
        val stats = RecapGenerator.aggregate(history, window, ZoneOffset.UTC, mergeArtists = false)
        assertEquals(4, stats.uniqueArtists)
        assertEquals(4, stats.artists.size)
        assertEquals(240000L, stats.totalMs)
    }
}
