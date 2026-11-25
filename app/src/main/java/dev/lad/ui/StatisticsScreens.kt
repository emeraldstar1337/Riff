@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package dev.lad.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.lad.LadApplication
import dev.lad.R
import dev.lad.data.*
import dev.lad.domain.ArtistCredits
import dev.lad.i18n.rememberText
import dev.lad.statistics.*
import dev.lad.statistics.Period
import java.time.*
import kotlin.math.roundToLong
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun RankingRow(rank: Ranking, index: Int, artistTracks: List<Track> = emptyList()) {
    val strings = rememberText()
    val isArtist = rank.detail == "Artist"
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            "${index + 1}",
            style = MaterialTheme.typography.titleLarge.copy(fontFeatureSettings = "tnum"),
            fontWeight = FontWeight.Black,
            modifier = Modifier.width(24.dp),
        )
        if (isArtist) {
            ArtistPortrait(
                name = rank.name,
                tracks = artistTracks,
                modifier = Modifier.size(48.dp),
                round = 12,
                fallbackArtwork = rank.artwork.ifBlank { null },
            )
        } else {
            Artwork(rank.artwork, rank.name, Modifier.size(48.dp), 12)
        }
        Column(Modifier.weight(1f)) {
            Text(
                rank.name,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                strings.label(rank.detail),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Text(
            strings.format(R.string.statistics_screens_min, rank.listenedMs / 60_000),
            style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
        )
    }
}

private fun tracksForArtist(
    name: String,
    tracks: List<Track>,
    mergeArtists: Boolean,
): List<Track> {
    if (tracks.isEmpty()) return emptyList()
    val grouped =
        ArtistCredits(tracks.map { it.artist }, mergeArtists)
            .groups(tracks) { it.artist }
            .firstOrNull { it.name.equals(name, ignoreCase = true) }
            ?.items
    return grouped ?: tracks.filter { it.artist.contains(name, ignoreCase = true) }
}

private data class PersonalRecords(
    val bestDay: Pair<LocalDate, Long>? = null,
    val mostPlayed: Pair<String, Int>? = null,
    val longestRunMs: Long? = null,
)

@Composable
internal fun SeasonRecapPromptCard(
    history: List<Listen>,
    settings: Settings,
    onOpen: (MusicStats, String, String) -> Unit,
    onDismiss: (String) -> Unit,
) {
    val strings = rememberText()
    val prompt by
        produceState<Pair<CompletedSeason, MusicStats>?>(
            null,
            history,
            settings.statistics,
            settings.seenSeasonRecap,
            settings.mergeArtists,
        ) {
            value =
                if (!settings.statistics || history.isEmpty()) null
                else
                    withContext(Dispatchers.Default) {
                        val completed = RecapGenerator.completedSeason()
                        if (settings.seenSeasonRecap == completed.key) null
                        else {
                            val stats =
                                RecapGenerator.aggregate(
                                    history,
                                    completed.window,
                                    mergeArtists = settings.mergeArtists,
                                )
                            if (stats.plays > 0) completed to stats else null
                        }
                    }
        }
    val ready = prompt ?: return
    val (completed, stats) = ready
    val seasonName = strings.label(completed.season.label)
    val seasonLabel = "$seasonName ${completed.year}"
    val seasonIcon =
        when (completed.season) {
            Season.WINTER -> Icons.Default.AcUnit
            Season.SPRING -> Icons.Default.LocalFlorist
            Season.SUMMER -> Icons.Default.WbSunny
            Season.AUTUMN -> Icons.Default.Spa
        }
    BitsReveal(key = completed.key) {
        Surface(
            onClick = { onOpen(stats, seasonLabel, completed.key) },
            shape = LadShapes.hero,
            color = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        ) {
            Row(
                Modifier.fillMaxWidth()
                    .padding(start = 20.dp, top = 18.dp, bottom = 18.dp, end = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Surface(
                    shape = FlowerShape,
                    color = MaterialTheme.colorScheme.tertiary,
                    contentColor = MaterialTheme.colorScheme.onTertiary,
                    modifier = Modifier.size(52.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(seasonIcon, null, Modifier.size(24.dp))
                    }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        strings[R.string.season_recap_ready_eyebrow],
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        strings.format(R.string.season_recap_ready_title, seasonName, completed.year),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        strings[R.string.season_recap_ready_subtitle],
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.82f),
                    )
                }
                IconButton(onClick = { onDismiss(completed.key) }) {
                    Icon(Icons.Default.Close, strings[R.string.close])
                }
            }
        }
    }
}

