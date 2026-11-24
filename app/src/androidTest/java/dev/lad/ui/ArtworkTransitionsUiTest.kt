package dev.lad.ui

import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import coil.ImageLoader
import coil.decode.DataSource
import coil.intercept.Interceptor
import coil.request.SuccessResult
import dev.lad.data.Settings
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ArtworkTransitionsUiTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun allArtworkTransitionsFinishAndKeepControlsUsable() {
        val settings = mutableStateOf(Settings(animations = true))
        val image = mutableStateOf<String?>("first")
        var clicked = false
        compose.mainClock.autoAdvance = false
        compose.setContent {
            LadTheme(settings.value) {
                Column {
                    ArtworkMotion(image.value, Modifier.size(160.dp)) { Text(it.orEmpty()) }
                    Button({ clicked = true }) { Text("Control") }
                }
            }
        }
        listOf("fade", "slide", "zoom", "cards", "morph", "none").forEachIndexed { index, style ->
            compose.runOnIdle {
                settings.value = settings.value.copy(coverTransition = style)
                image.value = "cover-$index"
            }
            compose.waitUntil(10000) {
                compose.mainClock.advanceTimeBy(32)
                compose.onAllNodesWithText("cover-$index").fetchSemanticsNodes().isNotEmpty()
            }
            compose.mainClock.advanceTimeBy(1200)
            compose.waitForIdle()
            compose.onNodeWithText("cover-$index").assertIsDisplayed()
        }
        compose.onNodeWithText("Control").performClick()
        compose.runOnIdle { assertTrue(clicked) }
    }

    @Test
    fun retainsOldArtworkDuringLoadingAndBlendsWithoutBlackFrame() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val gate = CompletableDeferred<Unit>()
        val requested = AtomicBoolean(false)
        val firstReady = AtomicBoolean(false)
        val image = mutableStateOf<String?>("red")
        val loader =
            ImageLoader.Builder(context)
                .components {
                    add(
                        Interceptor { chain ->
                            val blue = chain.request.data == "blue"
                            if (blue) {
                                requested.set(true)
                                gate.await()
                            } else firstReady.set(true)
                            val bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
                            bitmap.eraseColor(
                                if (blue) android.graphics.Color.BLUE
                                else android.graphics.Color.RED
                            )
                            SuccessResult(
                                BitmapDrawable(context.resources, bitmap),
                                chain.request,
                                DataSource.MEMORY,
                            )
                        }
                    )
                }
                .build()
        compose.mainClock.autoAdvance = false
        compose.setContent {
            LadTheme(Settings(theme = "dark", dynamicColor = false, coverShape = "rounded")) {
                ArtworkMotion(
                    image.value,
                    Modifier.size(160.dp).testTag("cover"),
                    imageLoader = loader,
                ) {
                    Box(Modifier.fillMaxSize().testTag("cover-$it")) {
                        Artwork(it, "Cover", Modifier.fillMaxSize())
                    }
                }
            }
        }
        try {
            compose.waitUntil(10000) { firstReady.get() }
            compose.mainClock.advanceTimeBy(1000)
            compose.waitUntil(10000) {
                compose.mainClock.advanceTimeBy(16)
                compose.onNodeWithTag("cover").captureToImage().toPixelMap()[80, 80].red > .9f
            }
            compose.runOnIdle { image.value = "blue" }
            compose.mainClock.advanceTimeBy(32)
            compose.waitUntil(10000) { requested.get() }
            val held = compose.onNodeWithTag("cover").captureToImage().toPixelMap()[80, 80]
            assertTrue(held.red > .9f)
            gate.complete(Unit)
            compose.waitUntil(10000) {
                compose.mainClock.advanceTimeBy(16)
                compose.onAllNodesWithTag("cover-blue").fetchSemanticsNodes().isNotEmpty()
            }
            compose.mainClock.advanceTimeBy(160)
            val blended = compose.onNodeWithTag("cover").captureToImage().toPixelMap()[80, 80]
            assertTrue(blended.red > .1f && blended.blue > .1f)
            assertTrue(blended.red + blended.blue > .9f)
            compose.mainClock.advanceTimeBy(800)
            val final = compose.onNodeWithTag("cover").captureToImage().toPixelMap()[80, 80]
            assertTrue(final.blue > .9f)
        } finally {
            gate.complete(Unit)
            loader.shutdown()
        }
    }

    @Test
    fun cardsFollowNextAndPreviousDirections() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val image = mutableStateOf<String?>("red")
        val direction = mutableIntStateOf(1)
        val loader =
            ImageLoader.Builder(context)
                .components {
                    add(
                        Interceptor { chain ->
                            val bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
                            bitmap.eraseColor(
                                if (chain.request.data == "blue") android.graphics.Color.BLUE
                                else android.graphics.Color.RED
                            )
                            SuccessResult(
                                BitmapDrawable(context.resources, bitmap),
                                chain.request,
                                DataSource.MEMORY,
                            )
                        }
                    )
                }
                .build()
        compose.mainClock.autoAdvance = false
        compose.setContent {
            LadTheme(
                Settings(
                    theme = "dark",
                    dynamicColor = false,
                    coverShape = "rounded",
                    coverTransition = "cards",
                )
            ) {
                ArtworkMotion(
                    image.value,
                    Modifier.size(160.dp).testTag("slide"),
                    direction.intValue,
                    loader,
                ) {
                    Box(Modifier.fillMaxSize().testTag("slide-$it")) {
                        Artwork(it, "Cover", Modifier.fillMaxSize())
                    }
                }
            }
        }
        fun awaitImage(uri: String) {
            compose.waitUntil(10000) {
                compose.mainClock.advanceTimeBy(16)
                compose.onAllNodesWithTag("slide-$uri").fetchSemanticsNodes().isNotEmpty()
            }
        }
        try {
            awaitImage("red")
            compose.mainClock.advanceTimeBy(800)
            compose.waitUntil(10000) {
                compose.mainClock.advanceTimeBy(16)
                compose.onNodeWithTag("slide").captureToImage().toPixelMap()[80, 80].red > .9f
            }
            compose.runOnIdle { image.value = "blue" }
            awaitImage("blue")
            compose.mainClock.advanceTimeBy(100)
            val next = compose.onNodeWithTag("slide").captureToImage().toPixelMap()
            assertTrue(next[12, 80].red > .9f)
            assertTrue(next[next.width - 12, 80].blue > .9f)
            compose.mainClock.advanceTimeBy(800)
            compose.runOnIdle {
                direction.intValue = -1
                image.value = "red"
            }
            awaitImage("red")
            compose.mainClock.advanceTimeBy(100)
            val previous = compose.onNodeWithTag("slide").captureToImage().toPixelMap()
            assertTrue(previous[12, 80].red > .9f)
            assertTrue(previous[previous.width - 12, 80].blue > .9f)
            compose.mainClock.advanceTimeBy(800)
        } finally {
            loader.shutdown()
        }
    }
}
