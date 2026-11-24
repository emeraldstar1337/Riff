package dev.lad

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.platform.app.InstrumentationRegistry
import dev.lad.data.Track
import dev.lad.ui.renderMusicCard
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class MusicCardShareTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun sharingCardsRenderLongCyrillicAndRtlAtExportResolution() = runBlocking {
        val track =
            Track(
                -9401,
                "",
                "Длинное название песни для музыкальной карточки",
                "Исполнитель & Guest",
                "Album",
                1,
                60000,
            )
        val story =
            renderMusicCard(
                context,
                track,
                "Музыка остаётся с тобой\nمرحبا بالعالم\nThis is a long lyric line",
                true,
                0xffadbbdd.toInt(),
            )
        assertEquals(1080, story.width)
        assertEquals(1920, story.height)
        val post = renderMusicCard(context, track, "", false, null)
        assertEquals(1350, post.height)
        File(context.cacheDir, "card-story.png").outputStream().use {
            story.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        File(context.cacheDir, "card-post.png").outputStream().use {
            post.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        story.recycle()
        post.recycle()
    }

    @Test
    fun cardUsesCoverPaletteAndRendersBothFormatsWithAndWithoutLyrics() = runBlocking {
        val cover = File(context.cacheDir, "card-palette-cover.png")
        Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888).apply {
            eraseColor(0xff26814a.toInt())
            cover.outputStream().use { compress(Bitmap.CompressFormat.PNG, 100, it) }
            recycle()
        }
        val track =
            Track(
                -9501,
                "",
                "Очень длинное название трека для проверки переносов",
                "Исполнитель & Guest",
                "Album",
                -9501,
                60000,
                customArtwork = cover.toURI().toString(),
            )
        for (story in listOf(true, false)) for (lyrics in
            listOf("", "Музыка остаётся с тобой и плавно следует за каждой строчкой")) {
            val card = renderMusicCard(context, track, lyrics, story, null, "flower")
            assertEquals(1080, card.width)
            assertEquals(if (story) 1920 else 1350, card.height)
            assertEquals(0xff26814a.toInt(), card.getPixel(540, card.height / 3))
            val background = card.getPixel(540, 50)
            assertTrue(Color.green(background) > Color.red(background))
            assertTrue(Color.green(background) > Color.blue(background))
            File(
                    context.cacheDir,
                    "card-${if (story) "story" else "post"}-${if (lyrics.isBlank()) "plain" else "lyrics"}.png",
                )
                .outputStream()
                .use { card.compress(Bitmap.CompressFormat.PNG, 100, it) }
            card.recycle()
        }
        cover.delete()
    }
}
