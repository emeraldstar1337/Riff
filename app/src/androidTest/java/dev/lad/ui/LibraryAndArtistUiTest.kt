package dev.lad.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import dev.lad.LadApplication
import dev.lad.R
import dev.lad.data.Settings
import dev.lad.data.Track
import dev.lad.i18n.AppLanguage
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class LibraryAndArtistUiTest {
    @get:Rule val compose = createComposeRule()

    private val app
        get() =
            InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
                as LadApplication

    private val appearance =
        Settings(theme = "dark", dynamicColor = false, artworkColor = false, animations = false)

    private fun text(id: Int) =
        AppLanguage.context(InstrumentationRegistry.getInstrumentation().targetContext)
            .getString(id)

    @Before
    fun useRussian() {
        AppLanguage.set(app, "ru")
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
            compose.setContent {
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
            compose.onNodeWithText("Артисты").performClick()
            compose.onNodeWithText("PHARAOH").performClick()
            compose.runOnIdle { assertEquals(tracks, chosen) }
            compose.onNodeWithText("Guest").performClick()
            compose.runOnIdle { assertEquals(tracks.drop(1), chosen) }
        } finally {
            val store = ViewModelStore()
            store.put("fixture", vm)
            store.clear()
        }
    }

    @Test
    fun artistAlbumsOpenTheCorrectAlbumInTrackOrder() {
        val tracks =
            listOf(
                Track(1, "", "One", "Artist", "First", 1, 60000, trackNumber = 2),
                Track(2, "", "Two", "Artist feat. Guest", "First", 1, 60000, trackNumber = 1),
                Track(3, "", "Three", "Artist", "Second", 2, 60000),
            )
        var selected = emptyList<Track>()
        compose.setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                ArtistAlbums(tracks) { _, rows -> selected = rows }
            }
        }
        compose.onNodeWithTag("artist-albums").assertIsDisplayed()
        compose.onNodeWithText("First").performClick()
        compose.runOnIdle { assertEquals(listOf(2L, 1L), selected.map { it.id }) }
        compose.onNodeWithText("Second").performClick()
        compose.runOnIdle { assertEquals(listOf(3L), selected.map { it.id }) }
    }

    @Test
    fun trackActionsUseRoundedSheetAtLargerTextSize() {
        val track =
            Track(1, "", "A very long track title that wraps safely", "Artist", "Album", 1, 60000)
        var favorite = false
        compose.setContent {
            LadTheme(Settings(theme = "dark", dynamicColor = false, animations = false)) {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, 1.3f)) {
                    Box(Modifier.width(320.dp)) {
                        TrackRow(track, false, {}, { favorite = true }, {})
                    }
                }
            }
        }
        compose.onNodeWithContentDescription(text(R.string.track_actions)).performClick()
        compose.onNodeWithText(text(R.string.track_information_and_tags)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.add_to_favourites)).assertIsDisplayed().performClick()
        compose.runOnIdle { assertTrue(favorite) }
    }
}
