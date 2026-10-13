package dev.lad

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import dev.lad.data.Settings
import dev.lad.data.Track
import dev.lad.domain.LrcParser
import dev.lad.domain.QueueEntry
import dev.lad.domain.QueueSnapshot
import dev.lad.playback.PlaybackState
import dev.lad.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class PlayerVisualUiTest {
    @org.junit.Before
    fun useRussianInterface() {
        dev.lad.i18n.AppLanguage.set(
            androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext,
            "ru",
        )
    }

    @get:Rule val rule = createComposeRule()
    private val settings =
        Settings(theme = "dark", dynamicColor = false, animations = false, coverShape = "flower")

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
                Column {
                    LyricLineText(line, true, position.longValue, {}, Modifier.testTag("active"))
                    LyricLineText(line, false, position.longValue, {}, Modifier.testTag("inactive"))
                }
            }
        }
        fun marked(tag: String) =
            rule.onNodeWithTag(tag).fetchSemanticsNode().config[SemanticsProperties.Text].single()
        assertEquals(
            "Свет",
            marked("active").let {
                it.text.substring(it.spanStyles.single().start, it.spanStyles.single().end)
            },
        )
        assertTrue(marked("inactive").spanStyles.isEmpty())
        rule.runOnIdle { position.longValue = 2200 }
        assertEquals(
            "рядом",
            marked("active").let {
                it.text.substring(it.spanStyles.single().start, it.spanStyles.single().end)
            },
        )
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
        val app =
            androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
                .targetContext
                .applicationContext as LadApplication
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
