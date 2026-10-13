package dev.lad.domain

object PlaybackPowerPolicy {
    fun fadeWindow(seconds: Int, duration: Long, speed: Float): Long =
        minOf(
            seconds.coerceAtLeast(0) * 1000L,
            (duration.coerceAtLeast(0) / speed.coerceAtLeast(0.25f) / 2).toLong(),
        )

    fun prepareIncoming(playing: Boolean, remaining: Long, fade: Long, speed: Float): Boolean =
        playing && fade > 0 && remaining / speed.coerceAtLeast(0.25f) <= fade + 2500

    fun offload(
        profile: SoundProfile,
        replayGain: Boolean,
        crossfade: Int,
        speed: Float,
        meter: Boolean,
    ): Boolean = profile.isNeutral && !replayGain && crossfade == 0 && speed == 1f && !meter

    fun waitMs(
        playing: Boolean,
        visible: Boolean,
        lyrics: Boolean,
        saving: Boolean,
        fading: Boolean,
        checkpointAge: Long,
        sleepRemaining: Long?,
        fadePreparation: Long?,
        lyricsDelayMs: Long = 100,
    ): Long {
        val audio =
            when {
                !playing -> Long.MAX_VALUE
                fading -> 50L
                visible && lyrics && !saving -> lyricsDelayMs.coerceIn(50, 1000)
                visible -> 1000L
                else -> (30_000 - checkpointAge).coerceAtLeast(1000)
            }
        return minOf(
            audio,
            sleepRemaining?.coerceAtLeast(50) ?: Long.MAX_VALUE,
            if (playing) fadePreparation?.coerceAtLeast(250) ?: Long.MAX_VALUE else Long.MAX_VALUE,
        )
    }
}

val SoundProfile.isNeutral: Boolean
    get() = (!enabled || bands.all { it == 0f }) && balance == 0f && volume == 1f
