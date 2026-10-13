package dev.lad

import android.os.ParcelFileDescriptor
import android.view.accessibility.AccessibilityNodeInfo
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.lad.data.LyricsRecord
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestName
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NavigationLyricsUiTest {
    @org.junit.Before
    fun useRussianInterface() {
        dev.lad.i18n.AppLanguage.set(
            androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext,
            "ru",
        )
    }

    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    @get:Rule val testName = TestName()
    private val app
        get() =
            InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
                as LadApplication

    @Before
    fun dismissSoftwareEmulatorSystemUiDialog() {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val root = automation.rootInActiveWindow ?: return

        if (root.findAccessibilityNodeInfosByText("System UI isn't responding").isNotEmpty()) {
            var button = root.findAccessibilityNodeInfosByText("Wait").firstOrNull()
            while (button != null && !button.isClickable) button = button.parent
            button?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        }
    }

    @Test
    fun languageAndWaveHandleCanBeChangedInSettings() {
        rule.onNodeWithContentDescription("Настройки").performClick()
        rule.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Язык"))
        rule.onNodeWithText("Язык").performClick()
        rule.onNodeWithText("English").performClick()
        rule.waitUntil(10000) {
            rule.onAllNodesWithText("App language").fetchSemanticsNodes().isNotEmpty()
        }
        rule.waitUntil(10000) {
            rule.activity.resources.configuration.locales[0].language == "en"
        }
        rule.onNodeWithText("Device language").assertIsDisplayed()
        rule.onNodeWithContentDescription("Back").performClick()
        rule.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Player"))
        rule.onNodeWithText("Player").performClick()
        rule.onNodeWithText("Wave · dot").performClick()
        rule.waitUntil(10000) {
            runBlocking { app.settings.settings.first().seekStyle == "wave_dot" }
        }
        rule.onNodeWithText("Wave · dot").assertIsSelected()
        rule.onNodeWithText("Wave · bar").performClick()
        rule.waitUntil(10000) {
            runBlocking { app.settings.settings.first().seekStyle == "wave_bar" }
        }
        rule.onNodeWithText("Wave · bar").assertIsSelected()
        rule.onNodeWithText("Seek bar").assertIsDisplayed()
        rule.onNodeWithText("Line · dot").performClick()
        rule.waitUntil(10000) {
            runBlocking { app.settings.settings.first().seekStyle == "line_dot" }
        }
        rule.onNodeWithText("Line · dot").assertIsSelected()
        rule.onNodeWithText("Line · bar").performClick()
        rule.waitUntil(10000) {
            runBlocking { app.settings.settings.first().seekStyle == "line_bar" }
        }
        rule.onNodeWithText("Line · bar").assertIsSelected()
        rule.onNodeWithText("Wave · bar").performClick()
        rule.waitUntil(10000) {
            runBlocking { app.settings.settings.first().seekStyle == "wave_bar" }
        }
        rule.onNodeWithText("Wave · bar").assertIsSelected()
    }

    @Test
    fun settingsBackReturnsToOverviewForBothButtons() {
        rule.onNodeWithContentDescription("Настройки").performClick()
        rule.onNodeWithText("Тема и цвета").performClick()
        rule.onNodeWithText("Monet").assertIsDisplayed()
        pressSystemBack()
        rule.waitUntil(10000) {
            rule.onAllNodesWithText("Найти настройку…").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithText("Тема и цвета").assertIsDisplayed()
        rule.onNodeWithText("Настройки").assertIsDisplayed()
        rule.waitUntil(10000) {
            runCatching { rule.onNodeWithText("Фигуры и движение").assertIsDisplayed() }.isSuccess
        }
        rule.onNodeWithText("Фигуры и движение").performScrollTo().performClick()
        rule.onNodeWithContentDescription("Назад").performClick()
        rule.onNodeWithText("Тема и цвета").assertIsDisplayed()
        rule.onNodeWithContentDescription("Назад").performClick()
        rule.onNodeWithText("Riff").assertIsDisplayed()
    }

    @Test
    fun syncedLyricsReplaceArtworkAndSeekWithoutClosingPlayer() {
        val track = runBlocking {
            app.music.scan(app.settings.settings.first())
            app.music.dao.availableTracks().first { it.title.replace(" ", "") == "LadTest1" }
        }
        runBlocking {
            app.music.dao.saveLyrics(
                LyricsRecord(
                    track.id,
                    "[00:02.00]ТЕСТ: первая строка\n[00:12.00]ТЕСТ: вторая строка",
                    "UI тест LRC",
                )
            )
        }
        rule.runOnUiThread { app.playback.start(listOf(track.id), track.id, false, "UI тест") }
        rule.waitUntil(120000) {
            app.playback.state.value.current?.id == track.id && app.playback.state.value.playing
        }
        rule.runOnUiThread { app.playback.toggle() }
        rule.waitUntil(30000) { !app.playback.state.value.playing }
        rule.onNodeWithTag("mini-player").performClick()
        rule.onNodeWithTag("player-artwork").assertIsDisplayed()
        rule.onNodeWithText("Текст", substring = false).performClick()
        rule.onNodeWithTag("player-lyrics").assertIsDisplayed()
        rule.onNodeWithTag("player-artwork").assertDoesNotExist()
        rule.onNodeWithText("ТЕСТ: вторая строка").performClick()
        rule.waitUntil(10000) { app.playback.state.value.positionMs in 12000..12500 }
        rule.waitUntil(10000) { app.playback.lyricsVisible.value }
        runCatching { rule.onNodeWithText("Звук", substring = false).performScrollTo() }
        rule.onNodeWithText("Звук", substring = false).assertIsDisplayed().performClick()
        rule.onNodeWithTag("audio-sheet").assertIsDisplayed()
        rule.waitUntil(10000) {
            !app.playback.lyricsVisible.value && !app.playback.meterVisible.value
        }
        rule.onNodeWithContentDescription("Закрыть Звук по-твоему").performClick()
        rule.waitUntil(10000) { app.playback.lyricsVisible.value }
        runCatching { rule.onNodeWithText("Очередь", substring = false).performScrollTo() }
        rule.onNodeWithText("Очередь", substring = false).assertIsDisplayed().performClick()
        rule.waitUntil(10000) { !app.playback.lyricsVisible.value }
        rule.onNodeWithContentDescription("Закрыть Очередь").performClick()
        rule.waitUntil(10000) { app.playback.lyricsVisible.value }
        runCatching { rule.onNodeWithTag("artwork-tab").performScrollTo() }
        rule.onNodeWithText("Обложка", substring = false).performClick()
        rule.waitUntil(10000) {
            runCatching { rule.onNodeWithTag("player-artwork").assertIsDisplayed() }.isSuccess
        }
        rule.onNodeWithTag("player-artwork").assertIsDisplayed()
        runCatching { rule.onNodeWithContentDescription("Свернуть").performScrollTo() }
        rule.onNodeWithContentDescription("Свернуть").assertIsDisplayed()
        pressSystemBack()
        rule.waitUntil(10000) {
            rule.onAllNodesWithTag("player-artwork").fetchSemanticsNodes().isEmpty()
        }
        rule.onNodeWithTag("mini-player").assertIsDisplayed()
    }

    private fun pressSystemBack() {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        ParcelFileDescriptor.AutoCloseInputStream(
                automation.executeShellCommand("input keyevent 4")
            )
            .use { it.readBytes() }
        rule.mainClock.advanceTimeBy(1000)
        rule.waitForIdle()
    }

    @After
    fun captureNativeScreen() {
        val file = java.io.File(app.getExternalFilesDir(null), "qa/${testName.methodName}.png")
        file.parentFile!!.mkdirs()
        InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()?.let { bitmap ->
            file.outputStream().use {
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
            bitmap.recycle()
        }
    }
}
