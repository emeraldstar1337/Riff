package dev.lad.domain

import kotlin.random.Random
import org.junit.Assert.*
import org.junit.Test

class QueueRandomnessTest {
    @Test
    fun everyRemainingPermutationIsReachableAndBalanced() {
        val queue = QueueManager(Random(74912))
        val counts = mutableMapOf<List<Long>, Int>()
        repeat(6000) {
            queue.start(listOf(1, 2, 3, 4), 1, true)
            val order = queue.state.entries.map { it.trackId }
            assertEquals(1L, order.first())
            assertEquals(setOf(1L, 2L, 3L, 4L), order.toSet())
            counts[order] = (counts[order] ?: 0) + 1
        }
        assertEquals(6, counts.size)
        assertTrue(counts.values.all { it in 850..1150 })
        assertTrue(counts.keys.any { it[1] == 2L && it[2] == 3L })
    }

    @Test
    fun repeatBoundaryIsBalancedAmongEligibleTracks() {
        val queue = QueueManager(Random(12334))
        val counts = mutableMapOf<Long, Int>()
        repeat(6000) {
            queue.restore(
                QueueSnapshot(
                    source = listOf(1, 2, 3),
                    entries = listOf(QueueEntry(1, 0), QueueEntry(2, 0), QueueEntry(3, 0)),
                    index = 2,
                    shuffle = true,
                )
            )
            val next = queue.appendCycleIfNeeded()
            assertEquals(3, next.map { it.trackId }.distinct().size)
            assertNotEquals(3L, next.first().trackId)
            val id = next.first().trackId
            counts[id] = (counts[id] ?: 0) + 1
        }
        assertEquals(setOf(1L, 2L), counts.keys)
        assertTrue(counts.values.all { it in 2800..3200 })
    }
}
