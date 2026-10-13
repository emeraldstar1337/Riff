package dev.lad

import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import dev.lad.data.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class ArtworkIntegrationTest {
    @Test
    fun embeddedArtStaysPerFileEvenWhenAlbumMatchesAndRefreshInvalidatesCache() = runBlocking {
        val app =
            InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
                as LadApplication
        app.music.scan(Settings(minimumSeconds = 0), true)
        val tracks = app.music.dao.availableTracks()
        val red = tracks.first { it.title == "Lad Test 1" }
        val blue = tracks.first { it.title == "Lad Test 2" }
        assertEquals(red.albumId, blue.albumId)
        val repository = ArtworkRepository(app)
        fun pixel(file: java.io.File?): Int {
            assertNotNull(file)
            val bitmap = BitmapFactory.decodeFile(file!!.absolutePath)
            val color = bitmap.getPixel(bitmap.width / 2, bitmap.height / 2)
            bitmap.recycle()
            return color
        }
        val r = pixel(repository.resolve(Uri.parse(red.artwork)))
        val b = pixel(repository.resolve(Uri.parse(blue.artwork)))
        assertTrue(Color.red(r) > Color.blue(r) + 100)
        assertTrue(Color.blue(b) > Color.red(b) + 100)
        val missing = tracks.first {
            it.mimeType.contains("wav", ignoreCase = true) || it.title == "03-wind"
        }
        assertNull(repository.resolve(Uri.parse(missing.artwork)))
        app.music.scan(Settings(minimumSeconds = 0), true)
        val refreshed = app.music.dao.track(red.id)!!
        assertNotEquals(red.artwork, refreshed.artwork)
        assertTrue(Color.red(pixel(repository.resolve(Uri.parse(refreshed.artwork)))) > 200)
    }
}
