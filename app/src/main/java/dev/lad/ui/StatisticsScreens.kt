package dev.lad.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.pager.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.lad.R
import dev.lad.data.*
import dev.lad.i18n.rememberText
import dev.lad.statistics.*
import dev.lad.statistics.Period
import java.time.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun RankingRow(rank: Ranking, index: Int) {
    val strings = rememberText()
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            "${index+1}",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Black,
            modifier = Modifier.width(24.dp),
        )
        Artwork(rank.artwork, rank.name, Modifier.size(48.dp), 16)
        Column(Modifier.weight(1f)) {
            Text(
                rank.name,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                rank.detail,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Text(
            strings.format(R.string.statistics_screens_min, rank.listenedMs / 60_000),
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

@Composable
fun StatisticsScreen(
    history: List<Listen>,
    tracks: List<Track>,
    onRecap: (MusicStats, String, Boolean) -> Unit,
    runs: List<PlaybackRun> = emptyList(),
) {
    val strings = rememberText()
    val mergeArtists = LocalLadSettings.current.mergeArtists
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
    LazyColumn(
        contentPadding = PaddingValues(LadSpacing.page),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(Period.entries) { p ->
                    FilterChip(
                        selected = period == p.ordinal,
                        onClick = { period = p.ordinal },
                        label = { Text(strings.label(p.label)) },
                    )
                }
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
                    "${stats.totalMs/3_600_000}",
                    strings[R.string.statistics_screens_hours],
                    Modifier.weight(1f),
                )
                Metric(
                    "${stats.totalMs/60_000}",
                    strings[R.string.statistics_screens_minutes],
                    Modifier.weight(1f),
                )
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Metric("${stats.plays}", strings[R.string.plays], Modifier.weight(1f))
                Metric(
                    "${stats.uniqueTracks}",
                    strings[R.string.different_tracks],
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
            val mostPlayed =
                history.filter { it.counted }.groupBy { it.trackId }.values.maxByOrNull { it.size }
            val days = history.groupBy {
                java.time.Instant.ofEpochMilli(it.startedAt)
                    .atZone(ZoneId.systemDefault())
                    .toLocalDate()
            }
            val best = days.maxByOrNull { (_, rows) -> rows.sumOf { it.listenedMs } }
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                InfoTile(
                    strings[R.string.your_most_musical_day],
                    best?.let {
                        strings.format(
                            R.string.statistics_screens_min_478,
                            it.key,
                            it.value.sumOf { row -> row.listenedMs } / 60000,
                        )
                    } ?: strings[R.string.more_to_come],
                )
                InfoTile(
                    strings[R.string.longest_session],
                    runs
                        .maxByOrNull { it.listenedMs }
                        ?.let { strings.format(R.string.of_music, duration(it.listenedMs)) }
                        ?: strings[R.string.start_listening_to_set_a_record],
                )
                InfoTile(
                    strings[R.string.most_repeated],
                    mostPlayed?.let {
                        strings.format(R.string.statistics_screens_plays, it.first().title, it.size)
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
                IconButton(onClick = { year-- }) {
                    Icon(Icons.Default.ChevronLeft, strings[R.string.previous_year])
                }
                Text(year.toString(), style = MaterialTheme.typography.titleLarge)
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
                    onRecap(
                        RecapGenerator.aggregate(
                            history,
                            RecapGenerator.year(year),
                            mergeArtists = mergeArtists,
                        ),
                        strings.format(R.string.your, year),
                        true,
                    )
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
                    Icon(Icons.Default.AutoAwesome, null, Modifier.size(34.dp))
                    Column {
                        Text(
                            strings[R.string.statistics_screens_your_year_in_music],
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(strings.format(R.string.text_10_stories_about_you, year))
                    }
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Season.entries.forEach { s ->
                    FilledTonalButton(
                        onClick = {
                            val w = RecapGenerator.season(s, year)
                            onRecap(
                                RecapGenerator.aggregate(history, w, mergeArtists = mergeArtists),
                                "${strings.label(s.label)} $year",
                                false,
                            )
                        },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 2.dp, vertical = 14.dp),
                    ) {
                        Text(strings.label(s.label), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
        item {
            TextButton(
                onClick = {
                    onRecap(RecapGenerator.demo(tracks), strings[R.string.demo_your_year], true)
                }
            ) {
                Text(strings[R.string.preview_a_demo_recap])
            }
        }
        if (stats.tracks.isNotEmpty()) {
            item { SectionTitle(strings[R.string.favourite_tracks]) }
            itemsIndexed(stats.tracks.take(5), key = { _, r -> "t${r.name}" }) { i, r ->
                RankingRow(r, i)
            }
            item { SectionTitle(strings[R.string.statistics_screens_artists]) }
            itemsIndexed(stats.artists.take(5), key = { _, r -> "a${r.name}" }) { i, r ->
                RankingRow(r, i)
            }
            item {
                SectionTitle(strings[R.string.most_played_album], stats.albums.firstOrNull()?.name)
            }
            itemsIndexed(stats.albums.take(5), key = { _, r -> "album${r.name}" }) { i, r ->
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
                    stats.hours.forEach { h ->
                        Box(
                            Modifier.weight(1f)
                                .fillMaxHeight((h.toFloat() / max).coerceAtLeast(0.04f))
                                .clip(RoundedCornerShape(5.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer)
                        )
                    }
                }
            }
            item {
                Text(
                    "00:00                         12:00                         23:00",
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            item {
                Text(
                    strings.format(
                        R.string.your_most_musical_day_minutes,
                        stats.bestDay,
                        (stats.days[stats.bestDay] ?: 0) / 60_000,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
fun RecapScreen(
    stats: MusicStats,
    label: String,
    year: Boolean,
    onClose: () -> Unit,
    animations: Boolean,
    settings: Settings,
) {
    val strings = rememberText()
    val slides =
        remember(stats, label, year, strings) { RecapGenerator.slides(stats, label, year, strings) }
    val pager = rememberPagerState(pageCount = { slides.size })
    val scope = rememberCoroutineScope()
    val currentSlide = slides[pager.currentPage]
    val cover = currentSlide.ranking.firstOrNull()?.artwork ?: stats.tracks.firstOrNull()?.artwork
    val seed =
        artworkSeed(cover, settings.recapArtwork)
            ?: (AccentColors[settings.accent] ?: AccentColors.getValue("violet"))
    val target = recapScheme(seed)
    val background by
        androidx.compose.animation.animateColorAsState(
            target.surface,
            animationSpec = tween(LadMotion.duration(LadMotion.palette, animations)),
            label = "recap color",
        )
    MaterialTheme(colorScheme = target.copy(surface = background, background = background)) {
        CompositionLocalProvider(LocalContentColor provides target.onSurface) {
            AmbientBackdrop(
                seed,
                enabled = settings.ambientBackground,
                animations = animations,
                modifier = Modifier.fillMaxSize(),
                artwork = if (settings.recapArtwork) cover else null,
            ) {
                Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            if (label.contains(strings[R.string.demo]))
                                strings[R.string.demo_sample_data]
                            else strings.format(R.string.statistics_screens_lad, label),
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.weight(1f),
                        )
                        RecapShareButton(stats, slides[pager.currentPage], label)
                        IconButton(onClick = onClose) {
                            Icon(Icons.Default.Close, strings[R.string.close_recap])
                        }
                    }
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        slides.indices.forEach { i ->
                            Box(
                                Modifier.weight(1f)
                                    .height(4.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(
                                        MaterialTheme.colorScheme.onSurface.copy(
                                            alpha = if (i <= pager.currentPage) 0.8f else 0.15f
                                        )
                                    )
                            )
                        }
                    }
                    HorizontalPager(state = pager, modifier = Modifier.weight(1f)) { index ->
                        val slide = slides[index]
                        Column(
                            Modifier.fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(LadSpacing.page),
                            verticalArrangement = Arrangement.spacedBy(LadSpacing.page),
                        ) {
                            Spacer(Modifier.height(10.dp))
                            Text(
                                slide.eyebrow,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                slide.title,
                                style = MaterialTheme.typography.headlineLarge,
                                fontWeight = FontWeight.Black,
                            )
                            Surface(
                                shape = LadShapes.hero,
                                color = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(
                                    slide.value,
                                    Modifier.padding(LadSpacing.page),
                                    fontSize = if (slide.value.length < 12) 56.sp else 32.sp,
                                    lineHeight = if (slide.value.length < 12) 62.sp else 38.sp,
                                    fontWeight = FontWeight.Black,
                                )
                            }
                            if (slide.ranking.isNotEmpty() && index in listOf(3, 5, 7)) {
                                Artwork(
                                    slide.ranking.first().artwork,
                                    slide.value,
                                    Modifier.size(152.dp),
                                    38,
                                )
                            }
                            Text(slide.caption, style = MaterialTheme.typography.titleMedium)
                            slide.ranking.forEachIndexed { i, r -> RankingRow(r, i) }
                            if (stats.totalMs == 0L && !label.contains(strings[R.string.demo]))
                                Text(
                                    strings[R.string.no_listening_activity_in_this_period],
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                        }
                    }
                    Row(
                        Modifier.fillMaxWidth().padding(20.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    if (animations)
                                        pager.animateScrollToPage(
                                            pager.currentPage - 1,
                                            animationSpec =
                                                tween(
                                                    LadMotion.expressive,
                                                    easing = LadMotion.easing,
                                                ),
                                        )
                                    else pager.scrollToPage(pager.currentPage - 1)
                                }
                            },
                            enabled = pager.currentPage > 0,
                        ) {
                            Text(strings[R.string.back])
                        }
                        Text(
                            "${pager.currentPage+1} / ${slides.size}",
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.labelLarge,
                        )
                        Button(
                            onClick = {
                                if (pager.currentPage == slides.lastIndex) onClose()
                                else
                                    scope.launch {
                                        if (animations)
                                            pager.animateScrollToPage(
                                                pager.currentPage + 1,
                                                animationSpec =
                                                    tween(
                                                        LadMotion.expressive,
                                                        easing = LadMotion.easing,
                                                    ),
                                            )
                                        else pager.scrollToPage(pager.currentPage + 1)
                                    }
                            },
                            contentPadding = PaddingValues(24.dp, 16.dp),
                        ) {
                            Text(
                                if (pager.currentPage == slides.lastIndex) strings[R.string.done]
                                else strings[R.string.statistics_screens_next]
                            )
                        }
                    }
                }
            }
        }
    }
}
