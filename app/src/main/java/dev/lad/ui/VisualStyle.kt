package dev.lad.ui

import android.graphics.drawable.BitmapDrawable
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import coil.imageLoader
import coil.request.ImageRequest
import dev.lad.LadApplication
import dev.lad.data.Settings
import dev.lad.data.Track
import dev.lad.domain.CoverPalette
import dev.lad.util.attempt
import kotlin.math.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
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
            if (!enabled) value = null
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
                                    (result.drawable as? BitmapDrawable)?.bitmap
                                        ?: return@attempt null
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

@Composable
fun trackSeed(track: Track, enabled: Boolean): Color? {
    val appearance = (LocalContext.current.applicationContext as? LadApplication)?.trackAppearance
    val revision = appearance?.changes?.collectAsStateWithLifecycle()?.value ?: 0
    val custom =
        remember(track.albumId, track.album, track.artist, revision, appearance) {
            appearance?.albumAccent(track)
        }
    val automatic = artworkSeed(track.artwork, enabled && custom == null)
    return if (enabled && custom != null) Color(custom) else automatic
}

fun ambientShapeColor(seed: Color, dark: Boolean): Color {
    val light = seed.luminance()
    val amount =
        if (dark) ((.35f - light) / .2f).coerceIn(0f, 1f) * .8f
        else ((light - .6f) / .3f).coerceIn(0f, 1f) * .35f
    return lerp(seed, if (dark) Color.White else Color.Black, amount)
}

private fun shiftHue(color: Color, degrees: Float, saturationScale: Float = 1f): Color {
    val r = color.red
    val g = color.green
    val b = color.blue
    val max = maxOf(r, g, b)
    val min = minOf(r, g, b)
    val l = (max + min) / 2f
    val d = max - min
    if (d < 1e-4f) return color
    val s = (if (l > 0.5f) d / (2f - max - min) else d / (max + min)).coerceIn(0f, 1f)
    var h =
        when (max) {
            r -> ((g - b) / d + (if (g < b) 6f else 0f)) * 60f
            g -> ((b - r) / d + 2f) * 60f
            else -> ((r - g) / d + 4f) * 60f
        }
    h = ((h + degrees) % 360f + 360f) % 360f
    val ns = (s * saturationScale).coerceIn(0f, 1f)
    val c = (1f - abs(2f * l - 1f)) * ns
    val x = c * (1f - abs((h / 60f) % 2f - 1f))
    val m = l - c / 2f
    val (r1, g1, b1) =
        when {
            h < 60f -> Triple(c, x, 0f)
            h < 120f -> Triple(x, c, 0f)
            h < 180f -> Triple(0f, c, x)
            h < 240f -> Triple(0f, x, c)
            h < 300f -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }
    return Color(
        (r1 + m).coerceIn(0f, 1f),
        (g1 + m).coerceIn(0f, 1f),
        (b1 + m).coerceIn(0f, 1f),
        color.alpha,
    )
}

private fun ensureContrast(foreground: Color, background: Color, dark: Boolean, minRatio: Float = 4.6f): Color {
    var result = foreground
    val target = if (dark) Color.White else Color.Black
    repeat(12) {
        val ratio =
            (maxOf(result.luminance(), background.luminance()) + 0.05f) /
                (minOf(result.luminance(), background.luminance()) + 0.05f)
        if (ratio >= minRatio) return result
        result = lerp(result, target, 0.12f)
    }
    return result
}

private fun onColorFor(container: Color): Color =
    if (container.luminance() > 0.179f) Color(0xFF101318) else Color(0xFFF6F7FB)

