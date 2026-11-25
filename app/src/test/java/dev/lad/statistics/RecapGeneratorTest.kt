package dev.lad.statistics

import java.time.*
import org.junit.Assert.*
import org.junit.Test

class RecapGeneratorTest {
    @Test
    fun briefOpensDoNotInflateUniqueTracksOrPlayCounts() {
        val rows =
            listOf(
                dev.lad.data.Listen("a", 1, "A", "Artist A", "Album", "", "", 1000, 500, false),
                dev.lad.data.Listen("b", 2, "B", "Artist B", "Album", "", "", 2000, 31000, true),
                dev.lad.data.Listen(
                    "b:later",
                    2,
                    "B",
                    "Artist B",
                    "Album",
                    "",
                    "",
                    3000,
                    2000,
                    false,
                ),
            )
        val stats = RecapGenerator.aggregate(rows, TimeWindow(0, 10000, "Test"), ZoneOffset.UTC)
        assertEquals(1, stats.plays)
        assertEquals(1, stats.uniqueTracks)
        assertEquals(1, stats.uniqueArtists)
        assertEquals(33500L, stats.totalMs)
    }

    @Test
    fun demoWorksWithoutTracksOrRealHistory() {
        val stats = RecapGenerator.demo()
        val slides =
            RecapGenerator.slides(
                stats,
                "2026",
                true,
                dev.lad.i18n.TextProvider { id, _ -> "message:$id" },
            )
        assertEquals(10, slides.size)
        assertEquals(5, stats.tracks.size)
        assertTrue(slides.all { it.title.isNotBlank() && it.value.isNotBlank() })
    }

    @Test
    fun winterSpansYearBoundary() {
        val w = RecapGenerator.season(Season.WINTER, 2026, ZoneOffset.UTC)
        assertEquals(Instant.parse("2025-12-01T00:00:00Z").toEpochMilli(), w.start)
        assertEquals(Instant.parse("2026-03-01T00:00:00Z").toEpochMilli(), w.end)
    }

    @Test
    fun monthStartsAtLocalMidnight() {
        val now = ZonedDateTime.of(2026, 10, 4, 16, 0, 0, 0, ZoneId.of("Europe/Moscow"))
        val w = RecapGenerator.period(Period.MONTH, now)
        assertEquals(Instant.parse("2026-09-30T21:00:00Z").toEpochMilli(), w.start)
    }

    @Test
    fun completedSeasonDetectsEndOfEverySeasonAndYearBoundary() {
        val endOfSummer = RecapGenerator.completedSeason(LocalDate.of(2026, 9, 1), ZoneOffset.UTC)
        assertEquals(Season.SUMMER, endOfSummer.season)
        assertEquals(2026, endOfSummer.year)
        assertEquals("SUMMER:2026", endOfSummer.key)

        val endOfAutumn = RecapGenerator.completedSeason(LocalDate.of(2026, 12, 5), ZoneOffset.UTC)
        assertEquals(Season.AUTUMN, endOfAutumn.season)
        assertEquals(2026, endOfAutumn.year)
        assertEquals("AUTUMN:2026", endOfAutumn.key)

        val januaryStillReferencesCompletedAutumn =
            RecapGenerator.completedSeason(LocalDate.of(2027, 1, 15), ZoneOffset.UTC)
        assertEquals(Season.AUTUMN, januaryStillReferencesCompletedAutumn.season)
        assertEquals(2026, januaryStillReferencesCompletedAutumn.year)
        assertEquals("AUTUMN:2026", januaryStillReferencesCompletedAutumn.key)

        val endOfWinter = RecapGenerator.completedSeason(LocalDate.of(2027, 3, 1), ZoneOffset.UTC)
        assertEquals(Season.WINTER, endOfWinter.season)
        assertEquals(2027, endOfWinter.year)
        assertEquals("WINTER:2027", endOfWinter.key)

        val endOfSpring = RecapGenerator.completedSeason(LocalDate.of(2027, 6, 2), ZoneOffset.UTC)
        assertEquals(Season.SPRING, endOfSpring.season)
        assertEquals(2027, endOfSpring.year)
        assertEquals("SPRING:2027", endOfSpring.key)
    }
}
