@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package dev.lad.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.lad.R
import dev.lad.domain.*
import dev.lad.i18n.rememberText

@Composable
fun BitPerfectInfo(state: BitPerfectState) {
    val strings = rememberText()
    val animations = LocalLadSettings.current.animations
    val detail =
        when (state.status) {
            BitPerfectStatus.OFF -> R.string.bit_perfect_off
            BitPerfectStatus.ANDROID_REQUIRED -> R.string.bit_perfect_android_required
            BitPerfectStatus.CONNECT_USB -> R.string.bit_perfect_connect_usb
            BitPerfectStatus.DEVICE_UNSUPPORTED -> R.string.bit_perfect_device_unsupported
            BitPerfectStatus.FORMAT_UNSUPPORTED -> R.string.bit_perfect_format_unsupported
            BitPerfectStatus.PREPARING -> R.string.bit_perfect_preparing
            BitPerfectStatus.ACTIVE -> R.string.bit_perfect_active
            BitPerfectStatus.UNAVAILABLE -> R.string.bit_perfect_unavailable
        }
    val active = state.status == BitPerfectStatus.ACTIVE
    val container by
        animateColorAsState(
            if (active) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceContainerHigh,
            if (animations) MaterialTheme.motionScheme.fastEffectsSpec() else snap(),
            label = "bitperfect container",
        )
    val contentColor =
        if (active) MaterialTheme.colorScheme.onPrimaryContainer
        else MaterialTheme.colorScheme.onSurface
    Surface(
        shape = LadShapes.artwork,
        color = container,
        contentColor = contentColor,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    when (state.status) {
                        BitPerfectStatus.ACTIVE -> Icons.Default.CheckCircle
                        BitPerfectStatus.CONNECT_USB,
                        BitPerfectStatus.PREPARING -> Icons.Default.Usb
                        else -> Icons.Default.Info
                    },
                    null,
                )
                Text(strings[detail], fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            }
            state.deviceName
                ?.takeIf { state.status != BitPerfectStatus.OFF }
                ?.let { name ->
                    Text(
                        name,
                        style = MaterialTheme.typography.labelLarge,
                        color = contentColor.copy(alpha = 0.8f),
                    )
                }
            state.format
                ?.takeIf { active }
                ?.let { format ->
                    val encoding =
                        if (format.encoding == PcmEncoding.FLOAT) "Float32"
                        else strings.format(R.string.pcm_bits, format.encoding.precision)
                    Text(
                        strings.format(
                            R.string.bit_perfect_format,
                            format.sampleRate,
                            encoding,
                            format.channels,
                        ),
                        style =
                            MaterialTheme.typography.bodyMedium.copy(
                                fontFeatureSettings = "tnum"
                            ),
                    )
                }
        }
    }
}
