package dev.lad

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import dev.lad.data.*
import dev.lad.domain.LyricLine
import dev.lad.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class Appearance08UiTest {
    @org.junit.Before
    fun useRussianInterface() {
        dev.lad.i18n.AppLanguage.set(
            androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext,
            "ru",
        )
    }

    @get:Rule val rule = createComposeRule()

    @Test
    fun lyricsPresetsReallyChangeRenderedFontAlignmentAndSpacing() {
        val settings = mutableStateOf(Settings(animations = false))
        rule.setContent {
            LadTheme(settings.value) {
                Column {
                    LyricLineText(
                        LyricLine(0, "Слова песни"),
                        true,
                        0,
                        {},
                        Modifier.testTag("sample"),
                    )
                }
            }
        }
        fun layout(): TextLayoutResult {
            val results = mutableListOf<TextLayoutResult>()
            rule.onNodeWithTag("sample").performSemanticsAction(
                SemanticsActions.GetTextLayoutResult
            ) {
                it(results)
            }
            return results.single()
        }
        rule.runOnIdle {
            settings.value =
                AppearancePresets.lyrics.first { it.id == "stage" }.apply(settings.value)
        }
        val stage = layout().layoutInput.style
        assertEquals(30.sp, stage.fontSize)
        assertEquals(TextAlign.Center, stage.textAlign)
        assertEquals(43.5.sp, stage.lineHeight)
        rule.runOnIdle {
            settings.value =
                AppearancePresets.lyrics.first { it.id == "compact" }.apply(settings.value)
        }
        val compact = layout().layoutInput.style
        assertEquals(18.sp, compact.fontSize)
        assertEquals(TextAlign.Start, compact.textAlign)
        assertTrue(compact.lineHeight.value < stage.lineHeight.value)
    }

    @Test
    fun miniPreviewWorksWithoutPlayingAndShowsSelectedStyle() {
        val settings = mutableStateOf(Settings())
        rule.setContent { LadTheme(settings.value) { MiniStylePreview(settings.value) } }
        rule.onNodeWithText("Твой любимый трек").assertIsDisplayed()
        rule.runOnIdle { settings.value = settings.value.copy(miniStyle = "expanded") }
        rule.onNodeWithText("1:24 / 3:42").assertIsDisplayed()
        rule.runOnIdle { settings.value = settings.value.copy(miniStyle = "slim") }
        rule.onNodeWithTag("artwork-placeholder").assertDoesNotExist()
    }

    @Test
    fun navigationPreviewTracksOrderAndEnabledTabs() {
        val selected = mutableStateOf(listOf("home", "library", "stats"))
        rule.setContent {
            LadTheme(Settings()) {
                NavigationPreview(
                    selected.value,
                    mapOf("home" to "Главная", "library" to "Музыка", "stats" to "История"),
                )
            }
        }
        rule.onNodeWithText("Главная → Музыка → История").assertIsDisplayed()
        rule.runOnIdle { selected.value = listOf("stats", "library") }
        rule.onNodeWithText("История → Музыка").assertIsDisplayed()
    }
}
