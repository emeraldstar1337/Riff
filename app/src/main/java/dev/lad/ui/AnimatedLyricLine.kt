package dev.lad.ui

import android.os.SystemClock
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.ResolvedTextDirection
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import dev.lad.domain.LyricLine
import dev.lad.playback.PlaybackState
import kotlin.math.PI
import kotlin.math.sin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive

@Composable
internal fun LyricLineText(
    line: LyricLine,
    active: Boolean,
    positionMs: Long,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    clock: PlaybackState? = null,
    endTimeMs: Long? = null,
    distance: Int = 0,
) {
    val appearance = LocalLadSettings.current
    val animations = appearance.animations
    val owner = LocalLifecycleOwner.current
    val time = remember(line) { mutableLongStateOf(positionMs) }
    LaunchedEffect(active, clock, positionMs, animations, owner) {
        time.longValue = positionMs
        if (active && clock?.playing == true && animations && line.words.isNotEmpty()) {
            owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                var previous = 0L
                while (
                    isActive &&
                        (currentCoroutineContext()[MotionDurationScale]?.scaleFactor ?: 1f) > 0f
                ) {
                    withFrameNanos { frame ->
                        if (frame - previous >= 16_666_666L) {
                            time.longValue =
                                clock.positionMs +
                                    ((SystemClock.elapsedRealtime() - clock.positionTimestampMs)
                                            .coerceAtLeast(0) * clock.speed)
                                        .toLong()
                            previous = frame
                        }
                    }
                    val finish =
                        line.words.last().endTimeMs
                            ?: endTimeMs
                            ?: (line.words.last().timeMs + 1200)
                    if (time.longValue >= finish) break
                }
            }
        }
    }
    val focus by
        animateFloatAsState(
            if (active) 1f else 0f,
            tween(if (animations) 640 else 0),
            label = "lyric presence",
        )
    val emphasis by
        animateFloatAsState(
            if (active) 1.035f else 1f,
            if (animations) spring(dampingRatio = .85f, stiffness = 105f) else snap(),
            label = "lyric focus",
        )
    val lift by
        animateFloatAsState(
            if (active) -2f else 0f,
            tween(if (animations) 640 else 0, easing = FastOutSlowInEasing),
            label = "lyric rise",
        )
    val softFocus by
        animateFloatAsState(
            if (active || distance < 2 || !animations) 0f else distance.coerceAtMost(4) * .35f,
            tween(if (animations) 640 else 0),
            label = "lyric depth",
        )
    val scheme = MaterialTheme.colorScheme
    val dim = appearance.lyricsDim.coerceIn(.12f, .6f)
    val color by
        animateColorAsState(
            scheme.onSurface.copy(
                alpha =
                    when {
                        active && line.words.isEmpty() -> 1f
                        active -> .32f
                        else ->
                            (dim / (1f + (distance - 1).coerceAtLeast(0) * .14f)).coerceAtLeast(
                                .12f
                            )
                    }
            ),
            tween(if (animations) 640 else 0),
            label = "lyric light",
        )
    var layout by
        remember(line.text, appearance.lyricsSize, appearance.lyricsAlignment) {
            mutableStateOf<TextLayoutResult?>(null)
        }
    val mask = remember { Path() }
    val highlight = scheme.onSurface
    Text(
        line.text.ifBlank { "♪" },
        modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .graphicsLayer {
                scaleX = emphasis
                scaleY = emphasis
                translationY = lift.dp.toPx()
                renderEffect =
                    if (softFocus > .01f && android.os.Build.VERSION.SDK_INT >= 31)
                        BlurEffect(softFocus.dp.toPx(), softFocus.dp.toPx(), TileMode.Decal)
                    else null
            }
            .drawWithCache {
                val result = layout
                val layoutLength = result?.layoutInput?.text?.length ?: 0
                val wordBoxes =
                    line.words.map { word ->
                        if (result != null && word.start >= 0 && word.end <= layoutLength)
                            (word.start until word.end).map { result.getBoundingBox(it) }
                        else emptyList()
                    }
                onDrawWithContent {
                    drawContent()
                    if (focus > .001f && result != null && line.words.isNotEmpty()) {
                        val now = time.longValue
                        line.words.forEachIndexed { index, word ->
                            val until =
                                word.endTimeMs
                                    ?: line.words.getOrNull(index + 1)?.timeMs
                                    ?: endTimeMs
                                    ?: (word.timeMs + 1200)
                            val progress =
                                if (!animations && now >= word.timeMs) 1f
                                else
                                    ((now - word.timeMs).toFloat() /
                                            (until - word.timeMs).coerceAtLeast(1))
                                        .coerceIn(0f, 1f)
                            if (
                                progress > 0f &&
                                    word.start >= 0 &&
                                    word.start < word.end &&
                                    word.end <= layoutLength
                            ) {
                                val boxes = wordBoxes[index]
                                var remaining =
                                    boxes.sumOf { it.width.toDouble() }.toFloat() * progress
                                mask.reset()
                                boxes.forEachIndexed { offset, box ->
                                    val amount = remaining.coerceIn(0f, box.width)
                                    if (amount > 0f) {
                                        val rtl =
                                            result.getBidiRunDirection(word.start + offset) ==
                                                ResolvedTextDirection.Rtl
                                        mask.addRect(
                                            Rect(
                                                if (rtl) box.right - amount else box.left,
                                                box.top - 4.dp.toPx(),
                                                if (rtl) box.right else box.left + amount,
                                                box.bottom + 4.dp.toPx(),
                                            )
                                        )
                                    }
                                    remaining -= box.width
                                }
                                val rise =
                                    if (animations) sin(progress * PI).toFloat() * 1.5.dp.toPx()
                                    else 0f
                                translate(top = -rise) {
                                    clipPath(mask) {
                                        drawText(
                                            result,
                                            color = highlight.copy(alpha = focus),
                                            shadow =
                                                if (animations && progress < 1f)
                                                    Shadow(
                                                        highlight.copy(alpha = .45f),
                                                        Offset.Zero,
                                                        6.dp.toPx(),
                                                    )
                                                else null,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
        style = lyricTextStyle(appearance),
        textAlign =
            if (appearance.lyricsAlignment == "center") TextAlign.Center else TextAlign.Start,
        fontWeight = FontWeight.Black,
        color = color,
        onTextLayout = { layout = it },
    )
}
