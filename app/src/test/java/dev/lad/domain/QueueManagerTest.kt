package dev.lad.domain

import kotlin.random.Random
import org.junit.Assert.*
import org.junit.Test

class QueueManagerTest {
    @Test
    fun selected38StartsAtOne() {
        val q = QueueManager(Random(7))
        q.start((1L..100L).toList(), 38, true)
        assertEquals(1, q.state.position)
        assertEquals(100, q.state.size)
        assertEquals(38L, q.state.current!!.trackId)
        assertEquals(100, q.state.entries.map { it.trackId }.distinct().size)
    }

    @Test
    fun manualSelectionPreservesFutureAndRepeatedSelection() {
        val q = QueueManager()
        q.start(listOf(1, 2, 3, 4), 1, false)
        q.insertNow(9)
        assertEquals(listOf(1L, 9L, 2L, 3L, 4L), q.state.entries.map { it.trackId })
        q.insertNow(8)
        assertEquals(2L, q.next())
        assertEquals(listOf(1L, 9L, 8L, 2L, 3L, 4L), q.state.entries.map { it.trackId })
    }

    @Test
    fun cyclesAreCompleteAndDoNotRepeatAtBoundary() {
        val q = QueueManager(Random(42))
        q.start((1L..100L).toList(), 38, true)
        repeat(99) { assertNotNull(q.next()) }
        val last = q.state.current!!.trackId
        assertNotNull(q.next())
        assertEquals(1, q.state.position)
        assertEquals(100, q.state.size)
        assertNotEquals(last, q.state.current!!.trackId)
        assertEquals(100, q.state.currentCycle.map { it.trackId }.distinct().size)
        repeat(299) { assertNotNull(q.next()) }
    }

    @Test
    fun restoresStateAndPlaybackPosition() {
        val q = QueueManager(Random(1))
        q.start((1L..100L).toList(), 38, true, "Плейлист")
        repeat(19) { q.next() }
        q.insertNow(777)
        q.savePosition(73_000)
        val restored = QueueManager()
        restored.restore(q.state)
        assertEquals(q.state, restored.state)
        assertEquals(q.next(), restored.next())
        assertEquals("Плейлист", restored.state.context)
    }

    @Test
    fun repeatOffActuallyStopsAndRepeatOneStays() {
        val q = QueueManager()
        q.start(listOf(1, 2), 1, false)
        q.setRepeat(RepeatMode.OFF)
        assertEquals(2L, q.next())
        assertNull(q.next())
        q.setRepeat(RepeatMode.ONE)
        assertEquals(2L, q.next())
    }

    @Test
    fun oneTrackCyclesWork() {
        val q = QueueManager()
        q.start(listOf(1), 1, true)
        repeat(8) { assertEquals(1L, q.next()) }
    }

    @Test
    fun manualChoiceConsumesPendingCopyWithoutReorderingOthers() {
        val q = QueueManager()
        q.start(listOf(1, 2, 3), 1, true)
        val future = q.state.entries.drop(1)
        q.insertNow(3)
        assertEquals(future.filter { it.trackId != 3L }, q.state.entries.drop(2))
        assertTrue(q.state.current!!.manual)
    }

    @Test
    fun completedManualSelectionsDisappearAndRemainingSourceContinues() {
        val q = QueueManager()
        q.start(listOf(1, 2, 3, 4), 1, false)
        q.insertNow(3)
        assertEquals(listOf(1L, 3L, 2L, 4L), q.state.entries.map { it.trackId })
        assertEquals(2L, q.next())
        assertEquals(listOf(1), q.removePlayedManualEntries())
        assertEquals(listOf(1L, 2L, 4L), q.state.entries.map { it.trackId })
        assertEquals(2L, q.state.current!!.trackId)
        q.insertNow(9)
        q.insertNow(8)
        q.removePlayedManualEntries()
        assertFalse(q.state.entries.any { it.trackId == 9L })
        assertEquals(listOf(4L), q.state.entries.drop(q.state.index + 1).map { it.trackId })
    }

    @Test
    fun toggleAndTrimRetainCurrent() {
        val q = QueueManager()
        q.start((1L..5).toList(), 1, false)
        repeat(16) { q.next() }
        val current = q.state.current
        q.trimHistory()
        assertEquals(current, q.state.current)
        q.toggleShuffle(true)
        assertEquals(current, q.state.current)
    }
}

class ListeningSessionTest {
    @Test
    fun listeningSplitsAtMidnightWithoutDoubleCounting() {
        val end = java.time.Instant.parse("2026-10-01T00:00:02Z").toEpochMilli()
        val s = ListeningSession(1, 60000, end - 4000, "a")
        s.accrue(4000, true, end, java.time.ZoneOffset.UTC)
        assertEquals(2, s.segments.size)
        assertEquals(2000L, s.segments[0].listenedMs)
        assertEquals(2000L, s.segments[1].listenedMs)
        assertEquals(4000L, s.listenedMs)
    }

    @Test
    fun pauseResumeDoesNotInflate() {
        val s = ListeningSession(1, 200_000, 0, "a")
        repeat(30) { s.accrue(1000, true) }
        repeat(60) { s.accrue(1000, false) }
        assertEquals(30_000, s.listenedMs)
        assertTrue(s.qualified)
    }

    @Test
    fun shortTracksUseHalfDuration() {
        val s = ListeningSession(1, 10_000, 0, "a")
        s.accrue(4000, true)
        assertFalse(s.qualified)
        s.accrue(1000, true)
        assertTrue(s.qualified)
    }
}
