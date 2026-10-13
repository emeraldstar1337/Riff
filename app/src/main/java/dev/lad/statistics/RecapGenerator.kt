package dev.lad.statistics

import dev.lad.R
import dev.lad.data.Listen
import dev.lad.data.Track
import dev.lad.domain.ArtistCredits
import java.time.*

enum class Period(val label: String) {
    MONTH("Month"),
    THREE("3 months"),
    SIX("6 months"),
    YEAR("Year"),
    ALL("All time"),
}

enum class Season(val label: String) {
    WINTER("Winter"),
    SPRING("Spring"),
    SUMMER("Summer"),
    AUTUMN("Autumn"),
}

data class TimeWindow(val start: Long, val end: Long, val label: String)

data class Ranking(
    val name: String,
    val detail: String,
    val listenedMs: Long,
    val plays: Int,
    val artwork: String = "",
    val trackId: Long? = null,
)

data class MusicStats(
    val totalMs: Long = 0,
    val plays: Int = 0,
    val uniqueTracks: Int = 0,
    val uniqueArtists: Int = 0,
    val tracks: List<Ranking> = emptyList(),
    val artists: List<Ranking> = emptyList(),
    val albums: List<Ranking> = emptyList(),
    val genres: List<Ranking> = emptyList(),
    val days: Map<LocalDate, Long> = emptyMap(),
    val hours: List<Long> = List(24) { 0L },
) {
    val favoriteHour
        get() = hours.indices.maxByOrNull { hours[it] } ?: 0

    val bestDay
        get() = days.maxByOrNull { it.value }?.key
}

data class RecapSlide(
    val eyebrow: String,
    val title: String,
    val value: String,
    val caption: String,
    val ranking: List<Ranking> = emptyList(),
)

object RecapGenerator {
    fun period(period: Period, now: ZonedDateTime = ZonedDateTime.now()): TimeWindow {
        val start =
            when (period) {
                Period.MONTH -> now.toLocalDate().withDayOfMonth(1)
                Period.THREE -> now.toLocalDate().minusMonths(3)
                Period.SIX -> now.toLocalDate().minusMonths(6)
                Period.YEAR -> LocalDate.of(now.year, 1, 1)
                Period.ALL -> LocalDate.of(1970, 1, 1)
            }.atStartOfDay(now.zone)
        return TimeWindow(
            start.toInstant().toEpochMilli(),
            now.toInstant().toEpochMilli() + 1,
            period.label,
        )
    }

    fun season(season: Season, year: Int, zone: ZoneId = ZoneId.systemDefault()): TimeWindow {
        val date =
            when (season) {
                Season.WINTER -> LocalDate.of(year - 1, 12, 1)
                Season.SPRING -> LocalDate.of(year, 3, 1)
                Season.SUMMER -> LocalDate.of(year, 6, 1)
                Season.AUTUMN -> LocalDate.of(year, 9, 1)
            }
        return TimeWindow(
            date.atStartOfDay(zone).toInstant().toEpochMilli(),
            date.plusMonths(3).atStartOfDay(zone).toInstant().toEpochMilli(),
            "${season.label} $year",
        )
    }

    fun year(year: Int, zone: ZoneId = ZoneId.systemDefault()) =
        TimeWindow(
            LocalDate.of(year, 1, 1).atStartOfDay(zone).toInstant().toEpochMilli(),
            LocalDate.of(year + 1, 1, 1).atStartOfDay(zone).toInstant().toEpochMilli(),
            year.toString(),
        )

    fun aggregate(
        history: List<Listen>,
        window: TimeWindow,
        zone: ZoneId = ZoneId.systemDefault(),
        mergeArtists: Boolean = true,
    ): MusicStats {
        val rows = history.filter {
            it.startedAt >= window.start && it.startedAt < window.end && it.listenedMs > 0
        }
        val credits = ArtistCredits(history.map { it.artist }, mergeArtists)
        val artistRanks =
            credits
                .groups(rows) { it.artist }
                .map { group ->
                    Ranking(
                        group.name,
                        "Artist",
                        group.items.sumOf { it.listenedMs },
                        group.items.count { it.counted },
                        group.items.first().artwork,
                    )
                }
                .sortedWith(
                    compareByDescending<Ranking> { it.listenedMs }
                        .thenByDescending { it.plays }
                        .thenBy { it.name }
                )
        fun rank(
            key: (Listen) -> String,
            detail: (Listen) -> String,
            track: Boolean = false,
        ): List<Ranking> =
            rows
                .groupBy(key)
                .map { (name, group) ->
                    val first = group.first()
                    Ranking(
                        if (track) first.title else name,
                        detail(first),
                        group.sumOf { it.listenedMs },
                        group.count { it.counted },
                        first.artwork,
                        if (track) first.trackId else null,
                    )
                }
                .sortedWith(
                    compareByDescending<Ranking> { it.listenedMs }
                        .thenByDescending { it.plays }
                        .thenBy { it.name }
                )
        val days =
            rows
                .groupBy { Instant.ofEpochMilli(it.startedAt).atZone(zone).toLocalDate() }
                .mapValues { (_, v) -> v.sumOf { it.listenedMs } }
        val byHour =
            rows
                .groupBy { Instant.ofEpochMilli(it.startedAt).atZone(zone).hour }
                .mapValues { (_, v) -> v.sumOf { it.listenedMs } }
        return MusicStats(
            rows.sumOf { it.listenedMs },
            rows.count { it.counted },
            rows.filter { it.counted }.map { it.trackId }.distinct().size,
            rows
                .filter { it.counted }
                .flatMap { credits.artists(it.artist) }
                .distinctBy { it.key }
                .size,
            rank({ it.trackId.toString() }, { it.artist }, true),
            artistRanks,
            rank(
                { "${it.album} • ${credits.artists(it.artist).first().name}" },
                { credits.artists(it.artist).first().name },
            ),
            rank({ it.genre.ifBlank { "Not specified" } }, { "Genre" }),
            days,
            List(24) { byHour[it] ?: 0 },
        )
    }