@Composable
fun StatisticsScreen(
    history: List<Listen>,
    tracks: List<Track>,
    onRecap: (MusicStats, String, Boolean) -> Unit,
    runs: List<PlaybackRun> = emptyList(),
    onMarkSeasonSeen: (String) -> Unit = {},
) {
    val strings = rememberText()
    val scope = rememberCoroutineScope()
    val settings = LocalLadSettings.current
    val mergeArtists = settings.mergeArtists
    val dateFormat = settings.dateFormat
    var period by rememberSaveable { mutableIntStateOf(0) }
    var year by rememberSaveable { mutableIntStateOf(LocalDate.now().year) }
    val stats by
        produceState(MusicStats(), history, period, mergeArtists) {
            value =
                withContext(Dispatchers.Default) {
                    RecapGenerator.aggregate(
                        history,
                        RecapGenerator.period(Period.entries[period]),
                        mergeArtists = mergeArtists,
                    )
                }
        }
    val records by
        produceState(PersonalRecords(), history, runs) {
            value =
                withContext(Dispatchers.Default) {
                    val mostPlayed =
                        history
                            .filter { it.counted }
                            .groupBy { it.trackId }
                            .values
                            .maxByOrNull { it.size }
                            ?.let { it.first().title to it.size }
                    val zone = ZoneId.systemDefault()
                    val bestDay =
                        history
                            .groupBy { Instant.ofEpochMilli(it.startedAt).atZone(zone).toLocalDate() }
                            .mapValues { (_, rows) -> rows.sumOf { it.listenedMs } }
                            .maxByOrNull { it.value }
                            ?.let { it.key to it.value }
                    val longestRunMs = runs.maxByOrNull { it.listenedMs }?.listenedMs
                    PersonalRecords(bestDay, mostPlayed, longestRunMs)
                }
        }
    LazyColumn(
        contentPadding = PaddingValues(LadSpacing.page),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item("season-recap-banner") {
            SeasonRecapPromptCard(
                history = history,
                settings = settings,
                onOpen = { seasonStats, seasonLabel, key ->
                    onMarkSeasonSeen(key)
                    onRecap(seasonStats, seasonLabel, false)
                },
                onDismiss = onMarkSeasonSeen,
            )
        }
        item {
            ChoiceChips(
                period.toString(),
                Period.entries.map { it.ordinal.toString() to strings.label(it.label) },
            ) {
                period = it.toInt()
            }
        }
        item {
            Text(
                strings[R.string.your_listening_time],
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Metric(
                    "${stats.totalMs / 3_600_000}",
                    strings.plural(R.plurals.statistics_screens_hours, stats.totalMs / 3_600_000),
                    Modifier.weight(1f),
                )
                Metric(
                    "${stats.totalMs / 60_000}",
                    strings.plural(R.plurals.statistics_screens_minutes, stats.totalMs / 60_000),
                    Modifier.weight(1f),
                )
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Metric(
                    "${stats.plays}",
                    strings.plural(R.plurals.plays, stats.plays),
                    Modifier.weight(1f),
                )
                Metric(
                    "${stats.uniqueTracks}",
                    strings.plural(R.plurals.different_tracks, stats.uniqueTracks),
                    Modifier.weight(1f),
                )
            }
        }
        if (stats.totalMs == 0L)
            item {
                Text(
                    strings[R.string.it_starts_with_the_first_track],
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        item {
            SectionTitle(
                strings[R.string.personal_records],
                strings[R.string.statistics_screens_all_time],
            )
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                InfoTile(
                    strings[R.string.your_most_musical_day],
                    records.bestDay?.let { (day, listenedMs) ->
                        strings.format(
                            R.string.statistics_screens_min_478,
                            AppDates.format(day, dateFormat),
                            listenedMs / 60000,
                        )
                    } ?: strings[R.string.more_to_come],
                )
                InfoTile(
                    strings[R.string.longest_session],
                    records.longestRunMs?.let {
                        strings.format(R.string.of_music, duration(it))
                    } ?: strings[R.string.start_listening_to_set_a_record],
                )
                InfoTile(
                    strings[R.string.most_repeated],
                    records.mostPlayed?.let { (title, count) ->
                        strings.plural(R.plurals.statistics_screens_plays, count, title, count)
                    } ?: strings[R.string.more_to_come],
                )
            }
        }
        item { SectionTitle(strings[R.string.your_music_recaps]) }
        item {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                IconButton(onClick = { if (year > 1970) year-- }, enabled = year > 1970) {
                    Icon(Icons.Default.ChevronLeft, strings[R.string.previous_year])
                }
                Text(
                    year.toString(),
                    style = MaterialTheme.typography.titleLarge.copy(fontFeatureSettings = "tnum"),
                )
                IconButton(
                    onClick = { if (year < LocalDate.now().year) year++ },
                    enabled = year < LocalDate.now().year,
                ) {
                    Icon(Icons.Default.ChevronRight, strings[R.string.next_year])
                }
            }
        }
        item {
            Card(
                onClick = {
                    val selectedYear = year
                    scope.launch {
                        val aggregated =
                            withContext(Dispatchers.Default) {
                                RecapGenerator.aggregate(
                                    history,
                                    RecapGenerator.year(selectedYear),
                                    mergeArtists = mergeArtists,
                                )
                            }
                        onRecap(aggregated, strings.format(R.string.your, selectedYear), true)
                    }
                },
                shape = LadShapes.hero,
                colors =
                    CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    ),
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Surface(
                        shape = CloverShape,
                        color = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(48.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.AutoAwesome, null, Modifier.size(24.dp))
                        }
                    }
                    Column(Modifier.weight(1f)) {
                        Text(
                            strings[R.string.statistics_screens_your_year_in_music],
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(strings.format(R.string.text_10_stories_about_you, year))
                    }
                    Icon(Icons.Default.ChevronRight, null)
                }
            }
        }
        if (stats.tracks.isNotEmpty()) {
            item { SectionTitle(strings[R.string.favourite_tracks]) }
            itemsIndexed(stats.tracks.take(5), key = { i, r -> "t:${r.trackId ?: r.name}:$i" }) {
                i,
                r ->
                RankingRow(r, i)
            }
            item { SectionTitle(strings[R.string.statistics_screens_artists]) }
            itemsIndexed(stats.artists.take(5), key = { i, r -> "a:${r.name}:$i" }) { i, r ->
                val artistTrackList =
                    remember(r.name, tracks, mergeArtists) {
                        tracksForArtist(r.name, tracks, mergeArtists)
                    }
                RankingRow(r, i, artistTrackList)
            }
            item {
                SectionTitle(strings[R.string.most_played_album], stats.albums.firstOrNull()?.name)
            }
            itemsIndexed(stats.albums.take(5), key = { i, r -> "album:${r.name}:$i" }) { i, r ->
                RankingRow(r, i)
            }
            item {
                SectionTitle(
                    strings[R.string.time_for_music],
                    strings.format(R.string.most_often_around, stats.favoriteHour),
                )
            }
            item {
                val max = stats.hours.maxOrNull()?.coerceAtLeast(1) ?: 1
                Row(
                    Modifier.fillMaxWidth().height(82.dp),
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    stats.hours.forEachIndexed { idx, h ->
                        Box(
                            Modifier.weight(1f)
                                .fillMaxHeight((h.toFloat() / max).coerceAtLeast(0.04f))
                                .clip(RoundedCornerShape(5.dp))
                                .background(
                                    if (idx == stats.favoriteHour)
                                        MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.primaryContainer
                                )
                        )
                    }
                }
            }
            item {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("00:00", style = MaterialTheme.typography.labelSmall)
                    Text("12:00", style = MaterialTheme.typography.labelSmall)
                    Text("23:00", style = MaterialTheme.typography.labelSmall)
                }
            }
            item {
                val bestMinutes = (stats.days[stats.bestDay] ?: 0) / 60_000
                Text(
                    strings.plural(
                        R.plurals.your_most_musical_day_minutes,
                        bestMinutes,
                        stats.bestDay?.let { AppDates.format(it, dateFormat) },
                        bestMinutes,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

private fun recapThemeShape(shape: RecapThemeShape): Shape =
    when (shape) {
        RecapThemeShape.FLOWER -> FlowerShape
        RecapThemeShape.CLOVER -> CloverShape
        RecapThemeShape.COOKIE -> CookieShape
        RecapThemeShape.SUNNY -> SunnyShape
        RecapThemeShape.CIRCLE -> CircleShape
    }

private val ChapterMoodTints =
    listOf(
        Color(0xFF7C5CE6),
        Color(0xFF0EA5E9),
        Color(0xFF10B981),
        Color(0xFFE11D48),
        Color(0xFF8B5CF6),
        Color(0xFFF59E0B),
        Color(0xFF14B8A6),
        Color(0xFFD946EF),
        Color(0xFF3B82F6),
        Color(0xFF6366F1),
    )

private fun recapExpressiveScheme(seed: Color, chapterIndex: Int): ColorScheme {
    val secondaryTint = ChapterMoodTints[(chapterIndex + 2) % ChapterMoodTints.size]
    val tertiaryTint = ChapterMoodTints[(chapterIndex + 5) % ChapterMoodTints.size]
    val surface = lerp(Color(0xFF0C0F14), seed, 0.11f)
    val surfaceLow = lerp(surface, seed, 0.08f)
    val surfaceHigh = lerp(surface, seed, 0.16f)
    val primary = lerp(seed, Color.White, 0.56f)
    val primaryContainer = lerp(surface, seed, 0.34f)
    val secondary = lerp(secondaryTint, Color.White, 0.54f)
    val secondaryContainer = lerp(surface, lerp(seed, secondaryTint, 0.45f), 0.28f)
    val tertiary = lerp(tertiaryTint, Color.White, 0.54f)
    val tertiaryContainer = lerp(surface, lerp(seed, tertiaryTint, 0.45f), 0.28f)
    return darkColorScheme(
        primary = primary,
        onPrimary = Color(0xFF0E1017),
        primaryContainer = primaryContainer,
        onPrimaryContainer = Color(0xFFF7F8FC),
        secondary = secondary,
        onSecondary = Color(0xFF0E1017),
        secondaryContainer = secondaryContainer,
        onSecondaryContainer = Color(0xFFF4F6FB),
        tertiary = tertiary,
        onTertiary = Color(0xFF0E1017),
        tertiaryContainer = tertiaryContainer,
        onTertiaryContainer = Color(0xFFF6F4FB),
        surface = surface,
        background = surface,
        onSurface = Color(0xFFF5F6FA),
        onBackground = Color(0xFFF5F6FA),
        onSurfaceVariant = Color(0xFFC8CDD8),
        surfaceContainerLowest = Color(0xFF090B10),
        surfaceContainerLow = surfaceLow,
        surfaceContainer = lerp(surface, seed, 0.12f),
        surfaceContainerHigh = surfaceHigh,
        surfaceContainerHighest = lerp(surface, seed, 0.22f),
    )
}

@Composable
fun RecapScreen(
    stats: MusicStats,
    label: String,
    year: Boolean,
    onClose: () -> Unit,
    animations: Boolean,
    settings: Settings,
    tracks: List<Track> = emptyList(),
) {
    val strings = rememberText()
    val context = LocalContext.current
    val artistRepo = (context.applicationContext as? LadApplication)?.artistPhotos
    val slides =
        remember(stats, label, year, strings) { RecapGenerator.slides(stats, label, year, strings) }

    // Atomic chapter index (never gets stuck halfway between slides)
    var currentPage by rememberSaveable { mutableIntStateOf(0) }
    var navDirection by remember { mutableIntStateOf(1) }
    val safePage = currentPage.coerceIn(0, slides.lastIndex)
    val currentSlide = slides[safePage]

    val leadRank = currentSlide.ranking.firstOrNull()
    val leadArtistTracks =
        remember(leadRank?.name, leadRank?.detail, tracks, settings.mergeArtists) {
            if (leadRank?.detail == "Artist")
                tracksForArtist(leadRank.name, tracks, settings.mergeArtists)
            else emptyList()
        }
    val leadArtistPhoto by
        produceState<String?>(
            if (leadRank?.detail == "Artist" && settings.artistPhotos)
                artistRepo?.peek(leadRank.name)?.image
            else null,
            leadRank?.name,
            leadRank?.detail,
            leadArtistTracks,
            settings.artistPhotos,
            artistRepo,
        ) {
            value =
                if (leadRank?.detail == "Artist" && settings.artistPhotos && artistRepo != null) {
                    artistRepo.peek(leadRank.name)?.image
                        ?: artistRepo.find(leadRank.name, leadArtistTracks)?.image
                } else null
        }
    val cover = leadArtistPhoto ?: leadRank?.artwork ?: stats.tracks.firstOrNull()?.artwork
    val rawSeed = artworkSeed(cover, settings.recapArtwork)
    val baseAccent = AccentColors[settings.accent] ?: AccentColors.getValue("violet")
    val moodTint = ChapterMoodTints[safePage % ChapterMoodTints.size]
    val targetSeed =
        if (rawSeed != null) lerp(rawSeed, moodTint, 0.24f)
        else lerp(baseAccent, moodTint, 0.52f)
    val animatedSeed by
        animateColorAsState(
            targetSeed,
            LadMotion.defaultEffects(animations),
            label = "recap seed",
        )
    val scheme = remember(animatedSeed, safePage) { recapExpressiveScheme(animatedSeed, safePage) }

    var holding by remember { mutableStateOf(false) }
    var revealed by
        remember(safePage, animations) {
            mutableStateOf(!animations || currentSlide.teaser.isBlank())
        }
    val slideProgress = remember(safePage) { mutableFloatStateOf(if (animations) 0f else 1f) }

    fun stepForward() {
        if (!revealed) {
            revealed = true
        } else if (safePage >= slides.lastIndex) {
            onClose()
        } else {
            navDirection = 1
            currentPage = safePage + 1
        }
    }

    fun stepBackward() {
        if (safePage > 0) {
            navDirection = -1
            currentPage = safePage - 1
        }
    }

    LaunchedEffect(safePage, animations) {
        if (!animations) {
            revealed = true
            slideProgress.floatValue = 1f
            return@LaunchedEffect
        }
        val hasTeaser = currentSlide.teaser.isNotBlank()
        val teaserMs = if (hasTeaser) 1450f else 0f
        val totalMs = if (hasTeaser) 8000f else 6500f
        var elapsedMs = 0f
        var prevNanos = 0L
        while (
            isActive &&
                (currentCoroutineContext()[MotionDurationScale]?.scaleFactor ?: 1f) > 0f
        ) {
            withFrameNanos { now ->
                val dtMs =
                    if (prevNanos == 0L) 16f
                    else ((now - prevNanos) / 1_000_000f).coerceIn(0f, 80f)
                prevNanos = now
                if (!holding) {
                    if (revealed && elapsedMs < teaserMs) {
                        elapsedMs = teaserMs
                    }
                    elapsedMs += dtMs
                    if (hasTeaser && !revealed && elapsedMs >= teaserMs) {
                        revealed = true
                    }
                    slideProgress.floatValue = (elapsedMs / totalMs).coerceIn(0f, 1f)
                }
            }
            if (elapsedMs >= totalMs) {
                if (safePage < slides.lastIndex) {
                    navDirection = 1
                    currentPage = safePage + 1
                }
                break
            }
        }
    }

    MaterialTheme(colorScheme = scheme) {
        Surface(Modifier.fillMaxSize(), color = scheme.surface, contentColor = scheme.onSurface) {
            Box(Modifier.fillMaxSize()) {
                // Calm, pure M3 Expressive ambient top radial wash (no floating blobs behind text!)
                Canvas(Modifier.fillMaxSize()) {
                    drawRect(
                        Brush.radialGradient(
                            colors =
                                listOf(
                                    scheme.primary.copy(alpha = 0.16f),
                                    scheme.tertiary.copy(alpha = 0.06f),
                                    Color.Transparent,
                                ),
                            center = Offset(size.width * 0.5f, size.height * 0.14f),
                            radius = size.maxDimension * 0.68f,
                        )
                    )
                }

                Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                    // Top Story Progress Capsules
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        slides.indices.forEach { i ->
                            val trackColor = scheme.onSurface.copy(alpha = 0.16f)
                            val fillColor = scheme.primary
                            Canvas(Modifier.weight(1f).height(4.dp).clip(CircleShape)) {
                                drawRect(trackColor)
                                val fill =
                                    when {
                                        i < safePage -> 1f
                                        i > safePage -> 0f
                                        else -> slideProgress.floatValue
                                    }
                                if (fill > 0f) {
                                    drawRect(
                                        color = fillColor,
                                        size = size.copy(width = size.width * fill),
                                    )
                                }
                            }
                        }
                    }

                    // Clean M3 Expressive Header Bar
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = scheme.surfaceContainerHigh,
                            contentColor = scheme.onSurface,
                        ) {
                            Row(
                                Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Icon(
                                    Icons.Default.AutoAwesome,
                                    null,
                                    Modifier.size(15.dp),
                                    tint = scheme.primary,
                                )
                                Text(
                                    if (label.contains(strings[R.string.demo]))
                                        strings[R.string.demo_sample_data]
                                    else strings.format(R.string.statistics_screens_lad, label),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        Spacer(Modifier.weight(1f))
                        Surface(
                            shape = CircleShape,
                            color = scheme.surfaceContainerLow,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RecapShareButton(stats, currentSlide, label, tracks)
                                IconButton(onClick = onClose) {
                                    Icon(Icons.Default.Close, strings[R.string.close_recap])
                                }
                            }
                        }
                    }

                    // Atomic M3 Expressive Story Stage (Swipe + Tap + Hold-to-Pause)
                    Box(
                        Modifier.weight(1f)
                            .fillMaxWidth()
                            .pointerInput(safePage, revealed) {
                                var dragTotal = 0f
                                val swipeThreshold = 42.dp.toPx()
                                detectHorizontalDragGestures(
                                    onDragStart = {
                                        dragTotal = 0f
                                        holding = true
                                    },
                                    onHorizontalDrag = { change, amount ->
                                        change.consume()
                                        dragTotal += amount
                                    },
                                    onDragCancel = { holding = false },
                                    onDragEnd = {
                                        holding = false
                                        if (dragTotal < -swipeThreshold) stepForward()
                                        else if (dragTotal > swipeThreshold) stepBackward()
                                    },
                                )
                            }
                            .pointerInput(safePage, revealed) {
                                detectTapGestures(
                                    onPress = {
                                        holding = true
                                        try {
                                            tryAwaitRelease()
                                        } finally {
                                            holding = false
                                        }
                                    },
                                    onTap = { pos ->
                                        if (pos.x < size.width * 0.28f) stepBackward()
                                        else stepForward()
                                    },
                                )
                            }
                    ) {
                        AnimatedContent(
                            targetState = safePage to revealed,
                            modifier = Modifier.fillMaxSize(),
                            transitionSpec = {
                                val sameChapter = initialState.first == targetState.first
                                if (sameChapter) {
                                    // Two-beat Intrigue -> Bento Hero reveal inside the same chapter
                                    (fadeIn(LadMotion.defaultEffects(animations)) +
                                            scaleIn(
                                                LadMotion.defaultSpatial(animations),
                                                initialScale = 0.92f,
                                            ))
                                        .togetherWith(fadeOut(LadMotion.fastEffects(animations)))
                                } else {
                                    // Clean M3 Expressive Shared X-Axis transition between chapters
                                    val sign = if (navDirection >= 0) 1 else -1
                                    (fadeIn(LadMotion.defaultEffects(animations)) +
                                            slideInHorizontally(
                                                LadMotion.defaultSpatial(animations)
                                            ) {
                                                it / 5 * sign
                                            } +
                                            scaleIn(
                                                LadMotion.defaultSpatial(animations),
                                                initialScale = 0.95f,
                                            ))
                                        .togetherWith(
                                            fadeOut(LadMotion.fastEffects(animations)) +
                                                slideOutHorizontally(
                                                    LadMotion.defaultSpatial(animations)
                                                ) {
                                                    -it / 6 * sign
                                                }
                                        )
                                }
                            },
                            label = "recap chapter stage",
                        ) { (pageIdx, showHero) ->
                            val slide = slides[pageIdx]
                            if (!showHero) {
                                RecapIntrigueBeat(
                                    index = pageIdx,
                                    slide = slide,
                                    animations = animations,
                                )
                            } else {
                                RecapBentoSlide(
                                    index = pageIdx,
                                    slide = slide,
                                    stats = stats,
                                    label = label,
                                    settings = settings,
                                    tracks = tracks,
                                    animations = animations,
                                )
                            }
                        }
                    }

                    // Compact Modern M3 Expressive Bottom Pill Controls
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        FilledTonalIconButton(
                            onClick = ::stepBackward,
                            enabled = safePage > 0,
                            modifier = Modifier.size(48.dp),
                            shape = CircleShape,
                        ) {
                            Icon(Icons.Default.ChevronLeft, strings[R.string.back])
                        }
                        Surface(
                            shape = CircleShape,
                            color = scheme.surfaceContainerLow,
                            modifier = Modifier.weight(1f).height(48.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    "${safePage + 1} / ${slides.size}",
                                    style =
                                        MaterialTheme.typography.labelLarge.copy(
                                            fontFeatureSettings = "tnum"
                                        ),
                                    color = scheme.onSurfaceVariant,
                                )
                            }
                        }
                        val isFinalAction = safePage == slides.lastIndex && revealed
                        Button(
                            onClick = ::stepForward,
                            modifier = Modifier.height(48.dp),
                            contentPadding = PaddingValues(horizontal = 22.dp),
                            shapes = ButtonDefaults.shapes(),
                        ) {
                            Text(
                                if (isFinalAction) strings[R.string.done]
                                else strings[R.string.statistics_screens_next],
                                fontWeight = FontWeight.Bold,
                            )
                            Spacer(Modifier.width(6.dp))
                            Icon(
                                if (isFinalAction) Icons.Default.Check
                                else Icons.AutoMirrored.Filled.ArrowForward,
                                null,
                                Modifier.size(18.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Clean Material 3 Expressive two-beat intrigue card:
 * Solid tonal shape badge ABOVE the headline (zero overlapping wireframes behind text).
 */
@Composable
private fun RecapIntrigueBeat(
    index: Int,
    slide: RecapSlide,
    animations: Boolean,
) {
    val badgeShape = recapThemeShape(slide.shape)
    val badgeIcon =
        when (index) {
            1 -> Icons.Default.Schedule
            3 -> Icons.Default.MusicNote
            5 -> Icons.Default.Mic
            7 -> Icons.Default.Album
            8 -> Icons.Default.WbTwilight
            else -> Icons.Default.AutoAwesome
        }
    val infinite = rememberInfiniteTransition(label = "intrigue pulse")
    val pulse by
        if (animations)
            infinite.animateFloat(
                initialValue = 0.94f,
                targetValue = 1.04f,
                animationSpec =
                    infiniteRepeatable(
                        animation = tween(1100, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse,
                    ),
                label = "badge breathe",
            )
        else remember { mutableFloatStateOf(1f) }

    Box(
        Modifier.fillMaxSize().padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(36.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 40.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                Surface(
                    shape = badgeShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.primary,
                    modifier =
                        Modifier.size(84.dp).graphicsLayer {
                            scaleX = pulse
                            scaleY = pulse
                        },
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(badgeIcon, null, Modifier.size(36.dp))
                    }
                }
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                    contentColor = MaterialTheme.colorScheme.primary,
                ) {
                    Text(
                        slide.eyebrow,
                        Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Text(
                    slide.teaser,
                    style =
                        MaterialTheme.typography.headlineMedium.copy(
                            fontSize = 26.sp,
                            lineHeight = 34.sp,
                        ),
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/**
 * Handcrafted Material 3 Expressive Bento layouts for all 10 Recap chapters.
 */
@Composable
private fun RecapBentoSlide(
    index: Int,
    slide: RecapSlide,
    stats: MusicStats,
    label: String,
    settings: Settings,
    tracks: List<Track>,
    animations: Boolean,
) {
    val strings = rememberText()
    val scheme = MaterialTheme.colorScheme
    val heroShape = recapThemeShape(slide.shape)

    var countStarted by remember(index) { mutableStateOf(!animations) }
    LaunchedEffect(index, animations) { countStarted = true }
    val countFraction by
        animateFloatAsState(
            targetValue = if (countStarted) 1f else 0f,
            animationSpec =
                if (animations && slide.countTarget != null)
                    tween(900, easing = FastOutSlowInEasing)
                else snap(),
            label = "bento count",
        )

    Column(
        Modifier.fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        when (index) {
            // SLIDE 0: Opening Editorial Poster + Collage + Quick Metrics
            0 -> {
                BitsReveal(key = "s0-hero", enabled = animations) {
                    Surface(
                        shape = RoundedCornerShape(32.dp),
                        color = scheme.primaryContainer,
                        contentColor = scheme.onPrimaryContainer,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            Modifier.fillMaxWidth().padding(24.dp),
                            verticalArrangement = Arrangement.spacedBy(18.dp),
                        ) {
                            val covers =
                                stats.tracks.map { it.artwork }.filter { it.isNotBlank() }.distinct().take(4)
                            if (covers.size >= 2) {
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    covers.take(3).forEachIndexed { cIdx, art ->
                                        val s = listOf(FlowerShape, CloverShape, CookieShape)[cIdx % 3]
                                        CompositionLocalProvider(LocalCoverMorphShape provides s) {
                                            Artwork(
                                                art,
                                                slide.value,
                                                Modifier.weight(1f).aspectRatio(1f),
                                                20,
                                            )
                                        }
                                    }
                                }
                            } else {
                                Surface(
                                    shape = FlowerShape,
                                    color = scheme.primary,
                                    contentColor = scheme.onPrimary,
                                    modifier = Modifier.size(72.dp),
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(Icons.Default.GraphicEq, null, Modifier.size(34.dp))
                                    }
                                }
                            }
                            Surface(
                                shape = CircleShape,
                                color = scheme.primary.copy(alpha = 0.20f),
                                contentColor = scheme.primary,
                            ) {
                                Text(
                                    slide.eyebrow,
                                    Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                            Text(
                                slide.title,
                                style = MaterialTheme.typography.titleLarge,
                                color = scheme.onPrimaryContainer.copy(alpha = 0.85f),
                            )
                            Text(
                                slide.value,
                                style =
                                    MaterialTheme.typography.displaySmall.copy(
                                        fontSize = 40.sp,
                                        lineHeight = 44.sp,
                                    ),
                                fontWeight = FontWeight.Black,
                            )
                            Text(
                                slide.caption,
                                style = MaterialTheme.typography.bodyLarge,
                                color = scheme.onPrimaryContainer.copy(alpha = 0.84f),
                            )
                        }
                    }
                }
                BitsReveal(key = "s0-grid", delayMs = 90, enabled = animations) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        BentoMiniStat(
                            value = "${stats.totalMs / 60_000}",
                            label =
                                strings.plural(
                                    R.plurals.statistics_screens_minutes,
                                    stats.totalMs / 60_000,
                                ),
                            containerColor = scheme.secondaryContainer,
                            contentColor = scheme.onSecondaryContainer,
                            modifier = Modifier.weight(1f),
                        )
                        BentoMiniStat(
                            value = "${stats.uniqueTracks}",
                            label = strings[R.string.library_screens_tracks],
                            containerColor = scheme.surfaceContainerHigh,
                            contentColor = scheme.onSurface,
                            modifier = Modifier.weight(1f),
                        )
                        BentoMiniStat(
                            value = "${stats.uniqueArtists}",
                            label = strings[R.string.artists],
                            containerColor = scheme.tertiaryContainer,
                            contentColor = scheme.onTertiaryContainer,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            // SLIDES 1 & 2: Big Count-Up Bento Hero + 2-Column Supporting Tiles
            1,
            2 -> {
                val displayed =
                    if (slide.countTarget != null && animations) {
                        val v = (slide.countTarget * countFraction).roundToLong()
                        if (index == 1) "%,d".format(v) else v.toString()
                    } else slide.value

                BitsReveal(key = "stat-hero-$index", enabled = animations) {
                    Surface(
                        shape = RoundedCornerShape(32.dp),
                        color =
                            if (index == 1) scheme.primaryContainer else scheme.tertiaryContainer,
                        contentColor =
                            if (index == 1) scheme.onPrimaryContainer
                            else scheme.onTertiaryContainer,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            Modifier.fillMaxWidth().padding(24.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = scheme.surface.copy(alpha = 0.28f),
                                ) {
                                    Text(
                                        slide.eyebrow,
                                        Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                                Surface(
                                    shape = heroShape,
                                    color = scheme.primary,
                                    contentColor = scheme.onPrimary,
                                    modifier = Modifier.size(48.dp),
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            if (index == 1) Icons.Default.Schedule
                                            else Icons.Default.Repeat,
                                            null,
                                            Modifier.size(24.dp),
                                        )
                                    }
                                }
                            }
                            Text(
                                slide.title,
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                displayed,
                                style =
                                    MaterialTheme.typography.displayLarge.copy(
                                        fontSize = 56.sp,
                                        lineHeight = 62.sp,
                                        fontWeight = FontWeight.Black,
                                        fontFeatureSettings = "tnum",
                                    ),
                            )
                            Text(
                                slide.caption,
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }
                    }
                }
                BitsReveal(key = "stat-sub-$index", delayMs = 90, enabled = animations) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        if (index == 1) {
                            BentoMiniStat(
                                value = "${stats.totalMs / 3_600_000}",
                                label =
                                    strings.plural(
                                        R.plurals.statistics_screens_hours,
                                        stats.totalMs / 3_600_000,
                                    ),
                                containerColor = scheme.secondaryContainer,
                                contentColor = scheme.onSecondaryContainer,
                                modifier = Modifier.weight(1f),
                            )
                            BentoMiniStat(
                                value = "${stats.days.size}",
                                label = strings[R.string.a_day_of_music],
                                containerColor = scheme.surfaceContainerHigh,
                                contentColor = scheme.onSurface,
                                modifier = Modifier.weight(1f),
                            )
                        } else {
                            BentoMiniStat(
                                value = "${stats.uniqueTracks}",
                                label =
                                    strings.plural(
                                        R.plurals.different_tracks,
                                        stats.uniqueTracks,
                                    ),
                                containerColor = scheme.primaryContainer,
                                contentColor = scheme.onPrimaryContainer,
                                modifier = Modifier.weight(1f),
                            )
                            BentoMiniStat(
                                value = "${stats.uniqueArtists}",
                                label = strings[R.string.statistics_screens_artists],
                                containerColor = scheme.surfaceContainerHigh,
                                contentColor = scheme.onSurface,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }

            // SLIDES 3 & 5: #1 Top Track & #1 Top Artist Hero Showcase Bento
            3,
            5 -> {
                val topItem = slide.ranking.firstOrNull()
                BitsReveal(key = "spot-card-$index", enabled = animations) {
                    Surface(
                        shape = RoundedCornerShape(32.dp),
                        color = scheme.surfaceContainerHigh,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            Modifier.fillMaxWidth().padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column {
                                    Text(
                                        slide.eyebrow,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = scheme.primary,
                                        fontWeight = FontWeight.Bold,
                                    )
                                    Text(
                                        slide.title,
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                                Surface(
                                    shape = CircleShape,
                                    color = scheme.primary,
                                    contentColor = scheme.onPrimary,
                                ) {
                                    Text(
                                        "#1",
                                        Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Black,
                                    )
                                }
                            }
                            if (topItem != null) {
                                if (topItem.detail == "Artist") {
                                    val topArtistTracks =
                                        remember(topItem.name, tracks, settings.mergeArtists) {
                                            tracksForArtist(
                                                topItem.name,
                                                tracks,
                                                settings.mergeArtists,
                                            )
                                        }
                                    ArtistPortrait(
                                        name = topItem.name,
                                        tracks = topArtistTracks,
                                        modifier = Modifier.size(196.dp),
                                        openSource = true,
                                        round = 28,
                                        fallbackArtwork = topItem.artwork.ifBlank { null },
                                        shapeOverride = heroShape,
                                    )
                                } else {
                                    CompositionLocalProvider(LocalCoverMorphShape provides heroShape) {
                                        Artwork(
                                            topItem.artwork,
                                            slide.value,
                                            Modifier.size(196.dp),
                                            28,
                                        )
                                    }
                                }
                            }
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Text(
                                    slide.value,
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.Black,
                                    textAlign = TextAlign.Center,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    slide.caption,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = scheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }
                    }
                }
                if (topItem != null) {
                    BitsReveal(key = "spot-metrics-$index", delayMs = 90, enabled = animations) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            BentoMiniStat(
                                value =
                                    strings.format(
                                        R.string.statistics_screens_min,
                                        topItem.listenedMs / 60_000,
                                    ),
                                label = strings[R.string.total_listening_time],
                                containerColor = scheme.primaryContainer,
                                contentColor = scheme.onPrimaryContainer,
                                modifier = Modifier.weight(1f),
                            )
                            BentoMiniStat(
                                value = "${topItem.plays}",
                                label = strings[R.string.track_details_panel_plays],
                                containerColor = scheme.secondaryContainer,
                                contentColor = scheme.onSecondaryContainer,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }

            // SLIDES 4 & 6: Top 5 Tracks & Top 5 Artists (M3E Connected Segmented List)
            4,
            6 -> {
                BitsReveal(key = "list-head-$index", enabled = animations) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = scheme.primary,
                                contentColor = scheme.onPrimary,
                            ) {
                                Text(
                                    slide.value,
                                    Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Black,
                                )
                            }
                            Text(
                                slide.eyebrow,
                                style = MaterialTheme.typography.labelMedium,
                                color = scheme.primary,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        Text(
                            slide.title,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Black,
                        )
                        Text(
                            slide.caption,
                            style = MaterialTheme.typography.bodyMedium,
                            color = scheme.onSurfaceVariant,
                        )
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    val items = slide.ranking
                    items.forEachIndexed { i, r ->
                        BitsReveal(
                            key = "seg-$index-$i-${r.name}",
                            delayMs = 60 + i * 55,
                            enabled = animations,
                        ) {
                            val rowArtistTracks =
                                remember(r.name, r.detail, tracks, settings.mergeArtists) {
                                    if (r.detail == "Artist")
                                        tracksForArtist(r.name, tracks, settings.mergeArtists)
                                    else emptyList()
                                }
                            val first = i == 0
                            Surface(
                                shape = segmentedItemShape(i, items.size),
                                color =
                                    if (first) scheme.primaryContainer
                                    else scheme.surfaceContainerHigh,
                                contentColor =
                                    if (first) scheme.onPrimaryContainer else scheme.onSurface,
                            ) {
                                Box(Modifier.padding(horizontal = 14.dp, vertical = 4.dp)) {
                                    RankingRow(r, i, rowArtistTracks)
                                }
                            }
                        }
                    }
                }
            }

            // SLIDE 7: #1 Top Album Bento Card + Connected Runner-Up Albums
            7 -> {
                val topAlbum = slide.ranking.firstOrNull()
                BitsReveal(key = "album-hero", enabled = animations) {
                    Surface(
                        shape = RoundedCornerShape(32.dp),
                        color = scheme.primaryContainer,
                        contentColor = scheme.onPrimaryContainer,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            Modifier.fillMaxWidth().padding(22.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    "${slide.eyebrow} · ${slide.title}",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                )
                                Surface(
                                    shape = CircleShape,
                                    color = scheme.primary,
                                    contentColor = scheme.onPrimary,
                                ) {
                                    Text(
                                        "#1",
                                        Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Black,
                                    )
                                }
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                            ) {
                                if (topAlbum != null) {
                                    CompositionLocalProvider(
                                        LocalCoverMorphShape provides CookieShape
                                    ) {
                                        Artwork(
                                            topAlbum.artwork,
                                            slide.value,
                                            Modifier.size(112.dp),
                                            20,
                                        )
                                    }
                                }
                                Column(
                                    Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Text(
                                        slide.value,
                                        style = MaterialTheme.typography.headlineSmall,
                                        fontWeight = FontWeight.Black,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    topAlbum?.detail?.takeIf { it.isNotBlank() }?.let {
                                        Text(
                                            it,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = scheme.onPrimaryContainer.copy(alpha = 0.85f),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                    Text(
                                        slide.caption,
                                        style = MaterialTheme.typography.labelLarge,
                                        color = scheme.primary,
                                    )
                                }
                            }
                        }
                    }
                }
                val others = slide.ranking.drop(1)
                if (others.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        others.forEachIndexed { idx, r ->
                            BitsReveal(
                                key = "alb-other-$idx-${r.name}",
                                delayMs = 80 + idx * 55,
                                enabled = animations,
                            ) {
                                Surface(
                                    shape = segmentedItemShape(idx, others.size),
                                    color = scheme.surfaceContainerHigh,
                                ) {
                                    Box(Modifier.padding(horizontal = 14.dp, vertical = 4.dp)) {
                                        RankingRow(r, idx + 1)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // SLIDE 8: Musical Day & 24h Rhythm Skyline
            8 -> {
                val formattedDay =
                    stats.bestDay?.let { AppDates.format(it, settings.dateFormat) } ?: slide.value
                BitsReveal(key = "s8-hero", enabled = animations) {
                    Surface(
                        shape = RoundedCornerShape(32.dp),
                        color = scheme.primaryContainer,
                        contentColor = scheme.onPrimaryContainer,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            Modifier.fillMaxWidth().padding(24.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Text(
                                slide.eyebrow,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                slide.title,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                formattedDay,
                                style =
                                    MaterialTheme.typography.displaySmall.copy(
                                        fontFeatureSettings = "tnum"
                                    ),
                                fontWeight = FontWeight.Black,
                            )
                            Text(
                                slide.caption,
                                style = MaterialTheme.typography.bodyLarge,
                                color = scheme.onPrimaryContainer.copy(alpha = 0.85f),
                            )
                        }
                    }
                }
                if (slide.hours.isNotEmpty()) {
                    BitsReveal(key = "s8-chart", delayMs = 90, enabled = animations) {
                        Surface(
                            shape = RoundedCornerShape(28.dp),
                            color = scheme.surfaceContainerHigh,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(
                                Modifier.fillMaxWidth().padding(20.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        strings[R.string.time_for_music],
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                    )
                                    Surface(
                                        shape = CircleShape,
                                        color = scheme.primary,
                                        contentColor = scheme.onPrimary,
                                    ) {
                                        Text(
                                            "%02d:00".format(stats.favoriteHour),
                                            Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                            style =
                                                MaterialTheme.typography.labelMedium.copy(
                                                    fontFeatureSettings = "tnum"
                                                ),
                                            fontWeight = FontWeight.Bold,
                                        )
                                    }
                                }
                                val maxHour = slide.hours.maxOrNull()?.coerceAtLeast(1L) ?: 1L
                                Row(
                                    Modifier.fillMaxWidth().height(104.dp),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.Bottom,
                                ) {
                                    slide.hours.forEachIndexed { hIdx, ms ->
                                        val isPeak = hIdx == stats.favoriteHour
                                        Box(
                                            Modifier.weight(1f)
                                                .fillMaxHeight(
                                                    (ms.toFloat() / maxHour).coerceAtLeast(0.08f)
                                                )
                                                .clip(CircleShape)
                                                .background(
                                                    if (isPeak) scheme.primary
                                                    else scheme.onSurface.copy(alpha = 0.22f)
                                                )
                                        )
                                    }
                                }
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text(
                                        "00:00",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = scheme.onSurfaceVariant,
                                    )
                                    Text(
                                        "12:00",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = scheme.onSurfaceVariant,
                                    )
                                    Text(
                                        "23:00",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = scheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // SLIDE 9: Finale Bento Summary Poster
            else -> {
                BitsReveal(key = "s9-banner", enabled = animations) {
                    Surface(
                        shape = RoundedCornerShape(32.dp),
                        color = scheme.primaryContainer,
                        contentColor = scheme.onPrimaryContainer,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            Modifier.fillMaxWidth().padding(22.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                "${slide.eyebrow} · ${slide.title}",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                slide.value,
                                style =
                                    MaterialTheme.typography.displaySmall.copy(
                                        fontSize = 42.sp,
                                        lineHeight = 46.sp,
                                        fontFeatureSettings = "tnum",
                                    ),
                                fontWeight = FontWeight.Black,
                            )
                            Text(
                                slide.caption,
                                style = MaterialTheme.typography.bodyMedium,
                                color = scheme.onPrimaryContainer.copy(alpha = 0.85f),
                            )
                        }
                    }
                }
                if (stats.artists.isNotEmpty() || stats.tracks.isNotEmpty()) {
                    BitsReveal(key = "s9-duo", delayMs = 70, enabled = animations) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            stats.artists.firstOrNull()?.let { topArt ->
                                val artTracks =
                                    remember(topArt.name, tracks, settings.mergeArtists) {
                                        tracksForArtist(topArt.name, tracks, settings.mergeArtists)
                                    }
                                Surface(
                                    shape = RoundedCornerShape(26.dp),
                                    color = scheme.secondaryContainer,
                                    contentColor = scheme.onSecondaryContainer,
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Column(
                                        Modifier.padding(16.dp),
                                        verticalArrangement = Arrangement.spacedBy(10.dp),
                                    ) {
                                        ArtistPortrait(
                                            name = topArt.name,
                                            tracks = artTracks,
                                            modifier = Modifier.size(48.dp),
                                            round = 14,
                                            fallbackArtwork = topArt.artwork.ifBlank { null },
                                            shapeOverride = CloverShape,
                                        )
                                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                            Text(
                                                strings[R.string.top_artist],
                                                style = MaterialTheme.typography.labelSmall,
                                            )
                                            Text(
                                                topArt.name,
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                    }
                                }
                            }
                            stats.tracks.firstOrNull()?.let { topTrk ->
                                Surface(
                                    shape = RoundedCornerShape(26.dp),
                                    color = scheme.tertiaryContainer,
                                    contentColor = scheme.onTertiaryContainer,
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Column(
                                        Modifier.padding(16.dp),
                                        verticalArrangement = Arrangement.spacedBy(10.dp),
                                    ) {
                                        CompositionLocalProvider(
                                            LocalCoverMorphShape provides FlowerShape
                                        ) {
                                            Artwork(
                                                topTrk.artwork,
                                                topTrk.name,
                                                Modifier.size(48.dp),
                                                14,
                                            )
                                        }
                                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                            Text(
                                                strings[R.string.top_track],
                                                style = MaterialTheme.typography.labelSmall,
                                            )
                                            Text(
                                                topTrk.name,
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                if (slide.ranking.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        slide.ranking.forEachIndexed { idx, r ->
                            BitsReveal(
                                key = "s9-r-$idx-${r.name}",
                                delayMs = 120 + idx * 55,
                                enabled = animations,
                            ) {
                                Surface(
                                    shape = segmentedItemShape(idx, slide.ranking.size),
                                    color = scheme.surfaceContainerHigh,
                                ) {
                                    Box(Modifier.padding(horizontal = 14.dp, vertical = 4.dp)) {
                                        RankingRow(r, idx)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (stats.totalMs == 0L && !label.contains(strings[R.string.demo]))
            Text(
                strings[R.string.no_listening_activity_in_this_period],
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
            )
    }
}

@Composable
private fun BentoMiniStat(
    value: String,
    label: String,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        color = containerColor,
        contentColor = contentColor,
    ) {
        Column(
            Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                value,
                style =
                    MaterialTheme.typography.headlineSmall.copy(
                        fontFeatureSettings = "tnum"
                    ),
                fontWeight = FontWeight.Black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = contentColor.copy(alpha = 0.82f),
            )
        }
    }
}
