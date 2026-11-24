package dev.lad.playback

import android.media.AudioTrack
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.ForwardingAudioSink
import dev.lad.domain.*
import java.nio.ByteBuffer

@androidx.annotation.OptIn(markerClass = [UnstableApi::class])
class BitPerfectAudioSink(
    normal: AudioSink,
    private val usb: UsbBitPerfect,
    private val enabled: () -> Boolean,
    private val onTrack: (AudioTrack) -> Unit,
    private val onDuck: () -> Unit,
    private val onTrackReleased: (AudioTrack) -> Unit = {},
) : ForwardingAudioSink(normal) {
    private data class Configuration(
        val format: Format,
        val size: Int,
        val channels: IntArray?,
        val plan: PcmPlan?,
    )

    private var configuration: Configuration? = null
    private var pending: Configuration? = null
    private var direct: AudioTrack? = null
    private var sessionId = 0
    private var listener: AudioSink.Listener? = null
    private var sourceEncoding: PcmEncoding? = null
    private var sourceFormat: Format? = null
    private val converter = PcmConverter()
    private var trimmer: PcmTrimmer? = null
    private var queued: ByteBuffer? = null
    private var output: ByteBuffer? = null
    private var written = 0L
    private var lastHead = 0L
    private var headWraps = 0L
    private var mediaBaseUs = AudioSink.CURRENT_POSITION_NOT_SET
    private var discontinuity = false
    private var playing = false
    private var draining = false
    private var advancing = false
    private var directMode = false
    private var bufferSize = 0

    fun setSource(format: Format) {
        sourceFormat = format
        sourceEncoding = PcmEncoding.from(format.pcmEncoding)
    }

    override fun setListener(value: AudioSink.Listener) {
        listener = value
        super.setListener(value)
    }

    private fun plan(format: Format): PcmPlan? {
        if (!enabled() || format.sampleMimeType != MimeTypes.AUDIO_RAW) return null
        val encoding = PcmEncoding.from(format.pcmEncoding) ?: return null
        val source =
            if (sourceFormat?.sampleMimeType == MimeTypes.AUDIO_RAW) encoding else sourceEncoding
        return usb.plan(PcmFormat(format.sampleRate, format.channelCount, encoding), source)
    }

    override fun supportsFormat(format: Format) =
        getFormatSupport(format) != AudioSink.SINK_FORMAT_UNSUPPORTED

    override fun getFormatSupport(format: Format): Int =
        if (plan(format) != null) AudioSink.SINK_FORMAT_SUPPORTED_DIRECTLY
        else super.getFormatSupport(format)

    override fun configure(format: Format, specifiedBufferSize: Int, outputChannels: IntArray?) {
        val mappingIsIdentity =
            outputChannels == null || outputChannels.withIndex().all { it.index == it.value }
        val trimmingIsValid =
            format.encoderDelay in 0..format.sampleRate * 10 &&
                format.encoderPadding in 0..format.sampleRate * 10
        val next =
            Configuration(
                format,
                specifiedBufferSize,
                outputChannels,
                if (mappingIsIdentity && trimmingIsValid) plan(format) else null,
            )
        if ((directMode || next.plan != null) && hasPendingData()) {
            pending = next
            if (!directMode) super.playToEndOfStream()
        } else install(next)
    }

    private fun install(value: Configuration) {
        releaseDirect()
        configuration = value
        directMode = value.plan != null
        draining = false
        if (directMode) super.flush()
        else {
            if (enabled()) usb.configureFailed()
            super.configure(value.format, value.size, value.channels)
            if (playing) super.play()
        }
    }

    private fun initialize(): Boolean {
        val config = configuration ?: return false
        val plan = config.plan ?: return false
        val channelMask = Util.getAudioTrackChannelConfig(plan.output.channels)
        val format = usb.prepare(plan.output) ?: return fallBack(config)
        val minimum = AudioTrack.getMinBufferSize(format.sampleRate, channelMask, format.encoding)
        if (minimum <= 0) return fallBack(config)
        bufferSize =
            maxOf(
                minimum * 4,
                plan.output.sampleRate * plan.output.channels * plan.output.encoding.bytes / 4,
            )
        val value =
            runCatching {
                    AudioTrack.Builder()
                        .setAudioAttributes(usb.playbackAttributes)
                        .setAudioFormat(format)
                        .setBufferSizeInBytes(bufferSize)
                        .setTransferMode(AudioTrack.MODE_STREAM)
                        .setSessionId(sessionId)
                        .build()
                }
                .getOrNull() ?: return fallBack(config)
        if (value.state != AudioTrack.STATE_INITIALIZED || !usb.attach(value, plan.output)) {
            value.release()
            return fallBack(config)
        }
        direct = value
        trimmer =
            PcmTrimmer(
                plan.output.channels * plan.output.encoding.bytes,
                config.format.encoderDelay,
                config.format.encoderPadding,
            )
        onTrack(value)
        listener?.onAudioTrackInitialized(
            AudioSink.AudioTrackConfig(
                format.encoding,
                format.sampleRate,
                channelMask,
                false,
                false,
                bufferSize,
            )
        )
        if (playing) value.play()
        return true
    }

    private fun fallBack(config: Configuration): Boolean {
        usb.reject()
        directMode = false
        super.configure(config.format, config.size, config.channels)
        if (playing) super.play()
        return false
    }

    override fun handleBuffer(
        buffer: ByteBuffer,
        presentationTimeUs: Long,
        encodedAccessUnitCount: Int,
    ): Boolean {
        pending?.let { next ->
            if (hasPendingData()) return false
            pending = null
            install(next)
        }
        if (!directMode)
            return super.handleBuffer(buffer, presentationTimeUs, encodedAccessUnitCount)
        if (direct == null && !initialize())
            return super.handleBuffer(buffer, presentationTimeUs, encodedAccessUnitCount)
        val config = configuration ?: return false
        val plan = config.plan ?: return false
        val track = direct ?: return false
        val activeTrimmer = trimmer ?: return false
        if (queued !== buffer) {
            queued = buffer
            output = activeTrimmer.process(converter.convert(buffer, plan))
            if (mediaBaseUs == AudioSink.CURRENT_POSITION_NOT_SET || discontinuity) {
                mediaBaseUs =
                    presentationTimeUs - writtenFrames() * 1_000_000 / plan.output.sampleRate
                if (discontinuity) listener?.onPositionDiscontinuity()
                discontinuity = false
            }
        }
        val data = output ?: return true
        if (data.hasRemaining()) {
            val count = track.write(data, data.remaining(), AudioTrack.WRITE_NON_BLOCKING)
            if (count < 0) {
                val error =
                    AudioSink.WriteException(
                        count,
                        config.format,
                        count == AudioTrack.ERROR_DEAD_OBJECT,
                    )
                listener?.onAudioSinkError(error)
                throw error
            }
            written += count
        }
        if (data.hasRemaining()) return false
        queued = null
        output = null
        return true
    }

    private fun writtenFrames(): Long {
        val format = configuration?.plan?.output ?: return 0
        return written / (format.channels * format.encoding.bytes)
    }

    private fun playedFrames(): Long {
        val value = direct ?: return 0
        val head =
            runCatching { value.playbackHeadPosition.toLong() and 0xffffffffL }.getOrDefault(lastHead)
        val playState = runCatching { value.playState }.getOrDefault(AudioTrack.PLAYSTATE_STOPPED)
        if (head < lastHead) {
            if (playState == AudioTrack.PLAYSTATE_PLAYING && lastHead - head > 0x80000000L) {
                headWraps += 0x100000000L
                lastHead = head
            }
        } else {
            lastHead = head
        }
        val played = (headWraps + lastHead).coerceAtMost(writtenFrames())
        if (played > 0 && !advancing && playing) {
            advancing = true
            listener?.onPositionAdvancing(System.currentTimeMillis())
        }
        return played
    }

    override fun getCurrentPositionUs(sourceEnded: Boolean): Long {
        if (!directMode) return super.getCurrentPositionUs(sourceEnded)
        if (mediaBaseUs == AudioSink.CURRENT_POSITION_NOT_SET) return mediaBaseUs
        val sampleRate = configuration?.plan?.output?.sampleRate ?: return mediaBaseUs
        return mediaBaseUs + playedFrames() * 1_000_000 / sampleRate
    }

    override fun hasPendingData(): Boolean =
        if (directMode)
            direct != null && (output?.hasRemaining() == true || playedFrames() < writtenFrames())
        else super.hasPendingData()

    override fun isEnded() = if (directMode) draining && !hasPendingData() else super.isEnded()

    override fun playToEndOfStream() {
        if (directMode) draining = true else super.playToEndOfStream()
    }

    override fun play() {
        playing = true
        if (directMode) direct?.play() else super.play()
    }

    override fun pause() {
        playing = false
        advancing = false
        if (directMode) direct?.pause() else super.pause()
    }

    override fun handleDiscontinuity() {
        if (directMode) discontinuity = true else super.handleDiscontinuity()
    }

    override fun setPlaybackParameters(value: PlaybackParameters) {
        super.setPlaybackParameters(if (enabled()) PlaybackParameters.DEFAULT else value)
    }

    override fun getPlaybackParameters() =
        if (enabled()) PlaybackParameters.DEFAULT else super.getPlaybackParameters()

    override fun setSkipSilenceEnabled(value: Boolean) {
        super.setSkipSilenceEnabled(value && !enabled())
    }

    override fun getSkipSilenceEnabled() = !enabled() && super.getSkipSilenceEnabled()

    override fun setAudioSessionId(value: Int) {
        sessionId = value
        super.setAudioSessionId(value)
    }

    override fun setVolume(value: Float) {
        if (directMode && value < 1f) {
            direct?.pause()
            onDuck()
        } else if (!directMode) super.setVolume(value)
    }

    override fun getAudioTrackBufferSizeUs(): Long {
        val outputFormat = if (directMode) configuration?.plan?.output else null
        return if (outputFormat != null)
            bufferSize.toLong() * 1_000_000 /
                (outputFormat.sampleRate * outputFormat.channels * outputFormat.encoding.bytes)
        else super.getAudioTrackBufferSizeUs()
    }

    override fun flush() {
        releaseDirect()
        pending?.let { configuration = it }
        pending = null
        directMode = configuration?.plan != null
        super.flush()
    }

    override fun reset() {
        releaseDirect()
        configuration = null
        pending = null
        directMode = false
        super.reset()
    }

    override fun release() {
        releaseDirect()
        super.release()
    }

    private fun releaseDirect() {
        val value = direct
        direct = null
        trimmer = null
        queued = null
        output = null
        written = 0
        lastHead = 0
        headWraps = 0
        mediaBaseUs = AudioSink.CURRENT_POSITION_NOT_SET
        discontinuity = false
        advancing = false
        if (value != null) {
            onTrackReleased(value)
            usb.detach(value)
            runCatching { value.pause() }
            runCatching { value.flush() }
            runCatching { value.release() }
        }
    }
}
