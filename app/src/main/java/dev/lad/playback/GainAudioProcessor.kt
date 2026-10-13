package dev.lad.playback

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import dev.lad.domain.AudioDsp
import dev.lad.domain.isNeutral
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.*

@androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
class GainAudioProcessor : BaseAudioProcessor() {
    @Volatile var gain = 1f
    val dsp = AudioDsp()
    var onLevels: ((List<Float>) -> Unit)? = null
    @Volatile var meterEnabled = false
    private var published = 0L
    private val levels = FloatArray(24)
    private val counts = IntArray(24)

    override fun onConfigure(format: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        dsp.configure(format.sampleRate, format.channelCount)
        return when (format.encoding) {
            C.ENCODING_PCM_16BIT,
            C.ENCODING_PCM_FLOAT -> format
            else -> AudioProcessor.AudioFormat.NOT_SET
        }
    }

    override fun onFlush() {
        dsp.reset()
    }

    override fun queueInput(input: ByteBuffer) {

        if (!input.hasRemaining()) return
        val output = replaceOutputBuffer(input.remaining()).order(ByteOrder.nativeOrder())
        input.order(ByteOrder.nativeOrder())
        val floating = inputAudioFormat.encoding == C.ENCODING_PCM_FLOAT
        val step = if (floating) 4 else 2
        val total = input.remaining() / step
        val now = System.nanoTime()
        val measure = meterEnabled && now - published >= 100_000_000
        val bufferGain = gain
        dsp.beginBuffer()
        val neutral = dsp.profile.isNeutral && bufferGain == 1f
        if (neutral && !measure) {
            output.put(input)
            output.flip()
            return
        }
        if (measure) {
            levels.fill(0f)
            counts.fill(0)
        }
        var n = 0
        while (input.remaining() >= step) {
            val raw = if (floating) 0.toShort() else input.short
            val sample = if (floating) input.float else raw / 32768f
            val value =
                if (neutral) sample
                else dsp.samplePrepared(sample * bufferGain, n % inputAudioFormat.channelCount)
            if (floating) output.putFloat(value)
            else
                output.putShort(
                    if (neutral) raw else (value * 32767).toInt().coerceIn(-32768, 32767).toShort()
                )
            if (measure) {
                val bin = (n.toLong() * 24 / total.coerceAtLeast(1)).toInt().coerceIn(0, 23)
                levels[bin] += value * value
                counts[bin]++
            }
            n++
        }
        output.flip()
        if (measure) {
            published = now
            onLevels?.invoke(
                levels.indices.map {
                    (sqrt(levels[it] / counts[it].coerceAtLeast(1)) * 3.5f).coerceIn(0f, 1f)
                }
            )
        }
    }
}
