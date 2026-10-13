package dev.lad.domain

import kotlin.math.*

data class SoundProfile(
    val enabled: Boolean = false,
    val bands: List<Float> = List(5) { 0f },
    val balance: Float = 0f,
    val volume: Float = 1f,
)

class AudioDsp {
    @Volatile var profile = SoundProfile()
    private var applied: SoundProfile? = null
    private var rate = 44100
    private var channels = 2
    private var filters = emptyArray<Array<Biquad>>()
    private var headroom = 1f

    fun configure(sampleRate: Int, channelCount: Int) {
        rate = sampleRate
        channels = channelCount
        applied = null
    }

    fun reset() {
        applied = null
    }

    private fun refresh() {
        val next = profile
        if (next == applied) return
        val frequencies = listOf(60.0, 230.0, 910.0, 3600.0, 14000.0)
        filters =
            Array(channels) {
                Array(5) { band ->
                    Biquad(
                        frequencies[band].coerceAtMost(rate * .45),
                        rate,
                        if (next.enabled)
                            next.bands.getOrElse(band) { 0f }.coerceIn(-12f, 12f).toDouble()
                        else 0.0,
                    )
                }
            }
        headroom =
            if (next.enabled)
                10.0.pow(-(next.bands.maxOrNull() ?: 0f).coerceAtLeast(0f) / 20.0).toFloat()
            else 1f
        applied = next
    }

    fun beginBuffer() {
        refresh()
    }

    fun sample(value: Float, channel: Int): Float {
        refresh()
        return samplePrepared(value, channel)
    }

    fun samplePrepared(value: Float, channel: Int): Float {
        val p = applied ?: profile
        var output = value.toDouble()
        if (p.enabled)
            filters[channel].forEachIndexed { band, filter ->
                if (p.bands.getOrElse(band) { 0f } != 0f) output = filter.process(output)
            }
        val balance = p.balance.coerceIn(-1f, 1f)
        val attenuation =
            if (channels < 2) 1f
            else
                when (channel) {
                    0 -> if (balance > 0) 1f - balance else 1f
                    1 -> if (balance < 0) 1f + balance else 1f
                    else -> 1f
                }
        return (output * headroom * p.volume.coerceIn(0f, 1f) * attenuation)
            .toFloat()
            .coerceIn(-1f, 1f)
    }

    private class Biquad(frequency: Double, sampleRate: Int, gain: Double) {
        private val a = 10.0.pow(gain / 40)
        private val w = 2 * PI * frequency / sampleRate
        private val alpha = sin(w) / 2.0
        private val a0 = 1 + alpha / a
        private val b0 = (1 + alpha * a) / a0
        private val b1 = (-2 * cos(w)) / a0
        private val b2 = (1 - alpha * a) / a0
        private val a1 = (-2 * cos(w)) / a0
        private val a2 = (1 - alpha / a) / a0
        private var z1 = 0.0
        private var z2 = 0.0

        fun process(x: Double): Double {
            val y = b0 * x + z1
            z1 = b1 * x - a1 * y + z2
            z2 = b2 * x - a2 * y
            return y
        }
    }
}
