package dev.lad.domain

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.random.Random
import org.junit.Assert.*
import org.junit.Test

class BitPerfectTest {
    private fun format(encoding: PcmEncoding, rate: Int = 44100, channels: Int = 2) =
        PcmFormat(rate, channels, encoding)

    private fun bytes(buffer: ByteBuffer) = ByteArray(buffer.remaining()).also { buffer.get(it) }

    @Test
    fun exactNativeFormatIsPreferred() {
        val input = format(PcmEncoding.PCM24)
        val plan =
            BitPerfectPolicy.plan(
                input,
                PcmEncoding.PCM24,
                listOf(format(PcmEncoding.PCM32), input),
            )
        assertEquals(input, plan?.output)
    }

    @Test
    fun rateAndChannelCountAreNeverChanged() {
        val input = format(PcmEncoding.PCM16)
        assertNull(
            BitPerfectPolicy.plan(
                input,
                PcmEncoding.PCM16,
                listOf(format(PcmEncoding.PCM16, 48000)),
            )
        )
        assertNull(
            BitPerfectPolicy.plan(
                input,
                PcmEncoding.PCM16,
                listOf(format(PcmEncoding.PCM16, channels = 1)),
            )
        )
    }

    @Test
    fun decoderPrecisionLossNeverQualifiesAsBitPerfect() {
        assertNull(
            BitPerfectPolicy.plan(
                format(PcmEncoding.PCM16),
                PcmEncoding.PCM24,
                listOf(format(PcmEncoding.PCM16)),
            )
        )
        assertNull(
            BitPerfectPolicy.plan(
                format(PcmEncoding.FLOAT),
                PcmEncoding.PCM32,
                listOf(format(PcmEncoding.FLOAT)),
            )
        )
    }

    @Test
    fun integer32CannotBeSentThroughFloatOr24Bit() {
        assertNull(
            BitPerfectPolicy.plan(
                format(PcmEncoding.PCM32),
                PcmEncoding.PCM32,
                listOf(format(PcmEncoding.FLOAT), format(PcmEncoding.PCM24)),
            )
        )
    }

    @Test
    fun genuineFloatAndUnknownFloatAreNeverQuantized() {
        val input = format(PcmEncoding.FLOAT)
        assertNull(
            BitPerfectPolicy.plan(input, PcmEncoding.FLOAT, listOf(format(PcmEncoding.PCM24)))
        )
        assertNull(BitPerfectPolicy.plan(input, null, listOf(format(PcmEncoding.PCM24))))
        assertNotNull(BitPerfectPolicy.plan(input, null, listOf(input)))
    }

    @Test
    fun integer16IsPaddedTo24WithoutChangingAmplitude() {
        val input = ByteBuffer.allocateDirect(10).order(ByteOrder.LITTLE_ENDIAN)
        listOf(-32768, -1, 0, 1, 32767).forEach { input.putShort(it.toShort()) }
        input.flip()
        val output =
            bytes(
                PcmConverter()
                    .convert(input, PcmPlan(format(PcmEncoding.PCM16), format(PcmEncoding.PCM24)))
            )
        assertArrayEquals(byteArrayOf(0, 0, -128, 0, -1, -1, 0, 0, 0, 0, 1, 0, 0, -1, 127), output)
    }

    @Test
    fun integer24IsPaddedTo32Exactly() {
        val input = ByteBuffer.wrap(byteArrayOf(0, 0, -128, -1, -1, 127, -1, -1, -1, 1, 0, 0))
        val output =
            PcmConverter()
                .convert(input, PcmPlan(format(PcmEncoding.PCM24), format(PcmEncoding.PCM32)))
                .order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(Int.MIN_VALUE, output.int)
        assertEquals(0x7fffff00, output.int)
        assertEquals(-256, output.int)
        assertEquals(256, output.int)
    }

