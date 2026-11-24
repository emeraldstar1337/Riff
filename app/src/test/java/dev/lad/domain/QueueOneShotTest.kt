package dev.lad.domain

import org.junit.Assert.*
import org.junit.Test

class QueueOneShotTest {
    @Test
    fun oldSavedManualSelectionDoesNotKeepItsScheduledDuplicate() {
        val q = QueueManager()
        q.restore(
            QueueSnapshot(
                source = listOf(1, 2, 3),
                entries =
                    listOf(
                        QueueEntry(1, 0),
                        QueueEntry(3, 0, true),
                        QueueEntry(2, 0),
                        QueueEntry(3, 0),
                    ),
                index = 1,
            )
        )
        assertEquals(listOf(2L), q.state.entries.drop(q.state.index + 1).map { it.trackId })
    }

    @Test
    fun manualSelectionAlsoConsumesAPrefetchedNextCycleCopy() {
        val q = QueueManager()
        q.start(listOf(1, 2, 3), 3, false)
        q.appendCycleIfNeeded()
        q.insertNow(1)
        assertFalse(q.state.entries.drop(q.state.index + 1).any { it.trackId == 1L })
        assertEquals(listOf(2L, 3L), q.state.entries.drop(q.state.index + 1).map { it.trackId })
    }

    @Test
    fun oneShotOutsideSourceNeverLeaksIntoAutomaticRepeat() {
        val q = QueueManager()
        q.start(listOf(1, 2), 1, false)
        q.insertNow(9)
        q.next()
        q.removePlayedManualEntries()
        repeat(10) { assertNotEquals(9L, q.next()) }
        assertFalse(q.state.entries.any { it.trackId == 9L })
    }
}
