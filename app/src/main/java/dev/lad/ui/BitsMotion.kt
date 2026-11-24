@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package dev.lad.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
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
        if (enabled && delayMs > 0) kotlinx.coroutines.delay(delayMs.toLong())
        ready = true
    }
    // M3 Expressive: split spatial (position/scale bounce) and effects (opacity, no overshoot)
    val opacity by
        animateFloatAsState(
            if (ready) 1f else 0f,
            if (enabled) MaterialTheme.motionScheme.defaultEffectsSpec() else snap(),
            label = "reveal opacity",
        )
    val motion by
        animateFloatAsState(
            if (ready) 1f else 0f,
            if (enabled) MaterialTheme.motionScheme.defaultSpatialSpec() else snap(),
            label = "reveal motion",
        )
    Box(
        modifier.graphicsLayer {
            alpha = opacity.coerceIn(0f, 1f)
            val s = 0.96f + 0.04f * motion
            scaleX = s
            scaleY = s
            translationY = (1f - motion) * 14.dp.toPx()
        }
    ) {
        content()
    }
}

fun Modifier.expressivePress(
    interactionSource: MutableInteractionSource,
    pressedScale: Float = 0.96f,
): Modifier = composed {
    val animations = LocalLadSettings.current.animations
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by
        animateFloatAsState(
            if (pressed && animations) pressedScale else 1f,
            if (animations) MaterialTheme.motionScheme.fastSpatialSpec() else snap(),
            label = "expressive press",
        )
    graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

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
