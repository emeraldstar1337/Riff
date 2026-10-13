package dev.lad

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.lad.data.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LyricsRepositoryIntegrationTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val synced =
        """{"trackName":"Песня","artistName":"Артист","albumName":"Альбом","duration":180,"syncedLyrics":"[00:02.00]Тестовая строка"}"""

    @Test
    fun searchFiltersPlainTextAndCachesProviderResponses() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, LadDatabase::class.java).build()
        try {
            var calls = 0
            val repo =
                LyricsRepository(context, db.music()) { url ->
                    assertTrue(url.contains("track_name="))
                    calls++
                    LyricsHttpReply(
                        200,
                        """[{"trackName":"Песня","artistName":"Артист","duration":180,"plainLyrics":"Обычный текст","syncedLyrics":null},$synced]""",
                    )
                }
            val result = repo.search("Песня", "Артист", 180000)
            assertEquals(1, result.size)
            assertTrue(result.single().synced)
            assertEquals(result, repo.search("Песня", "Артист", 180000))
            assertEquals(1, calls)
            repo.save(999, result.single().text, "LRCLIB")
            assertEquals(
                result.single().text,
                repo
                    .load(Track(999, "content://test", "Песня", "Артист", "Альбом", 7, 180000))!!
                    .text,
            )
        } finally {
            db.close()
        }
    }

    @Test
    fun missingSignatureFallsBackToSyncedSearch() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, LadDatabase::class.java).build()
        try {
            val urls = mutableListOf<String>()
            val repo =
                LyricsRepository(context, db.music()) { url ->
                    urls += url
                    if (url.contains("/get?")) LyricsHttpReply(404, "")
                    else LyricsHttpReply(200, "[$synced]")
                }
            val track = Track(999, "content://test", "Песня", "Артист", "Альбом", 7, 180000)
            assertNotNull(repo.findSynced(track))
            assertNotNull(repo.findSynced(track))
            assertEquals(2, urls.size)
            assertTrue(urls.first().contains("duration=180.0"))
        } finally {
            db.close()
        }
    }

    @Test
    fun retryAfterStopsRepeatedNetworkCalls() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, LadDatabase::class.java).build()
        try {
            var calls = 0
            val repo =
                LyricsRepository(context, db.music()) {
                    calls++
                    LyricsHttpReply(429, "", 60)
                }
            assertTrue(runCatching { repo.search("Песня", "Артист") }.isFailure)
            assertTrue(runCatching { repo.search("Другая песня", "Артист") }.isFailure)
            assertEquals(1, calls)
        } finally {
            db.close()
        }
    }
}