fun coverScheme(seed: Color, base: ColorScheme): ColorScheme {
    val dark = base.surface.luminance() < 0.4f
    val surface = lerp(base.surface, seed, if (dark) 0.10f else 0.05f)
    val secondarySeed = shiftHue(seed, 18f, 0.52f)
    val tertiarySeed = shiftHue(seed, 58f, 0.90f)

    val primary =
        ensureContrast(
            lerp(seed, if (dark) Color.White else Color.Black, if (dark) 0.36f else 0.20f),
            surface,
            dark,
        )
    val secondary =
        ensureContrast(
            lerp(secondarySeed, if (dark) Color.White else Color.Black, if (dark) 0.38f else 0.22f),
            surface,
            dark,
        )
    val tertiary =
        ensureContrast(
            lerp(tertiarySeed, if (dark) Color.White else Color.Black, if (dark) 0.36f else 0.18f),
            surface,
            dark,
        )

    val primaryContainer = lerp(surface, seed, if (dark) 0.26f else 0.16f)
    val secondaryContainer = lerp(surface, secondarySeed, if (dark) 0.20f else 0.13f)
    val tertiaryContainer = lerp(surface, tertiarySeed, if (dark) 0.25f else 0.16f)

    return base.copy(
        primary = primary,
        onPrimary = onColorFor(primary),
        primaryContainer = primaryContainer,
        onPrimaryContainer = ensureContrast(base.onSurface, primaryContainer, dark),
        secondary = secondary,
        onSecondary = onColorFor(secondary),
        secondaryContainer = secondaryContainer,
        onSecondaryContainer = ensureContrast(base.onSurface, secondaryContainer, dark),
        tertiary = tertiary,
        onTertiary = onColorFor(tertiary),
        tertiaryContainer = tertiaryContainer,
        onTertiaryContainer = ensureContrast(base.onSurface, tertiaryContainer, dark),
        surface = surface,
        background = surface,
        surfaceContainerLowest = lerp(surface, if (dark) Color.Black else Color.White, 0.28f),
        surfaceContainerLow = lerp(surface, seed, if (dark) 0.05f else 0.03f),
        surfaceContainer = lerp(surface, seed, if (dark) 0.10f else 0.06f),
        surfaceContainerHigh = lerp(surface, seed, if (dark) 0.15f else 0.09f),
        surfaceContainerHighest = lerp(surface, seed, if (dark) 0.21f else 0.13f),
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
    // Animate backdrop colors in the draw phase so FullPlayer never recomposes on every frame
    val animatedSurface =
        animateColorAsState(
            scheme.surface,
            if (settings.animations) tween(450, easing = FastOutSlowInEasing) else snap(),
            label = "backdrop surface",
        )
    val animatedSeed =
        animateColorAsState(
            seed,
            if (settings.animations) tween(450, easing = FastOutSlowInEasing) else snap(),
            label = "backdrop seed",
        )
    // Sync ambient drift to native display VSync (60/90/120Hz) instead of 12fps delay(83)
    val motion = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(
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
                var previous = 0L
                val speed = settings.motionSpeed.coerceIn(0.3f, 1.5f)
                while (
                    isActive &&
                        (currentCoroutineContext()[MotionDurationScale]?.scaleFactor ?: 1f) > 0f
                ) {
                    withFrameNanos { now ->
                        if (previous != 0L) {
                            val dt = ((now - previous) / 1_000_000_000f).coerceIn(0f, 0.1f)
                            motion.floatValue =
                                ((motion.floatValue + dt * speed * PI.toFloat() / 18f) %
                                    (2f * PI.toFloat()))
                        }
                        previous = now
                    }
                }
            }
        }
    }
    val saturation = settings.glowSaturation.coerceIn(0f, 1f)
    val strength = settings.glowStrength.coerceIn(0f, 1.8f)
    val radius = settings.glowRadius.coerceIn(0.5f, 1.6f)
    Box(modifier) {
        Box(
            Modifier.matchParentSize().drawWithCache {
                fun flower(lobes: Int, r0: Float): Path =
                    Path().apply {
                        for (step in 0..96) {
                            val angle = step / 96f * 2f * PI.toFloat()
                            val r = r0 * (0.88f + 0.12f * cos(lobes * angle))
                            val x = cos(angle) * r
                            val y = sin(angle) * r
                            if (step == 0) moveTo(x, y) else lineTo(x, y)
                        }
                        close()
                    }
                val centers =
                    listOf(
                        Offset(size.width * 0.15f, size.height * 0.18f),
                        Offset(size.width * 0.90f, size.height * 0.43f),
                        Offset(size.width * 0.16f, size.height * 0.61f),
                    )
                val shapes =
                    if (enabled && settings.glowShapes)
                        (0 until 3).map { i ->
                            when (settings.shapeType) {
                                "circle" -> flower(0, size.width * .20f * settings.shapeScale)
                                "flower" -> flower(8, size.width * .23f * settings.shapeScale)
                                "pebble" -> flower(3, size.width * .23f * settings.shapeScale)
                                else ->
                                    flower(
                                        listOf(8, 3, 4)[i % 3],
                                        size.width *
                                            listOf(.27f, .20f, .17f)[i % 3] *
                                            settings.shapeScale,
                                    )
                            }
                        }
                    else emptyList()
                val mainCenter = Offset(size.width * 0.5f, size.height * 0.36f)
                val mainRadius = (size.width * 0.82f * radius).coerceAtLeast(1f)
                val secCenter = Offset(size.width * 0.15f, size.height * 0.2f)
                val secRadius = (size.width * 0.9f * radius).coerceAtLeast(1f)
                onDrawBehind {
                    val surface = animatedSurface.value
                    drawRect(surface)
                    if (!enabled) return@onDrawBehind
                    val currentSeed = animatedSeed.value
                    val dark = surface.luminance() < 0.4f
                    val lum = currentSeed.luminance()
                    val tint = lerp(Color(lum, lum, lum, 1f), currentSeed, saturation)
                    val glow = ((if (dark) 0.70f else 0.30f) * strength).coerceAtMost(0.95f)
                    val phase = motion.floatValue
                    drawRect(
                        Brush.radialGradient(
                            listOf(
                                tint.copy(alpha = glow),
                                tint.copy(alpha = glow * 0.45f),
                                Color.Transparent,
                            ),
                            center = mainCenter,
                            radius = mainRadius,
                        ),
                        alpha = if (settings.glowMotion) 0.94f + sin(phase) * 0.06f else 1f,
                    )
                    drawRect(
                        Brush.radialGradient(
                            listOf(tint.copy(alpha = glow * 0.48f), Color.Transparent),
                            center = secCenter,
                            radius = secRadius,
                        )
                    )
                    if (shapes.isNotEmpty()) {
                        val shapeTint = ambientShapeColor(tint, dark)
                        val shapeAlpha =
                            (settings.shapeOpacity *
                                    if (dark)
                                        1f + .7f * ((.35f - tint.luminance()) / .2f).coerceIn(0f, 1f)
                                    else 1f)
                                .coerceIn(0.02f, 0.32f)
                        shapes.forEachIndexed { i, path ->
                            val center = centers[i]
                            withTransform({
                                translate(
                                    center.x + sin(phase + i * 2f) * size.width * 0.05f,
                                    center.y + cos(phase + i * 1.7f) * size.height * 0.025f,
                                )
                                rotate(sin(phase + i) * 14f, Offset.Zero)
                            }) {
                                drawPath(path, shapeTint.copy(alpha = shapeAlpha))
                            }
                        }
                    }
                }
            }
        )
        content()
    }
}
