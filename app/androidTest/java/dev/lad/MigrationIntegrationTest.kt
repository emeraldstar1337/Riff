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
class MigrationIntegrationTest {
    @Test
    fun versionOneUpgradePreservesFavoritesPlaylistsAndHistory() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "lad-migration-test.db"
        context.deleteDatabase(name)
        val legacy = context.openOrCreateDatabase(name, 0, null)
        val statements =
            listOf(
                "CREATE TABLE IF NOT EXISTS `tracks` (`id` INTEGER NOT NULL, `uri` TEXT NOT NULL, `title` TEXT NOT NULL, `artist` TEXT NOT NULL, `album` TEXT NOT NULL, `albumId` INTEGER NOT NULL, `durationMs` INTEGER NOT NULL, `trackNumber` INTEGER NOT NULL, `year` INTEGER NOT NULL, `genre` TEXT NOT NULL, `path` TEXT NOT NULL, `addedAt` INTEGER NOT NULL, `favorite` INTEGER NOT NULL, `available` INTEGER NOT NULL, PRIMARY KEY(`id`))",
                "CREATE TABLE IF NOT EXISTS `playlists` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `tracks` TEXT NOT NULL, `cover` TEXT)",
                "CREATE TABLE IF NOT EXISTS `listens` (`sessionId` TEXT NOT NULL, `trackId` INTEGER NOT NULL, `title` TEXT NOT NULL, `artist` TEXT NOT NULL, `album` TEXT NOT NULL, `genre` TEXT NOT NULL, `artwork` TEXT NOT NULL, `startedAt` INTEGER NOT NULL, `listenedMs` INTEGER NOT NULL, `counted` INTEGER NOT NULL, PRIMARY KEY(`sessionId`))",
                "CREATE INDEX IF NOT EXISTS `index_listens_startedAt` ON `listens` (`startedAt`)",
                "CREATE INDEX IF NOT EXISTS `index_listens_trackId` ON `listens` (`trackId`)",
            )
        statements.forEach { legacy.execSQL(it) }
        legacy.execSQL(
            "INSERT INTO tracks VALUES(123,'content://test/123','Song','Artist','Album',7,60000,1,2020,'Rock','Music/',0,1,1)"
        )
        legacy.execSQL("INSERT INTO playlists VALUES(1,'Mine','123',NULL)")
        legacy.execSQL(
            "INSERT INTO listens VALUES('session',123,'Song','Artist','Album','Rock','',1000,40000,1)"
        )
        legacy.version = 1
        legacy.close()
        val db =
            Room.databaseBuilder(context, LadDatabase::class.java, name)
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build()
        try {
            val dao = db.music()
            assertTrue(dao.track(123)!!.favorite)
            assertEquals(listOf(123L), dao.playlist(1)!!.tracks)
            dao.saveEdit(TrackEdit(123, "New", "Artist", "Album", 2020, "Rock"))
            assertEquals("New", dao.edits().single().title)
            dao.saveLyrics(LyricsRecord(123, "[00:01]Hello"))
            assertEquals("[00:01]Hello", dao.lyrics(123)!!.text)
            dao.saveRun(PlaybackRun("run", 1000, 40000))
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
    }
}
