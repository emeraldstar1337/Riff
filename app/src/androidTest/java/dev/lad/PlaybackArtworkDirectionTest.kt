package dev.lad

import androidx.test.platform.app.InstrumentationRegistry
import dev.lad.data.Track
import dev.lad.domain.QueueEntry
import dev.lad.domain.QueueSnapshot
import dev.lad.playback.PlaybackRepository
import dev.lad.playback.PlaybackState
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackArtworkDirectionTest {
    @Test
    fun followsActualQueueMovementAndHandlesTheNextRepeatCycle() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val repository = PlaybackRepository(context)
        val tracks = (1L..3L).map { Track(it, "", "Track $it", "Artist", "Album", 1, 60000) }
        val entries = (0..1).flatMap { cycle -> tracks.map { QueueEntry(it.id, cycle) } }
        fun at(index: Int) =
            PlaybackState(
                current = tracks[index % 3],
                queue = QueueSnapshot(entries = entries, index = index),
            )
        repository.publish(at(0))
        repository.publish(at(1))
        assertEquals(1, repository.state.value.artworkDirection)
        repository.publish(at(0))
        assertEquals(-1, repository.state.value.artworkDirection)
        repository.publish(at(2))
        repository.publish(at(3))
        assertEquals(1, repository.state.value.artworkDirection)
        assertEquals(3, repository.state.value.queue.index)
        val duplicates =
            listOf(
                QueueEntry(1, 0, true),
                QueueEntry(2, 0, true),
                QueueEntry(3, 0, true),
                QueueEntry(1, 0, true),
                QueueEntry(2, 0, true),
            )
        repository.publish(
            PlaybackState(
                current = tracks[0],
                queue = QueueSnapshot(entries = duplicates, index = 3),
            )
        )
        repository.publish(
            PlaybackState(
                current = tracks[1],
                queue = QueueSnapshot(entries = duplicates, index = 4),
            )
        )
        assertEquals(1, repository.state.value.artworkDirection)
        repository.publish(
            PlaybackState(
                current = tracks[0],
                queue = QueueSnapshot(entries = duplicates, index = 3),
            )
        )
        assertEquals(-1, repository.state.value.artworkDirection)
    }
}
