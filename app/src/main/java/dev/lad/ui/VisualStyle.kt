package dev.lad.ui

import android.graphics.drawable.BitmapDrawable
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import coil.imageLoader
import coil.request.ImageRequest
import dev.lad.data.Settings
import dev.lad.domain.CoverPalette
import dev.lad.util.attempt
import kotlin.math.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

val LocalLadSettings = staticCompositionLocalOf { Settings() }
val AccentColors =
    mapOf(
        "violet" to Color(0xFF865CE6),
        "ocean" to Color(0xFF167DB7),
        "mint" to Color(0xFF16836A),
        "rose" to Color(0xFFBB4476),
        "amber" to Color(0xFF956000),
    )

private val paletteCache = object : android.util.LruCache<String, Color?>(96) {}

@Composable
fun artworkSeed(uri: String?, enabled: Boolean): Color? {
    val context = LocalContext.current
    val seed by
        produceState<Color?>(null, uri, enabled) {
            value = null
            if (enabled && !uri.isNullOrBlank())
                value =
                    withContext(Dispatchers.IO) {
                        paletteCache.get(uri)?.let {
                            return@withContext it
                        }
                        attempt {
                            val result =
                                context.imageLoader.execute(
                                    ImageRequest.Builder(context)
                                        .data(uri)
                                        .size(128)
                                        .allowHardware(false)
                                        .build()
                                )
                            val bitmap =
                                (result.drawable as? BitmapDrawable)?.bitmap ?: return@attempt null
                            val pixels = IntArray(bitmap.width * bitmap.height)
                            bitmap.getPixels(
                                pixels,
                                0,
                                bitmap.width,
                                0,
                                0,
                                bitmap.width,
                                bitmap.height,
                            )
                            CoverPalette.seed(pixels)?.let {
                                Color(it).also { color -> paletteCache.put(uri, color) }
                            }
                        }
                            .getOrNull()
                    }
        }
    return seed
}

fun coverScheme(seed: Color, base: ColorScheme): ColorScheme {
    val dark = base.surface.luminance() < 0.4f
    val surface = lerp(base.surface, seed, if (dark) 0.12f else 0.07f)
    val primary = lerp(seed, if (dark) Color.White else Color.Black, if (dark) 0.38f else 0.18f)
    val container = lerp(surface, seed, if (dark) 0.24f else 0.15f)
    return base.copy(
        primary = primary,
        onPrimary = if (primary.luminance() > 0.179f) Color.Black else Color.White,
        primaryContainer = container,
        onPrimaryContainer = base.onSurface,
        secondaryContainer = container,
        onSecondaryContainer = base.onSurface,
        surface = surface,
        background = surface,
        surfaceContainer = lerp(surface, seed, 0.06f),
        surfaceContainerLow = surface,
        surfaceContainerHigh = lerp(surface, seed, 0.1f),
    )
}

fun recapScheme(seed: Color): ColorScheme {
    val backdrop = lerp(Color(0xFF101018), seed, 0.16f)
    val accent = lerp(seed, Color.White, 0.60f)
    val panel = lerp(backdrop, seed, 0.25f)
    return darkColorScheme(
        primary = accent,
        onPrimary = Color(0xFF111018),
        primaryContainer = panel,
        onPrimaryContainer = Color.White,
        secondary = accent,
        onSecondary = Color(0xFF111018),
        secondaryContainer = panel,
        onSecondaryContainer = Color.White,
        tertiary = accent,
        onTertiary = Color(0xFF111018),
        tertiaryContainer = panel,
        onTertiaryContainer = Color.White,
        surface = backdrop,
        background = backdrop,
        onSurface = Color.White,
        onBackground = Color.White,
        onSurfaceVariant = Color(0xFFE4E0EA),
        surfaceContainer = panel,
        surfaceContainerLow = backdrop,
        surfaceContainerHigh = panel,
    )
}

