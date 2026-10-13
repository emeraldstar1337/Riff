package dev.lad.domain
object Crossfade {
    fun fraction(remainingMediaMs: Long, speed: Float, fadeMs: Long): Float =
        if (fadeMs <= 0) 0f
        else
            (1f - remainingMediaMs.coerceAtLeast(0).toFloat() / speed.coerceAtLeast(0.1f) / fadeMs)
                .coerceIn(0f, 1f)

    fun split(elapsedMs: Long, fraction: Float): Pair<Long, Long> {
        val second = (elapsedMs * fraction.coerceIn(0f, 1f)).toLong()
        return (elapsedMs - second) to second
    }
}
