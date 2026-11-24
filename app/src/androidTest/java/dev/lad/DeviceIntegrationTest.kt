package dev.lad

import android.content.ComponentName
import android.os.Bundle
import androidx.media3.session.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.lad.data.*
import dev.lad.domain.*
import dev.lad.statistics.RecapGenerator
import java.util.concurrent.TimeUnit
import kotlin.random.Random
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DeviceIntegrationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val app
        get() = instrumentation.targetContext.applicationContext as LadApplication

    @Test
    fun snapshotJsonRoundTripAndRealDataStore() =
        runBlocking<Unit> {
            val q = QueueManager(Random(7))
            q.start((1L..100L).toList(), 38, true, "Тест")
            repeat(19) { q.next() }
            q.insertNow(777)
            q.savePosition(34567)
            q.setRepeat(RepeatMode.ONE)
            assertEquals(q.state, SettingsStore.decodeQueue(SettingsStore.encodeQueue(q.state)))
            val before = app.settings.queue.first()
            app.settings.saveQueue(q.state)
            assertEquals(q.state, app.settings.queue.first())
            before?.let { app.settings.saveQueue(it) }
        }

    @Test
    fun roomPlaylistCrudAndFavorites() = runBlocking {
        val db = androidx.room.Room.inMemoryDatabaseBuilder(app, LadDatabase::class.java).build()
        try {
            val dao = db.music()
            val t = Track(999, "content://test/999", "Песня", "Артист", "Альбом", 1, 60000)
            dao.upsertTracks(listOf(t))
            dao.favorite(999, true)
            assertTrue(dao.track(999)!!.favorite)
            dao.savePlaylist(Playlist(1, "Тест", listOf(999)))
            dao.savePlaylist(Playlist(1, "Переименован", listOf(999), "content://cover"))
            assertEquals("Переименован", dao.playlist(1)!!.name)
            assertEquals(listOf(999L), dao.playlist(1)!!.tracks)
            dao.deletePlaylist(1)
            assertNull(dao.playlist(1))
        } finally {
            db.close()
        }
    }

    @Test
    fun demoDoesNotWriteRealStatistics() = runBlocking {
        val before = app.music.dao.listens().first()
        assertEquals(
            10,
            RecapGenerator.slides(
                    RecapGenerator.demo(),
                    "ДЕМО",
                    true,
                    dev.lad.i18n.TextResources(dev.lad.i18n.AppLanguage.context(app)),
                )
                .size,
        )
        assertEquals(before, app.music.dao.listens().first())
    }

    @Test
    fun localFilesPlayAndManualSelectionPreservesQueue() = runBlocking {
        val tracks =
            app.music.dao.availableTracks().ifEmpty {
                app.music.scan(Settings(minimumSeconds = 0))
                app.music.dao.availableTracks()
            }
        assertTrue("Audio fixtures were not scanned", tracks.size >= 4)
        var future: com.google.common.util.concurrent.ListenableFuture<MediaController>? = null
        instrumentation.runOnMainSync {
            future =
                MediaController.Builder(
                        app,
                        SessionToken(
                            app,
                            ComponentName(app, dev.lad.playback.PlaybackService::class.java),
                        ),
                    )
                    .buildAsync()
        }
        val controller = future!!.get(30, TimeUnit.SECONDS)
        val activity = androidx.test.core.app.ActivityScenario.launch(MainActivity::class.java)
        fun command(bundle: Bundle) {
            var result: com.google.common.util.concurrent.ListenableFuture<SessionResult>? = null
            instrumentation.runOnMainSync {
                result =
                    controller.sendCustomCommand(
                        SessionCommand("dev.lad.CONTROL", Bundle.EMPTY),
                        bundle,
                    )
            }
            assertEquals(0, result!!.get(30, TimeUnit.SECONDS).resultCode)
        }
        try {
            command(
                Bundle().apply {
                    putString("action", "start")
                    putLongArray("ids", tracks.take(4).map { it.id }.toLongArray())
                    putLong("selected", tracks[2].id)
                    putBoolean("shuffle", true)
                }
            )
            val initial = app.playback.state.value
            assertEquals(1, initial.queue.position)
            assertEquals(4, initial.queue.size)
            assertEquals(tracks[2].id, initial.current!!.id)
            val upcoming = initial.queue.entries.drop(initial.queue.index + 1).map { it.trackId }
            command(
                Bundle().apply {
                    putString("action", "insert")
                    putLong("id", tracks[3].id)
                }
            )
            val after = app.playback.state.value
            assertEquals(tracks[3].id, after.current!!.id)
            assertEquals(
                upcoming.filter { it != tracks[3].id },
                after.queue.entries.drop(after.queue.index + 1).map { it.trackId },
            )
            for (track in tracks.take(6)) {
                command(
                    Bundle().apply {
                        putString("action", "start")
                        putLongArray("ids", tracks.map { it.id }.toLongArray())
                        putLong("selected", track.id)
                        putBoolean("shuffle", false)
                    }
                )
                val deadline = System.currentTimeMillis() + 20_000
                while (
                    !app.playback.state.value.playing &&
                        app.playback.state.value.error == null &&
                        System.currentTimeMillis() < deadline
                ) Thread.sleep(100)
                assertNull("Decoder error for ${track.title}", app.playback.state.value.error)
                assertTrue("Track did not start: ${track.title}", app.playback.state.value.playing)
            }
            instrumentation.runOnMainSync { controller.pause() }
        } finally {
            instrumentation.runOnMainSync { controller.release() }
            activity.close()
        }
    }
}
