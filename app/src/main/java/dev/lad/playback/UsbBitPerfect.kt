package dev.lad.playback

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioMixerAttributes
import android.media.AudioTrack
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.annotation.RequiresApi
import dev.lad.domain.*

class UsbBitPerfect(context: Context, private val onChanged: () -> Unit) {
    private val manager = context.getSystemService(AudioManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private val lock = Any()
    val playbackAttributes =
        AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()
    @Volatile private var enabled = false
    @Volatile private var selected: AudioDeviceInfo? = null
    @Volatile private var supported = emptyList<PcmFormat>()
    @Volatile private var current = BitPerfectState()
    @Volatile private var track: AudioTrack? = null
    private var native: Native? = null

    val hasNativeOutput: Boolean
        get() = synchronized(lock) { supported.isNotEmpty() || track != null }

    fun start() {
        synchronized(lock) {
            if (Build.VERSION.SDK_INT >= 34 && native == null)
                native = runCatching { Native() }.getOrNull()
        }
        refreshDevices()
    }

    fun setEnabled(value: Boolean) {
        synchronized(lock) {
            enabled = value
            if (!value) clearLocked()
        }
        refreshDevices()
    }

    fun refreshDevices(): Boolean =
        synchronized(lock) {
            val previous = selected?.id
            val previousSupported = supported
            val devices =
                manager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).filter {
                    it.type == AudioDeviceInfo.TYPE_USB_DEVICE ||
                        it.type == AudioDeviceInfo.TYPE_USB_HEADSET ||
                        it.type == AudioDeviceInfo.TYPE_USB_ACCESSORY
                }
            val candidates =
                devices.map { device ->
                    device to
                        if (Build.VERSION.SDK_INT >= 34) native?.formats(device).orEmpty()
                        else emptyList()
                }
            val choice =
                candidates.firstOrNull { it.first.id == previous && it.second.isNotEmpty() }
                    ?: candidates.firstOrNull { it.second.isNotEmpty() }
                    ?: candidates.firstOrNull { it.first.id == previous }
                    ?: candidates.firstOrNull()
            val nextDevice = choice?.first
            val nextSupported = choice?.second.orEmpty()
            if (previous != nextDevice?.id || nextSupported.isEmpty()) clearLocked()
            selected = nextDevice
            supported = nextSupported
            publishAvailabilityLocked()
            previous != selected?.id || previousSupported != supported
        }

    fun plan(input: PcmFormat, source: PcmEncoding?): PcmPlan? =
        synchronized(lock) {
            if (enabled) BitPerfectPolicy.plan(input, source, supported) else null
        }

    fun prepare(format: PcmFormat): AudioFormat? =
        synchronized(lock) {
            val accepted =
                if (Build.VERSION.SDK_INT >= 34 && enabled) native?.apply(format) else null
            if (accepted == null) reportLocked(BitPerfectState(BitPerfectStatus.UNAVAILABLE))
            accepted
        }

    fun attach(value: AudioTrack, format: PcmFormat): Boolean =
        synchronized(lock) {
            if (Build.VERSION.SDK_INT < 34 || !enabled) return false
            val device = selected ?: return false
            val accepted =
                runCatching { native?.matches(format, value.format) == true }.getOrDefault(false)
            if (!accepted || !value.setPreferredDevice(device)) {
                clearLocked()
                reportLocked(BitPerfectState(BitPerfectStatus.UNAVAILABLE))
                return false
            }
            track = value
            reportLocked(BitPerfectState(BitPerfectStatus.PREPARING, format))
            true
        }

    fun configureFailed() =
        synchronized(lock) {
            if (enabled && selected != null && supported.isNotEmpty())
                reportLocked(BitPerfectState(BitPerfectStatus.FORMAT_UNSUPPORTED))
            else publishAvailabilityLocked()
        }

    fun reject() =
        synchronized(lock) {
            clearLocked()
            reportLocked(BitPerfectState(BitPerfectStatus.UNAVAILABLE))
        }

    fun state(): BitPerfectState =
        synchronized(lock) {
            val value = track
            val format = current.format
            if (Build.VERSION.SDK_INT >= 34 && enabled && value != null && format != null) {
                val routed = runCatching { value.routedDevice }.getOrNull()
                val matches =
                    runCatching {
                            routed?.id == selected?.id &&
                                native?.matches(format, value.format) == true
                        }
                        .getOrDefault(false)
                val status =
                    if (matches) BitPerfectStatus.ACTIVE
                    else if (routed != null) BitPerfectStatus.UNAVAILABLE
                    else BitPerfectStatus.PREPARING
                return BitPerfectState(status, format)
            }
            current
        }

