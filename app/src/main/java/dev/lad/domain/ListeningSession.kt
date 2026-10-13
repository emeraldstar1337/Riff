package dev.lad.domain

import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

data class ListeningSegment(val bucket: Long, val startedAt: Long, val listenedMs: Long)

class ListeningSession(
    val trackId: Long,
    val durationMs: Long,
    val startedAt: Long,
    val sessionId: String,
) {
    var listenedMs = 0L
        private set

    private val buckets = linkedMapOf<Long, ListeningSegment>()
    val segments: List<ListeningSegment>
        get() = buckets.values.toList()

    val thresholdMs: Long
        get() = minOf(30_000L, (durationMs * .5).toLong().coerceAtLeast(1000L))

    val qualified: Boolean
        get() = listenedMs >= thresholdMs

    fun accrue(
        elapsedMs: Long,
        isPlaying: Boolean,
        wallEndMs: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault(),
    ) {
        if (!isPlaying) return
        val elapsed = elapsedMs.coerceAtLeast(0)
        listenedMs += elapsed
        var cursor = wallEndMs - elapsed
        while (cursor < wallEndMs) {
            val bucket =
                Instant.ofEpochMilli(cursor)
                    .atZone(zone)
                    .truncatedTo(ChronoUnit.HOURS)
                    .toInstant()
                    .toEpochMilli()
            val end = minOf(wallEndMs, bucket + 3_600_000L)
            val before = buckets[bucket]
            buckets[bucket] =
                ListeningSegment(
                    bucket,
                    before?.startedAt ?: cursor,
                    (before?.listenedMs ?: 0) + (end - cursor),
                )
            cursor = end
        }
    }
}
