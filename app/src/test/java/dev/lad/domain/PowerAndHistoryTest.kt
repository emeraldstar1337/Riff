package dev.lad.domain

import org.junit.Assert.*
import org.junit.Test

class PowerAndHistoryTest {
    @Test
    fun pausedWithoutTimerNeverPolls() {
        assertEquals(
            Long.MAX_VALUE,
            PlaybackPowerPolicy.waitMs(false, true, true, false, false, 90_000, null, 0),
        )
        assertEquals(
            5000,
            PlaybackPowerPolicy.waitMs(false, false, false, false, false, 0, 5000, null),
        )
    }

    @Test
    fun hiddenPlaybackWaitsForCheckpointOrFadePreparation() {
        assertEquals(
            30_000,
            PlaybackPowerPolicy.waitMs(true, false, false, false, false, 0, null, 180_000),
        )
        assertEquals(
            1500,
            PlaybackPowerPolicy.waitMs(true, false, false, false, false, 0, null, 1500),
        )
        assertEquals(50, PlaybackPowerPolicy.waitMs(true, false, false, false, true, 0, null, null))
    }

    @Test
    fun onlyVisibleSyncedLyricsNeedFastClock() {
        assertEquals(100, PlaybackPowerPolicy.waitMs(true, true, true, false, false, 0, null, null))
        assertEquals(1000, PlaybackPowerPolicy.waitMs(true, true, true, true, false, 0, null, null))
    }

    @Test
    fun incomingDecoderDoesNotRunForWholeTrackOrPause() {
        val fade = PlaybackPowerPolicy.fadeWindow(3, 180_000, 1f)
        assertFalse(PlaybackPowerPolicy.prepareIncoming(true, 170_000, fade, 1f))
        assertFalse(PlaybackPowerPolicy.prepareIncoming(false, 2000, fade, 1f))
        assertTrue(PlaybackPowerPolicy.prepareIncoming(true, 5400, fade, 1f))
    }

    @Test
    fun offloadNeverBypassesUserSoundEffects() {
        assertTrue(PlaybackPowerPolicy.offload(SoundProfile(), false, 0, 1f, false))
        listOf(
                SoundProfile(volume = 0.5f),
                SoundProfile(balance = 0.4f),
                SoundProfile(true, listOf(2f, 0f, 0f, 0f, 0f)),
            )
            .forEach { assertFalse(PlaybackPowerPolicy.offload(it, false, 0, 1f, false)) }
        assertFalse(PlaybackPowerPolicy.offload(SoundProfile(), true, 0, 1f, false))
        assertFalse(PlaybackPowerPolicy.offload(SoundProfile(), false, 3, 1f, false))
    }

    @Test
    fun undoRestoresQueuePositionAndKeepsEarlierEdits() {
        val journal = QueueHistory()
        val first =
            QueueSnapshot(entries = listOf(QueueEntry(1, 0), QueueEntry(2, 0)), playbackMs = 12345)
        journal.record("remove", first, 100)
        journal.record("move", first.copy(playbackMs = 23456), 100)
        assertEquals(2, journal.revisions.size)
        assertEquals(23456L, journal.restore(journal.revisions.last().id)?.playbackMs)
        assertEquals(first, journal.restore(journal.revisions.last().id))
        assertTrue(journal.revisions.isEmpty())
    }

    @Test
    fun historyIsBoundedAndRejectsMissingRevision() {
        val journal = QueueHistory()
        val s = QueueSnapshot(entries = listOf(QueueEntry(1, 0)))
        repeat(30) { journal.record("edit", s, 1) }
        assertEquals(12, journal.revisions.size)
        assertNull(journal.restore(-1))
        assertEquals(12, journal.revisions.size)
    }

    @Test
    fun sparseBackgroundCheckpointsCountWholeInterval() {
        val s = ListeningSession(1, 180_000, 0, "test")
        s.accrue(30_000, true, 30_000, java.time.ZoneOffset.UTC)
        s.accrue(30_000, false, 60_000, java.time.ZoneOffset.UTC)
        assertEquals(30_000, s.listenedMs)
        assertTrue(s.qualified)
    }
}