    fun demo(tracks: List<Track> = emptyList()): MusicStats {
        val titles =
            listOf(
                "Light Within",
                "On the Same Wavelength",
                "Warm Breeze",
                "After the Rain",
                "Before Dawn",
            )
        val artists = listOf("Moon", "Sea", "North", "Quiet City", "Orbit")
        val ranks =
            (0..4).map { i ->
                val t = tracks.getOrNull(i)
                Ranking(
                    t?.title ?: titles[i],
                    t?.artist ?: artists[i],
                    (250 - i * 32) * 60_000L,
                    80 - i * 12,
                    t?.artwork ?: "",
                    t?.id,
                )
            }
        return MusicStats(
            8642 * 60_000L,
            2184,
            312,
            64,
            ranks,
            ranks
                .mapIndexed { i, r ->
                    r.copy(
                        name = tracks.getOrNull(i)?.artist ?: artists[i],
                        detail = "Artist",
                        listenedMs = r.listenedMs * 3,
                    )
                }
                .groupBy { it.name }
                .values
                .map { rows ->
                    rows
                        .first()
                        .copy(
                            listenedMs = rows.sumOf { it.listenedMs },
                            plays = rows.sumOf { it.plays },
                        )
                }
                .sortedByDescending { it.listenedMs },
            ranks
                .mapIndexed { i, r ->
                    r.copy(name = tracks.getOrNull(i)?.album ?: "Chapter ${i+1}", detail = r.detail)
                }
                .groupBy { it.name to it.detail }
                .values
                .map { rows ->
                    rows
                        .first()
                        .copy(
                            listenedMs = rows.sumOf { it.listenedMs },
                            plays = rows.sumOf { it.plays },
                        )
                }
                .sortedByDescending { it.listenedMs },
            listOf(Ranking("Indie", "Genre", 4_000 * 60_000L, 900)),
            mapOf(LocalDate.now().minusDays(7) to 240 * 60_000L),
            List(24) { if (it in 18..23) (it - 16) * 60_000L else 20_000L },
        )
    }

    fun slides(
        stats: MusicStats,
        label: String,
        year: Boolean,
        strings: dev.lad.i18n.TextProvider,
    ): List<RecapSlide> {
        val minutes = stats.totalMs / 60_000
        val top = stats.tracks.firstOrNull()
        val artist = stats.artists.firstOrNull()
        val album = stats.albums.firstOrNull()
        val slides =
            mutableListOf(
                RecapSlide(
                    strings[R.string.lad_recap],
                    strings[R.string.your_music],
                    label,
                    strings.format(
                        R.string.the_soundtrack_to_your_life,
                        if (year) strings[R.string.all_year] else strings[R.string.this_season],
                    ),
                ),
                RecapSlide(
                    strings[R.string.every_minute],
                    strings[R.string.time_to_listen],
                    "%,d".format(minutes),
                    strings.format(R.string.minutes_of_music_hours, minutes / 60),
                ),
                RecapSlide(
                    strings[R.string.your_rhythm],
                    strings[R.string.on_repeat],
                    stats.plays.toString(),
                    strings.format(R.string.plays_different_tracks, stats.uniqueTracks),
                ),
                RecapSlide(
                    strings[R.string.recap_generator_on_repeat],
                    strings[R.string.top_track],
                    top?.name ?: strings[R.string.more_to_come],
                    top?.detail ?: strings[R.string.listen_to_music_to_see_it],
                    listOfNotNull(top),
                ),
                RecapSlide(
                    strings[R.string.your_top_five],
                    strings[R.string.tracks_that_stayed_with_you],
                    "TOP 5",
                    strings[R.string.ranked_by_listening_time],
                    stats.tracks.take(5),
                ),
                RecapSlide(
                    strings[R.string.your_voice],
                    strings[R.string.top_artist],
                    artist?.name ?: strings[R.string.new_discoveries],
                    strings.format(R.string.artists_in_your_history, stats.uniqueArtists),
                    listOfNotNull(artist),
                ),
                RecapSlide(
                    strings[R.string.close_to_your_heart],
                    strings[R.string.favourite_artists],
                    "TOP 5",
                    strings[R.string.the_artists_you_keep_coming_back],
                    stats.artists.take(5),
                ),
                RecapSlide(
                    strings[R.string.the_whole_story],
                    strings[R.string.top_album],
                    album?.name ?: strings[R.string.the_first_chapter],
                    strings.format(
                        R.string.minutes_with_your_top_album_your,
                        album?.let { it.listenedMs / 60_000 } ?: 0,
                    ),
                    stats.albums.take(5),
                ),
                RecapSlide(
                    strings[R.string.your_time],
                    strings[R.string.a_day_of_music],
                    stats.bestDay?.toString() ?: strings[R.string.still_to_come],
                    strings.format(R.string.you_listen_most_often_around, stats.favoriteHour),
                ),
                RecapSlide(
                    strings[R.string.keep_the_feeling],
                    label,
                    strings.format(R.string.hours, minutes / 60),
                    strings.format(
                        R.string.plays_tracks_artists,
                        stats.plays,
                        stats.uniqueTracks,
                        stats.uniqueArtists,
                    ),
                    stats.tracks.take(3),
                ),
            )
        return slides
    }
}
