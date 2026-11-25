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

enum class RecapThemeShape {
    FLOWER,
    CLOVER,
    COOKIE,
    SUNNY,
    CIRCLE,
}

data class TimeWindow(val start: Long, val end: Long, val label: String)

data class CompletedSeason(
    val season: Season,
    val year: Int,
    val window: TimeWindow,
    val key: String = "${season.name}:$year",
)

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
    val teaser: String = "",
    val shape: RecapThemeShape = RecapThemeShape.FLOWER,
    val countTarget: Long? = null,
    val hours: List<Long> = emptyList(),
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

    /**
     * Returns the most recently completed calendar season relative to [today].
     * E.g. starting Sept 1 -> Summer of [today.year] just ended; starting Dec 1 -> Autumn ended;
     * starting Mar 1 -> Winter ended; starting Jun 1 -> Spring ended.
     */
    fun completedSeason(
        today: LocalDate = LocalDate.now(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): CompletedSeason {
        val (completedSeason, seasonYear) =
            when (today.monthValue) {
                in 3..5 -> Season.WINTER to today.year
                in 6..8 -> Season.SPRING to today.year
                in 9..11 -> Season.SUMMER to today.year
                12 -> Season.AUTUMN to today.year
                else -> Season.AUTUMN to (today.year - 1)
            }
        return CompletedSeason(
            season = completedSeason,
            year = seasonYear,
            window = season(completedSeason, seasonYear, zone),
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
        val rows =
            history.filter {
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
        val credits =
            if (tracks.isNotEmpty()) ArtistCredits(tracks.map { it.artist }, true) else null
        return MusicStats(
            8642 * 60_000L,
            2184,
            312,
            64,
            ranks,
            ranks
                .mapIndexed { i, r ->
                    val rawArtist = tracks.getOrNull(i)?.artist
                    val artistName =
                        rawArtist?.let { credits?.artists(it)?.firstOrNull()?.name ?: it }
                            ?: artists[i]
                    r.copy(
                        name = artistName,
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
        return listOf(
            RecapSlide(
                eyebrow = strings[R.string.lad_recap],
                title = strings[R.string.your_music],
                value = label,
                caption =
                    strings.format(
                        R.string.the_soundtrack_to_your_life,
                        if (year) strings[R.string.all_year] else strings[R.string.this_season],
                    ),
                shape = RecapThemeShape.FLOWER,
            ),
            RecapSlide(
                eyebrow = strings[R.string.every_minute],
                title = strings[R.string.time_to_listen],
                value = "%,d".format(minutes),
                caption =
                    strings.format(
                        R.string.minutes_of_music_hours,
                        strings.plural(R.plurals.music_minutes_label, minutes),
                        strings.plural(R.plurals.hours, minutes / 60, minutes / 60),
                    ),
                teaser = strings[R.string.recap_teaser_minutes],
                shape = RecapThemeShape.SUNNY,
                countTarget = minutes,
            ),
            RecapSlide(
                eyebrow = strings[R.string.your_rhythm],
                title = strings[R.string.on_repeat],
                value = stats.plays.toString(),
                caption =
                    strings.format(
                        R.string.plays_different_tracks,
                        strings.plural(R.plurals.plays, stats.plays),
                        strings.plural(
                            R.plurals.different_track_count,
                            stats.uniqueTracks,
                            stats.uniqueTracks,
                        ),
                    ),
                shape = RecapThemeShape.COOKIE,
                countTarget = stats.plays.toLong(),
            ),
            RecapSlide(
                eyebrow = strings[R.string.recap_generator_on_repeat],
                title = strings[R.string.top_track],
                value = top?.name ?: strings[R.string.more_to_come],
                caption = top?.detail ?: strings[R.string.listen_to_music_to_see_it],
                ranking = listOfNotNull(top),
                teaser = strings[R.string.recap_teaser_track],
                shape = RecapThemeShape.FLOWER,
            ),
            RecapSlide(
                eyebrow = strings[R.string.your_top_five],
                title = strings[R.string.tracks_that_stayed_with_you],
                value = "TOP 5",
                caption = strings[R.string.ranked_by_listening_time],
                ranking = stats.tracks.take(5),
                shape = RecapThemeShape.COOKIE,
            ),
            RecapSlide(
                eyebrow = strings[R.string.your_voice],
                title = strings[R.string.top_artist],
                value = artist?.name ?: strings[R.string.new_discoveries],
                caption =
                    strings.plural(
                        R.plurals.artists_in_your_history,
                        stats.uniqueArtists,
                        stats.uniqueArtists,
                    ),
                ranking = listOfNotNull(artist),
                teaser = strings[R.string.recap_teaser_artist],
                shape = RecapThemeShape.CLOVER,
            ),
            RecapSlide(
                eyebrow = strings[R.string.close_to_your_heart],
                title = strings[R.string.favourite_artists],
                value = "TOP 5",
                caption = strings[R.string.the_artists_you_keep_coming_back],
                ranking = stats.artists.take(5),
                shape = RecapThemeShape.CLOVER,
            ),
            RecapSlide(
                eyebrow = strings[R.string.the_whole_story],
                title = strings[R.string.top_album],
                value = album?.name ?: strings[R.string.the_first_chapter],
                caption =
                    (album?.let { it.listenedMs / 60_000 } ?: 0L).let { albumMinutes ->
                        strings.plural(
                            R.plurals.minutes_with_your_top_album_your,
                            albumMinutes,
                            albumMinutes,
                        )
                    },
                ranking = stats.albums.take(5),
                teaser = strings[R.string.recap_teaser_album],
                shape = RecapThemeShape.SUNNY,
            ),
            RecapSlide(
                eyebrow = strings[R.string.your_time],
                title = strings[R.string.a_day_of_music],
                value = stats.bestDay?.toString() ?: strings[R.string.still_to_come],
                caption = strings.format(R.string.you_listen_most_often_around, stats.favoriteHour),
                teaser = strings[R.string.recap_teaser_time],
                shape = RecapThemeShape.CIRCLE,
                hours = stats.hours,
            ),
            RecapSlide(
                eyebrow = strings[R.string.keep_the_feeling],
                title = label,
                value = strings.plural(R.plurals.hours, minutes / 60, minutes / 60),
                caption =
                    strings.format(
                        R.string.plays_tracks_artists,
                        strings.plural(R.plurals.play_count, stats.plays, stats.plays),
                        strings.plural(R.plurals.tracks, stats.uniqueTracks, stats.uniqueTracks),
                        strings.plural(
                            R.plurals.artist_count,
                            stats.uniqueArtists,
                            stats.uniqueArtists,
                        ),
                    ),
                ranking = stats.tracks.take(3),
                shape = RecapThemeShape.FLOWER,
            ),
        )
    }
}
