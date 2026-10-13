package dev.lad.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.lad.R
import dev.lad.i18n.rememberText
import kotlin.math.PI
import kotlin.math.sin

@Composable
internal fun SeekStyleChoices(current: String, onSelect: (String) -> Unit) {
    val strings = rememberText()
    val choices =
        listOf(
            "wave_dot" to R.string.wave_dot,
            "wave_bar" to R.string.wave_bar,
            "line_dot" to R.string.line_dot,
            "line_bar" to R.string.line_bar,
        )
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        choices.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                pair.forEach { (value, label) ->
                    val selected = current == value
                    Surface(
                        onClick = { onSelect(value) },
                        modifier =
                            Modifier.weight(1f).semantics {
                                this.selected = selected
                                role = Role.RadioButton
                            },
                        shape = RoundedCornerShape(if (selected) 20.dp else 28.dp),
                        color =
                            if (selected) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor =
                            if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                            else MaterialTheme.colorScheme.onSurface,
                    ) {
                        Column(
                            Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(18.dp),
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    strings[label],
                                    Modifier.weight(1f),
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.labelLarge,
                                )
                                if (selected) Icon(Icons.Default.Check, null, Modifier.size(18.dp))
                            }
                            SeekStylePreview(value)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SeekStylePreview(style: String) {
    val color = LocalContentColor.current
    val path = remember { Path() }
    Canvas(Modifier.fillMaxWidth().height(28.dp)) {
        val inset = 12.dp.toPx()
        val x = inset + (size.width - inset * 2) * 0.48f
        drawSeekTrack(
            inset,
            size.width - inset,
            x,
            style,
            0f,
            color,
            color.copy(alpha = 0.22f),
            path,
        )
        val y = size.height / 2
        if (style.endsWith("bar"))
            drawLine(
                color,
                Offset(x, y - 12.dp.toPx()),
                Offset(x, y + 12.dp.toPx()),
                4.dp.toPx(),
                StrokeCap.Round,
            )
        else drawCircle(color, 10.dp.toPx(), Offset(x, y))
    }
}

internal fun DrawScope.drawSeekTrack(
    start: Float,
    end: Float,
    handle: Float,
    style: String,
    phase: Float,
    active: Color,
    inactive: Color,
    path: Path,
) {
    val wavy = style.startsWith("wave")
    val stroke = (if (wavy) 2.5.dp else 4.dp).toPx()
    val radius = (if (style.endsWith("bar")) 2.dp else 10.dp).toPx()
    val gap = radius + stroke / 2 + 3.dp.toPx()
    val playedEnd = (handle - gap).coerceAtLeast(start)
    val remainingStart = (handle + gap).coerceAtMost(end)
    val y = size.height / 2
    if (remainingStart < end)
        drawLine(
            inactive,
            Offset(remainingStart, y),
            Offset(end, y),
            stroke,
            StrokeCap.Round,
        )
    if (playedEnd <= start) return
    if (!wavy) {
        drawLine(active, Offset(start, y), Offset(playedEnd, y), stroke, StrokeCap.Round)
        return
    }
    path.reset()
    val wavelength = 22.dp.toPx()
    val amplitude = 2.5.dp.toPx()
    val taper = 8.dp.toPx()
    var x = start
    while (x < playedEnd) {
        val envelope = minOf(1f, (x - start) / taper, (playedEnd - x) / taper)
        val waveY =
            y + sin((x - start) / wavelength * 2 * PI.toFloat() - phase) * amplitude * envelope
        if (x == start) path.moveTo(x, waveY) else path.lineTo(x, waveY)
        x += 1.dp.toPx()
    }
    path.lineTo(playedEnd, y)
    drawPath(path, active, style = Stroke(stroke, cap = StrokeCap.Round))
}
