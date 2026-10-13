package dev.lad

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.lad.data.MediaTools
import dev.lad.data.Track
import java.io.File
import kotlinx.coroutines.runBlocking
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TagExportIntegrationTest {
    @Test
    fun correctedCopiesKeepOriginalFilesAndRoundTripTags() =
        runBlocking<Unit> {
            val app =
                InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
                    as LadApplication
            val tools = MediaTools(app)

            val tracks =
                listOf("01-first.mp3", "02-wave.flac", "03-wind.wav", "04-night.ogg", "06-rain.m4a")
                    .mapIndexed { index, name ->
                        val source = File("/sdcard/Music/LadTest", name)
                        assertTrue("Missing fixture: $name", source.isFile)
                        Track(
                            index.toLong(),
                            Uri.fromFile(source).toString(),
                            name,
                            "Riff",
                            "Тест",
                            0,
                            60000,
                            fileSize = source.length(),
                        )
                    }
            var checked = 0
            for (track in tracks) {
                val details = tools.details(track)
                val extension = details.filename.substringAfterLast('.', "").lowercase()
                if (extension !in listOf("mp3", "flac", "wav", "ogg", "m4a")) continue
                val originalSize = details.size
                val output = File.createTempFile("lad-test-export", ".$extension", app.cacheDir)
                try {
                    tools.exportTags(
                        track.copy(title = "Лад — тест тегов", year = 2026),
                        Uri.fromFile(output),
                    )
                    val tagged = AudioFileIO.read(output)
                    assertEquals(
                        "Title roundtrip: $extension",
                        "Лад — тест тегов",
                        tagged.tag.getFirst(FieldKey.TITLE),
                    )
                    assertEquals("2026", tagged.tag.getFirst(FieldKey.YEAR))
                    assertEquals(originalSize, tools.details(track).size)
                    checked++
                } finally {
                    output.delete()
                }
            }
            assertEquals("Install the generated six-format fixtures", 5, checked)
        }
}
