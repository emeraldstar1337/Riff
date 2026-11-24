package dev.lad

import androidx.test.platform.app.InstrumentationRegistry
import dev.lad.data.Listen
import dev.lad.data.LyricsRecord
import dev.lad.data.Playlist
import dev.lad.domain.QueueEntry
import dev.lad.domain.QueueSnapshot
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class UpgradeSeedTest {
    @Test
    fun seedVersion08Data() = runBlocking {
        val app =
            InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
                as LadApplication
        val tracks = app.music.dao.availableTracks()
        assertTrue(tracks.size >= 3)
        val first = tracks.first()
        app.music.dao.favorite(first.id, true)
        app.music.dao.savePlaylist(
            Playlist(name = "Upgrade preservation", tracks = tracks.take(3).map { it.id })
        )
        app.music.dao.saveLyrics(
            LyricsRecord(
                first.id,
                "[00:00.00]Saved before the update\n[00:05.00]Lyrics are preserved",
                "Upgrade test",
            )
        )
        app.music.dao.saveListen(
            Listen(
                "upgrade-preservation",
                first.id,
                first.title,
                first.artist,
                first.album,
                first.genre,
                first.artwork,
                System.currentTimeMillis(),
                31000,
                true,
            )
        )
        app.settings.saveQueue(
            QueueSnapshot(
                source = tracks.take(3).map { it.id },
                entries = tracks.take(3).map { QueueEntry(it.id, 0) },
                playbackMs = 4567,
            ),
            emptyList(),
        )
    }
}
