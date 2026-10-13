package dev.lad.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import dev.lad.R
import dev.lad.i18n.rememberText
import kotlin.math.PI
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SeekBar(
    value: Float,
    maximum: Float,
    onValue: (Float) -> Unit,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier,
    playing: Boolean = false,
    positionTimestampMs: Long = android.os.SystemClock.elapsedRealtime(),
    speed: Float = 1f,
    style: String = LocalLadSettings.current.seekStyle,
) {
    val strings = rememberText()
    val settings = LocalLadSettings.current
    val owner = LocalLifecycleOwner.current
    val wavy = style.startsWith("wave")
    val bar = style.endsWith("bar")
    val phase = remember { mutableFloatStateOf(0f) }
    val position = remember { mutableFloatStateOf(value) }
    var dragging by remember { mutableStateOf(false) }
    LaunchedEffect(
        playing,
        value,
        positionTimestampMs,
        speed,
        dragging,
        settings.animations,
        wavy,
        owner,
    ) {
        position.floatValue = value
        if (playing && settings.animations && !dragging) {
            owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                var previous = 0L
                while (
                    isActive &&
                        (currentCoroutineContext()[MotionDurationScale]?.scaleFactor ?: 1f) > 0f
                ) withFrameNanos { now ->
                    if (previous == 0L) previous = now
                    if (now - previous >= 33_333_333L) {
                        val elapsed =
                            (android.os.SystemClock.elapsedRealtime() - positionTimestampMs)
                                .coerceIn(0, 2000)
                        position.floatValue = (value + elapsed * speed).coerceAtMost(maximum)
                        if (wavy)
                            phase.floatValue =
                                (phase.floatValue + (now - previous) / 1_000_000_000f * 1.8f) %
                                    (2 * PI).toFloat()
                        previous = now
                    }
                }
            }
        }
    }
    val active = MaterialTheme.colorScheme.primary
    val inactive = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.16f)
    val path = remember { Path() }
    Slider(
        value =
            (if (dragging || !playing || !settings.animations) value else position.floatValue)
                .coerceIn(0f, maximum.coerceAtLeast(1f)),
        onValueChange = {
            dragging = true
            onValue(it)
        },
        onValueChangeFinished = {
            onFinish()
            dragging = false
        },
        valueRange = 0f..maximum.coerceAtLeast(1f),
        modifier =
            modifier.fillMaxWidth().height(48.dp).semantics {
                contentDescription = strings[R.string.seek_bar]
            },
        thumb = {
            Box(
                Modifier.size(if (bar) 4.dp else 20.dp, if (bar) 24.dp else 20.dp)
                    .background(active, if (bar) RoundedCornerShape(2.dp) else CircleShape)
            )
        },
        track = { slider ->
            Canvas(Modifier.fillMaxWidth().height(24.dp)) {
                val handle =
                    size.width * (slider.value / maximum.coerceAtLeast(1f)).coerceIn(0f, 1f)
                drawSeekTrack(
                    0f,
                    size.width,
                    handle,
                    style,
                    phase.floatValue,
                    active,
                    inactive,
                    path,
                )
            }
        },
    )
}
