package dev.lad.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.lad.R
import dev.lad.domain.*
import dev.lad.i18n.rememberText

@Composable
fun BitPerfectInfo(state: BitPerfectState) {
    val strings = rememberText()
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
    Surface(
        shape = LadShapes.card,
        color =
            if (active) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(if (active) Icons.Default.CheckCircle else Icons.Default.Info, null)
                Text(strings[detail], fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
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
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
        }
    }
}
