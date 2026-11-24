@file:OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)

package dev.lad.ui

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Typography
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.lad.R

object LadSpacing {
    val micro = 4.dp
    val small = 8.dp
    val inline = 12.dp
    val medium = 16.dp
    val page = 20.dp
    val section = 28.dp
    val touch = 48.dp
    val mini = 68.dp
    val nav = 72.dp
}

object LadShapes {
    // Material 3 Expressive 10-step corner scale: 0 / 4 / 8 / 12 / 16 / 20 / 28 / 32 / 48 / full
    val tiny = RoundedCornerShape(8.dp)
    val small = RoundedCornerShape(12.dp)
    val medium = RoundedCornerShape(16.dp)
    val card = RoundedCornerShape(20.dp)
    val artwork = RoundedCornerShape(28.dp)
    val hero = RoundedCornerShape(32.dp)
    val player = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
    val selected = RoundedCornerShape(16.dp)
    val control = RoundedCornerShape(28.dp)
}

object LadMotion {
    const val quick = 160
    const val standard = 260
    const val expressive = 420
    const val container = 440
    const val palette = 650
    val easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    fun duration(value: Int, enabled: Boolean) = if (enabled) value else 0

    // Material 3 Expressive spring tokens (damping / stiffness from M3E tokens)
    fun <T> fastSpatial(enabled: Boolean = true): FiniteAnimationSpec<T> =
        if (enabled) spring(dampingRatio = 0.6f, stiffness = 800f) else snap()

    fun <T> defaultSpatial(enabled: Boolean = true): FiniteAnimationSpec<T> =
        if (enabled) spring(dampingRatio = 0.8f, stiffness = 380f) else snap()

    fun <T> slowSpatial(enabled: Boolean = true): FiniteAnimationSpec<T> =
        if (enabled) spring(dampingRatio = 0.8f, stiffness = 200f) else snap()

    fun <T> fastEffects(enabled: Boolean = true): FiniteAnimationSpec<T> =
        if (enabled) spring(dampingRatio = 1.0f, stiffness = 3800f) else snap()

    fun <T> defaultEffects(enabled: Boolean = true): FiniteAnimationSpec<T> =
        if (enabled) spring(dampingRatio = 1.0f, stiffness = 1600f) else snap()

    fun <T> slowEffects(enabled: Boolean = true): FiniteAnimationSpec<T> =
        if (enabled) spring(dampingRatio = 1.0f, stiffness = 800f) else snap()
}

object LadColors {
    val night = Color(0xFF0C1013)
    val surfaceLowest = Color(0xFF080B0D)
    val surfaceLow = Color(0xFF151B20)
    val surface = Color(0xFF1B2329)
    val surfaceHigh = Color(0xFF232C34)
    val surfaceHighest = Color(0xFF2C3740)
    val ink = Color(0xFFF0F3F6)
    val muted = Color(0xFFB9C3CB)
    val paper = Color(0xFFF8F9FA)
    val scrim = Color(0xFF06090B)
}

object LadElevation {
    val player = 3.dp
    val flat = 0.dp
}

val RiffFont =
    FontFamily(
        listOf(100, 200, 300, 400, 500, 600, 700, 800, 900).map {
            Font(
                R.font.google_sans,
                FontWeight(it),
                variationSettings =
                    androidx.compose.ui.text.font.FontVariation.Settings(
                        androidx.compose.ui.text.font.FontVariation.weight(it)
                    ),
            )
        }
    )

val LadTypography =
    Typography(
        displayLarge = TextStyle(fontFamily = RiffFont, fontSize = 57.sp, lineHeight = 64.sp),
        displayMedium = TextStyle(fontFamily = RiffFont, fontSize = 45.sp, lineHeight = 52.sp),
        titleSmall =
            TextStyle(
                fontFamily = RiffFont,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                fontWeight = FontWeight.Medium,
            ),
        displaySmall =
            TextStyle(
                fontFamily = RiffFont,
                fontSize = 36.sp,
                lineHeight = 40.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = (-1).sp,
            ),
        headlineLarge =
            TextStyle(
                fontFamily = RiffFont,
                fontSize = 30.sp,
                lineHeight = 35.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-.6).sp,
            ),
        headlineMedium =
            TextStyle(
                fontFamily = RiffFont,
                fontSize = 26.sp,
                lineHeight = 32.sp,
                fontWeight = FontWeight.Bold,
            ),
        headlineSmall =
            TextStyle(
                fontFamily = RiffFont,
                fontSize = 22.sp,
                lineHeight = 28.sp,
                fontWeight = FontWeight.Bold,
            ),
        titleLarge =
            TextStyle(
                fontFamily = RiffFont,
                fontSize = 22.sp,
                lineHeight = 28.sp,
                fontWeight = FontWeight.SemiBold,
            ),
        titleMedium =
            TextStyle(
                fontFamily = RiffFont,
                fontSize = 16.sp,
                lineHeight = 22.sp,
                fontWeight = FontWeight.SemiBold,
            ),
        bodyLarge = TextStyle(fontFamily = RiffFont, fontSize = 16.sp, lineHeight = 24.sp),
        bodyMedium = TextStyle(fontFamily = RiffFont, fontSize = 14.sp, lineHeight = 20.sp),
        bodySmall = TextStyle(fontFamily = RiffFont, fontSize = 12.sp, lineHeight = 16.sp),
        labelLarge =
            TextStyle(
                fontFamily = RiffFont,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                fontWeight = FontWeight.SemiBold,
            ),
        labelMedium =
            TextStyle(
                fontFamily = RiffFont,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                fontWeight = FontWeight.Medium,
            ),
        labelSmall =
            TextStyle(
                fontFamily = RiffFont,
                fontSize = 11.sp,
                lineHeight = 16.sp,
                fontWeight = FontWeight.Medium,
            ),
    )
