package dev.lad.playback

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import dev.lad.domain.SoundProfile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.*
import org.junit.Test

@androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
class GainAudioProcessorTest {
    @Test
    fun emptyPollPreservesPendingAudioAndPublishedLevels() {
        val processor = GainAudioProcessor()
        processor.configure(AudioProcessor.AudioFormat(44100, 2, C.ENCODING_PCM_16BIT))
        processor.flush()
        processor.meterEnabled = true
        var publications = 0
        var last = emptyList<Float>()
        processor.onLevels = {
            publications++
            last = it
        }
        val input = ByteBuffer.allocateDirect(4096).order(ByteOrder.nativeOrder())
        repeat(2048) { input.putShort(8192) }
        input.flip()
        processor.queueInput(input)
        processor.queueInput(ByteBuffer.allocateDirect(0))
        assertEquals(4096, processor.output.remaining())
        assertEquals(1, publications)
        assertTrue(last.all { it > 0.1f })
    }

    @Test
    fun balanceCommandAffectsNextPcmBufferWithoutFlush() {
        val processor = GainAudioProcessor()
        processor.configure(AudioProcessor.AudioFormat(44100, 2, C.ENCODING_PCM_16BIT))
        processor.flush()
        fun frame(): ByteBuffer =
            ByteBuffer.allocateDirect(4).order(ByteOrder.nativeOrder()).apply {
                putShort(12000)
                putShort(12000)
                flip()
            }
        processor.queueInput(frame())
        assertTrue(processor.output.order(ByteOrder.nativeOrder()).short > 11000)
        processor.dsp.profile = SoundProfile(balance = 1f)
        processor.queueInput(frame())
        val output = processor.output.order(ByteOrder.nativeOrder())
        assertEquals(0, output.short.toInt())
        assertTrue(output.short > 11000)
    }
}

@androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
class NeutralPcmTest {
    @Test
    fun noEffectsPreservesEveryPcmSampleAndSkipsMetering() {
        val processor = GainAudioProcessor()
        processor.configure(AudioProcessor.AudioFormat(44100, 2, C.ENCODING_PCM_16BIT))
        processor.flush()
        var publications = 0
        processor.onLevels = { publications++ }
        val original = shortArrayOf(-32768, -12000, -1, 0, 1, 12000, 32767, 300)
        val input = ByteBuffer.allocateDirect(original.size * 2).order(ByteOrder.nativeOrder())
        original.forEach { input.putShort(it) }
        input.flip()
        processor.queueInput(input)
        val output = processor.output.order(ByteOrder.nativeOrder())
        original.forEach { assertEquals(it, output.short) }
        assertEquals(0, publications)
    }
}