    fun detach(value: AudioTrack?) =
        synchronized(lock) {
            if (value == null || track !== value) return
            clearLocked()
            publishAvailabilityLocked()
        }

    fun close() {
        synchronized(lock) {
            clearLocked()
            if (Build.VERSION.SDK_INT >= 34) native?.close()
            native = null
        }
        handler.removeCallbacksAndMessages(null)
    }

    private fun clearLocked() {
        track = null
        if (Build.VERSION.SDK_INT >= 34) native?.clear()
    }

    private fun publishAvailabilityLocked() {
        val status =
            when {
                !enabled -> BitPerfectStatus.OFF
                Build.VERSION.SDK_INT < 34 -> BitPerfectStatus.ANDROID_REQUIRED
                selected == null -> BitPerfectStatus.CONNECT_USB
                supported.isEmpty() -> BitPerfectStatus.DEVICE_UNSUPPORTED
                track != null -> return
                else -> BitPerfectStatus.PREPARING
            }
        reportLocked(BitPerfectState(status))
    }

    private fun reportLocked(value: BitPerfectState) {
        if (current == value) return
        current = value
        handler.post(onChanged)
    }

    @RequiresApi(34)
    private inner class Native {
        private var appliedDevice: AudioDeviceInfo? = null
        private val listener =
            AudioManager.OnPreferredMixerAttributesChangedListener { attr, device, _ ->
                if (attr.usage == AudioAttributes.USAGE_MEDIA && device.id == selected?.id)
                    handler.post(onChanged)
            }

        init {
            manager.addOnPreferredMixerAttributesChangedListener(contextMainExecutor(), listener)
        }

        private fun contextMainExecutor() = java.util.concurrent.Executor { handler.post(it) }

        fun formats(device: AudioDeviceInfo): List<PcmFormat> =
            runCatching {
                    manager
                        .getSupportedMixerAttributes(device)
                        .filter {
                            it.mixerBehavior == AudioMixerAttributes.MIXER_BEHAVIOR_BIT_PERFECT
                        }
                        .mapNotNull { it.format.toPcm() }
                }
                .getOrDefault(emptyList())

        fun apply(format: PcmFormat): AudioFormat? =
            runCatching {
                    val device = selected ?: return null
                    val preferred =
                        manager.getSupportedMixerAttributes(device).firstOrNull {
                            it.mixerBehavior == AudioMixerAttributes.MIXER_BEHAVIOR_BIT_PERFECT &&
                                it.format.toPcm() == format
                        } ?: return null
                    if (appliedDevice?.id != device.id) clear()
                    val requested =
                        AudioMixerAttributes.Builder(preferred.format)
                            .setMixerBehavior(AudioMixerAttributes.MIXER_BEHAVIOR_BIT_PERFECT)
                            .build()
                    val accepted =
                        manager.setPreferredMixerAttributes(playbackAttributes, device, requested)
                    if (accepted) appliedDevice = device else clear()
                    if (accepted) preferred.format else null
                }
                .getOrNull()

        fun matches(format: PcmFormat, actual: AudioFormat): Boolean =
            selected?.let { device ->
                manager.getPreferredMixerAttributes(playbackAttributes, device)?.let {
                    it.mixerBehavior == AudioMixerAttributes.MIXER_BEHAVIOR_BIT_PERFECT &&
                        it.format.toPcm() == format &&
                        it.format.encoding == actual.encoding &&
                        it.format.sampleRate == actual.sampleRate &&
                        it.format.channelMask == actual.channelMask &&
                        it.format.channelIndexMask == actual.channelIndexMask
                }
            } == true

        fun clear() {
            appliedDevice?.let {
                runCatching { manager.clearPreferredMixerAttributes(playbackAttributes, it) }
            }
            appliedDevice = null
        }

        fun close() {
            manager.removeOnPreferredMixerAttributesChangedListener(listener)
        }

        private fun AudioFormat.toPcm() =
            PcmEncoding.from(encoding)?.let { PcmFormat(sampleRate, channelCount, it) }
    }
}
