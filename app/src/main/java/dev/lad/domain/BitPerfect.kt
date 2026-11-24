package dev.lad.domain

import java.nio.ByteBuffer
import java.nio.ByteOrder

enum class PcmEncoding(val code: Int, val bytes: Int, val precision: Int) {
    PCM16(2, 2, 16),
    PCM24(21, 3, 24),
    PCM32(22, 4, 32),
    FLOAT(4, 4, 24);

    companion object {
        fun from(code: Int) = entries.firstOrNull { it.code == code }
    }
}

data class PcmFormat(val sampleRate: Int, val channels: Int, val encoding: PcmEncoding)

data class PcmPlan(val input: PcmFormat, val output: PcmFormat)

enum class BitPerfectStatus {
    OFF,
    ANDROID_REQUIRED,
    CONNECT_USB,
    DEVICE_UNSUPPORTED,
    FORMAT_UNSUPPORTED,
    PREPARING,
    ACTIVE,
    UNAVAILABLE,
}

data class BitPerfectState(
    val status: BitPerfectStatus = BitPerfectStatus.OFF,
    val format: PcmFormat? = null,
    val deviceName: String? = null,
)

object BitPerfectPolicy {
    fun plan(input: PcmFormat, source: PcmEncoding?, supported: List<PcmFormat>): PcmPlan? {
        if (input.sampleRate <= 0 || input.channels <= 0) return null
        if (
            source != null &&
                source != PcmEncoding.FLOAT &&
                input.encoding.precision < source.precision
        )
            return null
        return supported
            .filter { it.sampleRate == input.sampleRate && it.channels == input.channels }
            .sortedBy { if (it.encoding == input.encoding) 0 else it.encoding.bytes }
            .firstOrNull { output ->
                when {
                    output.encoding == input.encoding -> true
                    input.encoding == PcmEncoding.FLOAT ->
                        source != null &&
                            source != PcmEncoding.FLOAT &&
                            source.precision <= output.encoding.precision
                    output.encoding == PcmEncoding.FLOAT -> input.encoding.precision <= 24
                    else -> output.encoding.precision >= input.encoding.precision
                }
            }
            ?.let { PcmPlan(input, it) }
    }
}

class PcmConverter {
    private var output = ByteBuffer.allocateDirect(0)

    fun convert(input: ByteBuffer, plan: PcmPlan): ByteBuffer {
        if (plan.input.encoding == plan.output.encoding) return input
        val source = plan.input.encoding
        val target = plan.output.encoding
        require(input.remaining() % source.bytes == 0)
        val length = input.remaining() / source.bytes * target.bytes
        if (output.capacity() < length) output = ByteBuffer.allocateDirect(length)
        output.clear()
        output.order(ByteOrder.LITTLE_ENDIAN)
        input.order(ByteOrder.LITTLE_ENDIAN)
        while (input.hasRemaining()) {
            if (source == PcmEncoding.FLOAT) {
                val sample =
                    input.float.let { if (it.isFinite()) it.toDouble().coerceIn(-1.0, 1.0) else 0.0 }
                val scale = (1L shl (target.precision - 1))
                val value = (sample * scale).toLong().coerceIn(-scale, scale - 1L).toInt()
                writeInteger(output, target, value)
            } else {
                val value =
                    when (source) {
                        PcmEncoding.PCM16 -> input.short.toInt()
                        PcmEncoding.PCM24 ->
                            (input.get().toInt() and 255) or
                                ((input.get().toInt() and 255) shl 8) or
                                (input.get().toInt() shl 16)
                        PcmEncoding.PCM32 -> input.int
                        else -> error("Unsupported PCM encoding")
                    }
                if (target == PcmEncoding.FLOAT)
                    output.putFloat(value / (1L shl (source.precision - 1)).toFloat())
                else writeInteger(output, target, value shl (target.precision - source.precision))
            }
        }
        output.flip()
        return output
    }

    private fun writeInteger(buffer: ByteBuffer, encoding: PcmEncoding, value: Int) {
        when (encoding) {
            PcmEncoding.PCM16 -> buffer.putShort(value.toShort())
            PcmEncoding.PCM24 -> {
                buffer.put(value.toByte())
                buffer.put((value shr 8).toByte())
                buffer.put((value shr 16).toByte())
            }
            PcmEncoding.PCM32 -> buffer.putInt(value)
            else -> error("Unsupported PCM encoding")
        }
    }
}

class PcmTrimmer(private val frameBytes: Int, delayFrames: Int, paddingFrames: Int) {
    private var delay = delayFrames.toLong() * frameBytes
    private val tail = ByteArray(paddingFrames * frameBytes)
    private var tailSize = 0
    private var output = ByteBuffer.allocateDirect(0)

    fun process(input: ByteBuffer): ByteBuffer {
        val skip = minOf(delay, input.remaining().toLong()).toInt()
        input.position(input.position() + skip)
        delay -= skip
        if (tail.isEmpty()) return input
        val size = tailSize + input.remaining()
        val emit = (size - tail.size).coerceAtLeast(0)
        if (output.capacity() < emit) output = ByteBuffer.allocateDirect(emit)
        output.clear()
        val fromTail = minOf(emit, tailSize)
        output.put(tail, 0, fromTail)
        val fromInput = emit - fromTail
        val limit = input.limit()
        input.limit(input.position() + fromInput)
        output.put(input)
        input.limit(limit)
        val retained = tailSize - fromTail
        if (retained > 0) tail.copyInto(tail, 0, fromTail, tailSize)
        val added = input.remaining()
        input.get(tail, retained, added)
        tailSize = retained + added
        output.flip()
        return output
    }
}
