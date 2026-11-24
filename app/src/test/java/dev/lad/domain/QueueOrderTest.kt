package dev.lad.domain

import kotlin.random.Random
import org.junit.Assert.*
import org.junit.Test

class QueueOrderTest {
    @Test
    fun disablingShuffleRestoresWholeSourceAroundCurrentTrack() {
        val source = (1L..20L).toList()
        val queue = QueueManager(Random(8))
        queue.start(source, 8, true)
        repeat(5) { queue.next() }
        val current = queue.state.current
        queue.savePosition(17000)
        queue.toggleShuffle(false)
        assertEquals(source, queue.state.entries.map { it.trackId })
        assertEquals(current, queue.state.current)
        assertEquals(17000, queue.state.playbackMs)
        assertEquals(current!!.trackId.toInt(), queue.state.position)
        if (current.trackId < 20) assertEquals(current.trackId + 1, queue.next())
    }

    @Test
    fun repeatedTogglesPreserveCurrentAndReturnToSourceOrder() {
        val queue = QueueManager(Random(13))
        val source = (1L..50L).toList()
        queue.start(source, 23, false)
        repeat(15) {
            queue.toggleShuffle(true)
            repeat(2) { queue.next() }
            val current = queue.state.current
            queue.toggleShuffle(false)
            assertEquals(current, queue.state.current)
            assertEquals(source, queue.state.currentCycle.map { it.trackId })
        }
    }

    @Test
    fun disablingInLaterCycleDoesNotLoseHistoryOrRepeatCurrent() {
        val queue = QueueManager(Random(3))
        queue.start(listOf(1, 2, 3), 2, true)
        repeat(4) { queue.next() }
        val current = queue.state.current
        val history = queue.state.entries.takeWhile { it.cycle < current!!.cycle }
        queue.toggleShuffle(false)
        assertEquals(current, queue.state.current)
        assertEquals(history, queue.state.entries.take(history.size))
        assertEquals(listOf(1L, 2L, 3L), queue.state.currentCycle.map { it.trackId })
    }

    @Test
    fun manuallySelectedExternalTrackSurvivesAndQueueEditsAreNotUndone() {
        val queue = QueueManager(Random(5))
        queue.start((1L..8L).toList(), 1, true)
        val removed = queue.state.entries[2].trackId
        queue.removeUpcoming(2)
        queue.insertNow(99)
        queue.toggleShuffle(false)
        assertEquals(99L, queue.state.current!!.trackId)
        assertTrue(queue.state.current!!.manual)
        assertFalse(queue.state.entries.any { it.trackId == removed })
        assertEquals(8, queue.state.entries.size)
        assertEquals(
            (1L..8L).filter { it != removed },
            queue.state.entries.filterNot { it.manual }.map { it.trackId },
        )
    }

    @Test
    fun singleTrackAndEmptyQueueTogglesAreSafe() {
        val queue = QueueManager()
        queue.toggleShuffle(true)
        queue.toggleShuffle(false)
        assertTrue(queue.state.entries.isEmpty())
        queue.start(listOf(1), 1, true)
        queue.toggleShuffle(false)
        assertEquals(1L, queue.state.current!!.trackId)
        assertEquals(1, queue.state.entries.size)
    }

    @Test
    fun disablingShuffleNormalizesManualSourceTrackBeforeSkipping() {
        val queue = QueueManager(Random(4))
        val ids = (1L..8L).toList()
        queue.start(ids, 1, true)
        queue.insertNow(2)
        queue.toggleShuffle(false)
        assertEquals(ids, queue.state.currentCycle.map { it.trackId })
        assertEquals(2, queue.state.position)
        assertFalse(queue.state.current!!.manual)
        assertEquals(3L, queue.next())
        queue.removePlayedManualEntries()
        assertEquals(3, queue.state.position)
        assertEquals(4L, queue.next())
        assertEquals(4, queue.state.position)
    }
}
