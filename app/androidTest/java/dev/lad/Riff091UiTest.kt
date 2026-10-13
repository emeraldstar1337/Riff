package dev.lad

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import dev.lad.data.Settings
import dev.lad.data.Track
import dev.lad.ui.*
import kotlin.math.abs
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class Riff091UiTest {
    @get:Rule val rule = createComposeRule()
    private val app
        get() =
            InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
                as LadApplication

    private val appearance =
        Settings(theme = "dark", dynamicColor = false, artworkColor = false, animations = false)

    @Before
    fun useRussian() {
        dev.lad.i18n.AppLanguage.set(app, "ru")
    }

    @Test
    fun roundHandlesAreCenteredAndSeparatedFromBothTracks() {
        var primary = Color.Unspecified
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
            val expected = 10 * density + (image.width - 20 * density) * 0.4f
            assertEquals("$tag: handle is misplaced", expected, center, 2 * density)
            assertTrue(
                "$tag: missing gap",
                dot.first - runs[runs.lastIndex - 1].last >= 2 * density,
            )
        }
    }

    @Test
    fun artistPageCombinesCreditsButRetainsGuestCollections() {
        val tracks =
            listOf("PHARAOH", "pharaoh feat. Guest", "PHARAOH & Guest").mapIndexed { index, artist
                ->
                Track(
                    9300L + index,
                    "content://qa/$index",
                    "Song $index",
                    artist,
                    "Album",
                    1,
                    60000,
                )
            }
        val vm = LadViewModel(app)
        var chosen: List<Track>? = null
        try {
            rule.setContent {
                LadTheme(appearance) {
                    LibraryScreen(
                        tracks,
                        null,
                        vm,
                        true,
                        false,
                        {},
                        { _, rows -> chosen = rows },
                        {},
                    )
                }
            }
            rule.onNodeWithText("Артисты").performClick()
            rule.onNodeWithText("PHARAOH").performClick()
            rule.runOnIdle { assertEquals(tracks, chosen) }
            rule.onNodeWithText("Guest").performClick()
            rule.runOnIdle { assertEquals(tracks.drop(1), chosen) }
        } finally {
            val store = androidx.lifecycle.ViewModelStore()
            store.put("fixture", vm)
            store.clear()
        }
    }
}
