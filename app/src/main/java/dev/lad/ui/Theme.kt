@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package dev.lad.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import dev.lad.data.Settings

@Composable
fun LadTheme(settings: Settings, content: @Composable () -> Unit) {
    val dark =
        when (settings.theme) {
            "dark" -> true
            "light" -> false
            else -> isSystemInDarkTheme()
        }
    val accent = AccentColors[settings.accent] ?: AccentColors.getValue("violet")
    val base =
        if (settings.dynamicColor && Build.VERSION.SDK_INT >= 31) {
            if (dark) dynamicDarkColorScheme(androidx.compose.ui.platform.LocalContext.current)
            else dynamicLightColorScheme(androidx.compose.ui.platform.LocalContext.current)
        } else coverScheme(accent, if (dark) darkColorScheme() else lightColorScheme())
    val styled =
        if (dark)
            base.copy(
                surface = LadColors.night,
                background = LadColors.night,
                surfaceContainerLow = LadColors.surfaceLow,
                surfaceContainer = LadColors.surface,
                surfaceContainerHigh = LadColors.surfaceHigh,
                onSurface = LadColors.ink,
                onSurfaceVariant = LadColors.muted,
            )
        else base
    val scheme =
        if (dark && settings.pureBlack)
            styled.copy(
                surface = Color.Black,
                background = Color.Black,
                surfaceContainerLow = Color.Black,
            )
        else styled
    CompositionLocalProvider(LocalLadSettings provides settings) {
        MaterialExpressiveTheme(
            colorScheme = scheme,
            typography = LadTypography,
            shapes =
                Shapes(
                    small = LadShapes.small,
                    medium = LadShapes.medium,
                    large = LadShapes.card,
                    extraLarge = LadShapes.hero,
                ),
            content = content,
        )
    }
}
