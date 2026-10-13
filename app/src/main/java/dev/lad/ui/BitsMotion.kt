package dev.lad.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.*
import androidx.compose.ui.unit.dp
import kotlin.math.*

val BitsEase = LadMotion.easing

@Composable
fun BitsReveal(
    modifier: Modifier = Modifier,
    key: Any = Unit,
    delayMs: Int = 0,
    enabled: Boolean = LocalLadSettings.current.animations,
    content: @Composable () -> Unit,
) {
    var ready by remember(key) { mutableStateOf(!enabled) }
    LaunchedEffect(key, enabled) {
        if (enabled) kotlinx.coroutines.delay(delayMs.toLong())
        ready = true
    }
    val value by
        animateFloatAsState(
            if (ready) 1f else 0f,
            tween(LadMotion.duration(LadMotion.expressive, enabled), easing = BitsEase),
            label = "AnimatedContent",
        )
    Box(
        modifier.graphicsLayer {
            alpha = value
            translationY = (1 - value) * 12.dp.toPx()
        }
    ) {
        content()
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun BitsTitle(
    text: String,
    modifier: Modifier = Modifier,
    style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.headlineLarge,
) {
    BitsReveal(key = text, modifier = modifier) {
        Text(text, style = style, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
    }
}

@Composable
fun BitsFlow(seed: Color, enabled: Boolean, animations: Boolean, modifier: Modifier = Modifier) {
    if (!enabled) return
    Canvas(modifier) {
        drawRect(
            Brush.radialGradient(
                listOf(seed.copy(alpha = 0.12f), Color.Transparent),
                center =
                    androidx.compose.ui.geometry.Offset(size.width * 0.75f, size.height * 0.18f),
                radius = size.width * 0.95f,
            )
        )
    }
}

val FlowerShape: Shape = GenericShape { size, _ ->
    val cx = size.width / 2
    val cy = size.height / 2
    for (i in 0..180) {
        val angle = i * 2 * PI / 180
        val radius = .91 + .09 * cos(angle * 10)
        val x = (cx + cx * radius * cos(angle)).toFloat()
        val y = (cy + cy * radius * sin(angle)).toFloat()
        if (i == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}