@Composable
fun AmbientBackdrop(
    seed: Color,
    enabled: Boolean,
    animations: Boolean,
    modifier: Modifier = Modifier,
    artwork: String? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val settings = LocalLadSettings.current
    val owner = LocalLifecycleOwner.current

    val motion =
        produceState(
            0f,
            enabled,
            settings.glowMotion,
            settings.animations,
            settings.glowShapes,
            settings.motionSpeed,
            animations,
            owner,
        ) {
            if (
                enabled &&
                    (settings.glowMotion || settings.glowShapes) &&
                    settings.animations &&
                    animations
            ) {
                owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                    while (isActive) {
                        value =
                            ((value +
                                    0.083f * settings.motionSpeed.coerceIn(0.3f, 1.5f) * PI / 18) %
                                    (2 * PI))
                                .toFloat()
                        delay(83)
                    }
                }
            }
        }
    Box(modifier.background(scheme.surface)) {
        if (enabled) {
            val dark = scheme.surface.luminance() < 0.4f
            val saturation = settings.glowSaturation.coerceIn(0f, 1f)
            val gray = Color(seed.luminance(), seed.luminance(), seed.luminance(), 1f)
            val tint = lerp(gray, seed, saturation)
            val strength = settings.glowStrength.coerceIn(0f, 1.8f)
            val radius = settings.glowRadius.coerceIn(0.5f, 1.6f)
            Box(
                Modifier.matchParentSize().drawWithCache {
                    val glow = ((if (dark) 0.70f else 0.30f) * strength).coerceAtMost(0.95f)
                    val main =
                        Brush.radialGradient(
                            listOf(
                                tint.copy(alpha = glow),
                                tint.copy(alpha = glow * 0.45f),
                                Color.Transparent,
                            ),
                            center = Offset(size.width * 0.5f, size.height * 0.36f),
                            radius = size.width * 0.82f * radius,
                        )
                    val secondary =
                        Brush.radialGradient(
                            listOf(tint.copy(alpha = glow * 0.48f), Color.Transparent),
                            center = Offset(size.width * 0.15f, size.height * 0.2f),
                            radius = size.width * 0.9f * radius,
                        )
                    fun flower(lobes: Int, radius: Float): Path =
                        Path().apply {
                            for (step in 0..180) {
                                val angle = step / 180f * 2f * PI.toFloat()
                                val r = radius * (0.88f + 0.12f * cos(lobes * angle))
                                val x = cos(angle) * r
                                val y = sin(angle) * r
                                if (step == 0) moveTo(x, y) else lineTo(x, y)
                            }
                            close()
                        }
                    val shapes =
                        listOf(
                            flower(8, size.width * 0.27f),
                            flower(3, size.width * 0.20f),
                            flower(4, size.width * 0.17f),
                        )
                    val centers =
                        listOf(
                            Offset(size.width * 0.15f, size.height * 0.18f),
                            Offset(size.width * 0.90f, size.height * 0.43f),
                            Offset(size.width * 0.25f, size.height * 0.78f),
                        )
                    onDrawBehind {
                        val phase = motion.value
                        drawRect(
                            main,
                            alpha = if (settings.glowMotion) 0.94f + sin(phase) * 0.06f else 1f,
                        )
                        drawRect(secondary)
                        if (settings.glowShapes)
                            shapes.forEachIndexed { i, path ->
                                val center = centers[i]
                                withTransform({
                                    translate(
                                        center.x + sin(phase + i * 2f) * size.width * 0.05f,
                                        center.y + cos(phase + i * 1.7f) * size.height * 0.025f,
                                    )
                                    rotate(sin(phase + i) * 14f, Offset.Zero)
                                }) {
                                    drawPath(
                                        path,
                                        tint.copy(
                                            alpha = settings.shapeOpacity.coerceIn(0.02f, 0.16f)
                                        ),
                                    )
                                }
                            }
                    }
                }
            )
        }
        content()
    }
}
