@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package dev.lad.ui

import android.graphics.RectF
import androidx.compose.animation.core.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.*

val BitsEase = LadMotion.easing

internal val LocalCoverMorphShape = compositionLocalOf<Shape?> { null }

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

/**
 * Exact polar distance from center to a rounded square in [-1, 1]^2 with normalized corner radius c in [0, 1].
 */
internal fun roundedSquarePolarRadius(angleRad: Double, cornerFraction: Float): Float {
    val c = cornerFraction.coerceIn(0.02f, 1f).toDouble()
    if (c >= 0.999) return 1f
    val u = abs(cos(angleRad))
    val v = abs(sin(angleRad))
    val a = 1.0 - c
    if (u > 1e-6 && v / u <= a) return (1.0 / u).toFloat()
    if (v > 1e-6 && u / v <= a) return (1.0 / v).toFloat()
    val sum = u + v
    val b = a * sum
    val disc = (b * b - (2.0 * a * a - c * c)).coerceAtLeast(0.0)
    return (b + sqrt(disc)).toFloat()
}

/**
 * Normalized polar radius r(theta) for Material 3 Expressive silhouette shapes.
 * Note: for "rounded", normalized to fit inscribed unit square cleanly alongside radial shapes.
 */
internal fun expressivePolarRadius(
    shape: String,
    angleRad: Double,
    cornerFraction: Float = 0.24f,
): Float =
    when (shape) {
        "circle" -> 1f
        "flower" -> (0.91 + 0.09 * cos(angleRad * 10.0)).toFloat()
        "clover" ->
            (0.84 - 0.14 * cos(angleRad * 4.0) + 0.02 * cos(angleRad * 8.0)).toFloat()
        "cookie" -> {
            val phi = angleRad + PI / 2.0
            (0.90 + 0.085 * cos(phi * 7.0) + 0.015 * cos(phi * 14.0)).toFloat()
        }
        "sunny" ->
            (0.92 + 0.065 * cos(angleRad * 12.0) + 0.015 * cos(angleRad * 24.0)).toFloat()
        "squircle" -> {
            val u = abs(cos(angleRad)).pow(4.2)
            val v = abs(sin(angleRad)).pow(4.2)
            (1.0 / (u + v).pow(1.0 / 4.2)).toFloat().coerceAtMost(1.12f)
        }
        else -> roundedSquarePolarRadius(angleRad, cornerFraction)
    }

private fun polarGenericShape(shapeName: String): Shape = GenericShape { size, _ ->
    val cx = size.width / 2f
    val cy = size.height / 2f
    val steps = 180
    for (i in 0..steps) {
        val angle = i * 2.0 * PI / steps
        val r = expressivePolarRadius(shapeName, angle)
        val x = (cx + cx * r * cos(angle).toFloat()).coerceIn(0f, size.width)
        val y = (cy + cy * r * sin(angle).toFloat()).coerceIn(0f, size.height)
        if (i == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}

val FlowerShape: Shape = polarGenericShape("flower")
val CloverShape: Shape = polarGenericShape("clover")
val CookieShape: Shape = polarGenericShape("cookie")
val SunnyShape: Shape = polarGenericShape("sunny")
val SquircleShape: Shape = polarGenericShape("squircle")

/**
 * Continuous Material 3 Expressive polar shape morph between any chosen pause shape (morphFraction = 0f)
 * and any chosen playing shape (morphFraction = 1f), with smooth cubic easing across intermediate silhouettes.
 */
internal class MorphedCoverShape(
    private val pauseShape: String,
    private val playShape: String,
    private val morphFraction: Float,
    private val cornerFraction: Float = 0.22f,
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val t = morphFraction.coerceIn(0f, 1f)
        // Subtle mid-morph organic tension pinch (M3E liquid morph principle)
        val midPinch =
            if (pauseShape != playShape) 1f - 0.035f * sin(t * PI.toFloat()) else 1f
        val path =
            Path().apply {
                val steps = 200
                for (i in 0..steps) {
                    val angle = i * 2.0 * PI / steps
                    val fromR = expressivePolarRadius(pauseShape, angle, cornerFraction)
                    val toR = expressivePolarRadius(playShape, angle, cornerFraction)
                    val r = (fromR + (toR - fromR) * t) * midPinch
                    val x = (cx + cx * r * cos(angle).toFloat()).coerceIn(0f, size.width)
                    val y = (cy + cy * r * sin(angle).toFloat()).coerceIn(0f, size.height)
                    if (i == 0) moveTo(x, y) else lineTo(x, y)
                }
                close()
            }
        return Outline.Generic(path)
    }
}

internal fun buildCoverAndroidPath(
    rect: RectF,
    coverShape: String,
    roundPx: Float = 64f,
): android.graphics.Path =
    when (coverShape) {
        "circle" ->
            android.graphics.Path().apply {
                addOval(rect, android.graphics.Path.Direction.CW)
            }
        "flower",
        "clover",
        "cookie",
        "sunny",
        "squircle" ->
            android.graphics.Path().apply {
                val cx = rect.centerX()
                val cy = rect.centerY()
                val rx = rect.width() / 2f
                val ry = rect.height() / 2f
                val steps = 200
                for (i in 0..steps) {
                    val angle = i * 2.0 * PI / steps
                    val r = expressivePolarRadius(coverShape, angle)
                    val x = cx + rx * r * cos(angle).toFloat()
                    val y = cy + ry * r * sin(angle).toFloat()
                    if (i == 0) moveTo(x, y) else lineTo(x, y)
                }
                close()
            }
        else ->
            android.graphics.Path().apply {
                addRoundRect(rect, roundPx, roundPx, android.graphics.Path.Direction.CW)
            }
    }