    @Test
    fun every24BitBoundarySurvivesFloatDecoderOutput() {
        val values =
            intArrayOf(-8388608, -8388607, -1, 0, 1, 8388606, 8388607) +
                IntArray(10000) { Random(41 + it).nextInt(-8388608, 8388608) }
        val input = ByteBuffer.allocateDirect(values.size * 4).order(ByteOrder.LITTLE_ENDIAN)
        values.forEach { input.putFloat(it / 8388608f) }
        input.flip()
        val plan =
            BitPerfectPolicy.plan(
                format(PcmEncoding.FLOAT),
                PcmEncoding.PCM24,
                listOf(format(PcmEncoding.PCM24)),
            )!!
        val output = PcmConverter().convert(input, plan)
        values.forEach { expected ->
            val actual =
                (output.get().toInt() and 255) or
                    ((output.get().toInt() and 255) shl 8) or
                    (output.get().toInt() shl 16)
            assertEquals(expected, actual)
        }
    }

    @Test
    fun directOutputDoesNotCopyOrChangeSamples() {
        val input = ByteBuffer.wrap(byteArrayOf(-1, 0, 1, 127, -128, 42))
        val plan = PcmPlan(format(PcmEncoding.PCM24), format(PcmEncoding.PCM24))
        assertSame(input, PcmConverter().convert(input, plan))
        assertArrayEquals(byteArrayOf(-1, 0, 1, 127, -128, 42), bytes(input))
    }

    @Test
    fun paddingAndEncoderDelayAreTrimmedAcrossBufferBoundaries() {
        val trim = PcmTrimmer(2, 2, 2)
        val emitted = ArrayList<Byte>()
        for (chunk in
            listOf(
                byteArrayOf(1, 1),
                byteArrayOf(2, 2, 3, 3, 4, 4),
                byteArrayOf(5, 5, 6, 6, 7, 7),
            )) emitted.addAll(bytes(trim.process(ByteBuffer.wrap(chunk))).toList())
        assertArrayEquals(byteArrayOf(3, 3, 4, 4, 5, 5), emitted.toByteArray())
    }

    @Test
    fun subPaddingStreamsDoNotEmitTailSamples() {
        val trim = PcmTrimmer(6, 0, 4)
        assertEquals(0, trim.process(ByteBuffer.wrap(ByteArray(6))).remaining())
        assertEquals(0, trim.process(ByteBuffer.wrap(ByteArray(12))).remaining())
    }

    @Test
    fun noTrimmingLeavesTheBufferUntouched() {
        val input = ByteBuffer.wrap(byteArrayOf(1, 2, 3, 4))
        assertSame(input, PcmTrimmer(2, 0, 0).process(input))
    }

    @Test
    fun floatToIntegerClampsFullScaleAndOutOfRangeWithoutWrapAround() {
        val samples = floatArrayOf(-1.5f, -1.0f, 0f, 1.0f, 1.5f, Float.NaN)
        fun floatBuffer() =
            ByteBuffer.allocateDirect(samples.size * 4).order(ByteOrder.LITTLE_ENDIAN).apply {
                samples.forEach(::putFloat)
                flip()
            }

        val pcm16 =
            PcmConverter()
                .convert(floatBuffer(), PcmPlan(format(PcmEncoding.FLOAT), format(PcmEncoding.PCM16)))
                .order(ByteOrder.LITTLE_ENDIAN)
        assertEquals((-32768).toShort(), pcm16.short)
        assertEquals((-32768).toShort(), pcm16.short)
        assertEquals(0.toShort(), pcm16.short)
        assertEquals(32767.toShort(), pcm16.short)
        assertEquals(32767.toShort(), pcm16.short)
        assertEquals(0.toShort(), pcm16.short)

        val pcm24 =
            PcmConverter()
                .convert(floatBuffer(), PcmPlan(format(PcmEncoding.FLOAT), format(PcmEncoding.PCM24)))
        fun next24() =
            (pcm24.get().toInt() and 255) or
                ((pcm24.get().toInt() and 255) shl 8) or
                (pcm24.get().toInt() shl 16)
        assertEquals(-8388608, next24())
        assertEquals(-8388608, next24())
        assertEquals(0, next24())
        assertEquals(8388607, next24())
        assertEquals(8388607, next24())
        assertEquals(0, next24())
    }
}
