package dev.lad.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import dev.lad.LadApplication
import dev.lad.data.Settings
import dev.lad.data.Track
import dev.lad.domain.LrcParser
import dev.lad.domain.QueueEntry
import dev.lad.domain.QueueSnapshot
import dev.lad.i18n.AppLanguage
import dev.lad.playback.PlaybackState
import kotlin.math.abs
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class PlayerVisualUiTest {
    @get:Rule val rule = createComposeRule()

    private val app
        get() =
            InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
                as LadApplication

    private val settings =
        Settings(theme = "dark", dynamicColor = false, animations = false, coverShape = "flower")

    @Before
    fun useRussianInterface() {
        AppLanguage.set(app, "ru")
    }

    @Test
    fun tabsHaveEqualHeightAndAlignedCentres() {
        rule.setContent {
            LadTheme(settings) {
                var lyrics by remember { mutableStateOf(false) }
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    PlayerTabs(lyrics) { lyrics = it }
                }
            }
        }
        val art = rule.onNodeWithTag("artwork-tab").fetchSemanticsNode().boundsInRoot
        val lyrics = rule.onNodeWithTag("lyrics-tab").fetchSemanticsNode().boundsInRoot
        val outer = rule.onNodeWithTag("player-tabs").fetchSemanticsNode().boundsInRoot
        assertEquals(art.height, lyrics.height, 0.5f)
        assertEquals(outer.center.y, art.center.y, 0.5f)
        assertEquals(art.center.y, lyrics.center.y, 0.5f)
        rule.onNodeWithTag("artwork-tab").assertIsSelected()
        rule.onNodeWithTag("lyrics-tab").performClick().assertIsSelected()
        rule.onNodeWithTag("artwork-tab").assertIsNotSelected()
    }

    @Test
    fun highlightMovesToTheTimedWordAndNeverMarksInactiveLine() {
        val line = LrcParser.parse("[00:01.00]<00:01.00>Свет <00:02.00>рядом").single()
        val position = mutableLongStateOf(1500)
        rule.setContent {
            LadTheme(settings) {
                Column(Modifier.background(MaterialTheme.colorScheme.surface)) {
                    LyricLineText(line, true, position.longValue, {}, Modifier.testTag("active"))
                    LyricLineText(line, false, position.longValue, {}, Modifier.testTag("inactive"))
                }
            }
        }
        fun brightPixels(tag: String): Int {
            val image = rule.onNodeWithTag(tag).captureToImage()
            val pixels = image.toPixelMap()
            return (0 until image.height).sumOf { y ->
                (0 until image.width).count { x ->
                    pixels[x, y].let { it.luminance() * it.alpha > .65f }
                }
            }
        }
        val first = brightPixels("active")
        assertTrue(first > 0)
        assertEquals(0, brightPixels("inactive"))
        rule.runOnIdle { position.longValue = 2200 }
        assertTrue(brightPixels("active") > first)
        assertEquals(0, brightPixels("inactive"))
    }

    @Test
    fun timedLyricsHandleWrappedWordsRtlAndSettingsChanges() {
        val lyricSettings = mutableStateOf(Settings(lyricsSize = 30f))
        val active = mutableStateOf(true)
        val lines =
            LrcParser.parse(
                "[00:01.00]<00:01.00>A long lyric <00:02.00>with multiple words\n" +
                    "[00:04.00]<00:04.00>مرحبا <00:05.00>بالعالم"
            )
        rule.setContent {
            CompositionLocalProvider(LocalLadSettings provides lyricSettings.value) {
                MaterialTheme(colorScheme = darkColorScheme()) {
                    Column(Modifier.fillMaxWidth()) {
                        lines.forEach { line ->
                            LyricLineText(line, active.value, 5500, {}, endTimeMs = 6500)
                        }
                    }
                }
            }
        }
        rule.onNodeWithText(lines[0].text).assertIsDisplayed()
        rule.onNodeWithText(lines[1].text).assertIsDisplayed()
        rule.runOnIdle {
            active.value = false
            lyricSettings.value =
                lyricSettings.value.copy(animations = false, lyricsAlignment = "start")
        }
        rule.onNodeWithText(lines[0].text).assertIsDisplayed()
        rule.runOnIdle { active.value = true }
        rule.onNodeWithText(lines[1].text).assertIsDisplayed()
    }

    @Test
    fun lyricViewportFadesBothEdgesWithoutDimmingTheActiveArea() {
        rule.setContent {
            Box(Modifier.size(160.dp, 240.dp).background(Color.Black)) {
                Canvas(Modifier.fillMaxSize().lyricEdges().testTag("lyric-viewport")) {
                    drawRect(Color.White)
                }
            }
        }
        val pixels = rule.onNodeWithTag("lyric-viewport").captureToImage().toPixelMap()
        assertTrue(pixels[80, 0].red < .1f)
        assertTrue(pixels[80, pixels.height / 2].red > .95f)
        assertTrue(pixels[80, pixels.height - 1].red < .1f)
        assertTrue(pixels[80, 10].red in 0.1f..0.8f)
    }

    @Test
    fun roundHandlesAreCenteredAndSeparatedFromBothTracks() {
        var primary = Color.Unspecified
        val appearance =
            Settings(theme = "dark", dynamicColor = false, artworkColor = false, animations = false)
        rule.setContent {
            LadTheme(appearance) {
                primary = MaterialTheme.colorScheme.primary
                Column(
                    Modifier.fillMaxSize()
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(20.dp)
                ) {
                    SeekBar(40f, 100f, {}, {}, Modifier.testTag("line"), style = "line_dot")
                    SeekBar(40f, 100f, {}, {}, Modifier.testTag("wave"), style = "wave_dot")
                }
            }
        }
        val density = app.resources.displayMetrics.density
        listOf("line", "wave").forEach { tag ->
            val image = rule.onNodeWithTag(tag).captureToImage()
            val pixels = image.toPixelMap()
            val y = image.height / 2
            val runs = mutableListOf<IntRange>()
            var first = -1
            for (x in 0..image.width) {
                val colored =
                    if (x == image.width) false
                    else
                        pixels[x, y].let {
                            abs(it.red - primary.red) < 0.05f &&
                                abs(it.green - primary.green) < 0.05f &&
                                abs(it.blue - primary.blue) < 0.05f
                        }
                if (colored && first < 0) first = x
                if (!colored && first >= 0) {
                    runs += first until x
                    first = -1
                }
            }
            assertTrue("$tag: track and handle merged", runs.size >= 2)
            val dot = runs.last()
            val center = (dot.first + dot.last) / 2f
            val expected = 6 * density + (image.width - 12 * density) * 0.4f
            assertEquals("$tag: handle is misplaced", expected, center, 2 * density)
            assertTrue(
                "$tag: missing gap",
                dot.first - runs[runs.lastIndex - 1].last >= 2 * density,
            )
        }
    }

    @Test
    fun missingCoverHasGraphicFallbackWithoutLetter() {
        rule.setContent {
            LadTheme(settings) {
                AmbientBackdrop(
                    Color(0xFFDC594F),
                    enabled = true,
                    animations = false,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    Artwork(null, "Заглушка", Modifier.align(Alignment.Center).size(280.dp))
                }
            }
        }
        rule.onNodeWithTag("artwork-placeholder").assertIsDisplayed()
        rule.onNodeWithText("З").assertDoesNotExist()
    }

    @Test
    fun queueScrollStaysBelowHeaderAndCloseRemovesModal() {
        val tracks =
            (1L..35L).map {
                Track(it, "content://qa/$it", "Трек $it", "Артист", "Альбом", 1, 60000)
            }
        val queue = QueueSnapshot(entries = tracks.map { QueueEntry(it.id, 0) }, index = 3)
        val state = PlaybackState(current = tracks[3], queue = queue)
        var removed: Int? = null
        rule.setContent {
            LadTheme(settings) {
                var open by remember { mutableStateOf(true) }
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
                    Text("Плеер", Modifier.testTag("under-player"))
                    if (open)
                        QueueSheet(state, tracks, {}, { _, _ -> }, { removed = it }) {
                            open = false
                        }
                }
            }
        }
        rule.onNodeWithTag("queue-current").assertIsDisplayed()
        val header = rule.onNodeWithTag("queue-header").fetchSemanticsNode().boundsInRoot
        repeat(4) {
            rule.onNodeWithTag("queue-list").performTouchInput { swipeUp(durationMillis = 70) }
            rule.onNodeWithTag("queue-list").performTouchInput { swipeDown(durationMillis = 70) }
        }
        val after = rule.onNodeWithTag("queue-header").fetchSemanticsNode().boundsInRoot
        assertEquals(header.top, after.top, 1f)
        assertEquals(header.bottom, after.bottom, 1f)
        rule.onNodeWithTag("queue-list").performScrollToIndex(25)
        val list = rule.onNodeWithTag("queue-list").fetchSemanticsNode().boundsInRoot
        assertTrue("Scrolling rows overlap the header", list.top >= header.bottom)
        rule.onNodeWithTag("queue-header").assertIsDisplayed()
        rule.onNodeWithTag("queue-list").performScrollToIndex(0)
        rule.onNodeWithContentDescription("Действия в очереди: Трек 5").performClick()
        rule.onNodeWithText("Убрать из очереди").performClick()
        rule.runOnIdle { assertEquals(4, removed) }
        rule.onNodeWithContentDescription("Закрыть Очередь").performClick()
        rule.waitUntil(10000) {
            rule.onAllNodesWithTag("queue-sheet").fetchSemanticsNodes().isEmpty()
        }
        rule.onNodeWithTag("under-player").assertIsDisplayed()
    }

    @Test
    fun audioPanelAggressiveFlingsKeepHeaderStill() {
        lateinit var vm: LadViewModel
        rule.runOnUiThread { vm = LadViewModel(app) }
        rule.setContent {
            LadTheme(settings) {
                var open by remember { mutableStateOf(true) }
                if (open) AudioPanel(settings, PlaybackState(), vm) { open = false }
            }
        }
        rule.onNodeWithTag("audio-header").assertIsDisplayed()
        val before = rule.onNodeWithTag("audio-header").fetchSemanticsNode().boundsInRoot
        repeat(4) {
            rule.onNodeWithTag("audio-body").performTouchInput { swipeUp(durationMillis = 70) }
            rule.onNodeWithTag("audio-body").performTouchInput { swipeDown(durationMillis = 70) }
        }
        val after = rule.onNodeWithTag("audio-header").fetchSemanticsNode().boundsInRoot
        assertEquals(before.top, after.top, 1f)
        assertEquals(before.bottom, after.bottom, 1f)
        rule.onNodeWithContentDescription("Закрыть Звук по-твоему").performClick()
        rule.waitUntil(10000) {
            rule.onAllNodesWithTag("audio-sheet").fetchSemanticsNodes().isEmpty()
        }
    }
}
