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
    val dynamic = settings.dynamicColor && Build.VERSION.SDK_INT >= 31
    val base =
        if (dynamic) {
            if (dark) dynamicDarkColorScheme(androidx.compose.ui.platform.LocalContext.current)
            else dynamicLightColorScheme(androidx.compose.ui.platform.LocalContext.current)
        } else coverScheme(accent, if (dark) darkColorScheme() else lightColorScheme())
    val styled =
        if (dark && !dynamic)
            base.copy(
                surface = LadColors.night,
                background = LadColors.night,
                surfaceContainerLowest = LadColors.surfaceLowest,
                surfaceContainerLow = LadColors.surfaceLow,
                surfaceContainer = LadColors.surface,
                surfaceContainerHigh = LadColors.surfaceHigh,
                surfaceContainerHighest = LadColors.surfaceHighest,
                onSurface = LadColors.ink,
                onSurfaceVariant = LadColors.muted,
            )
        else base
    val scheme =
        if (dark && settings.pureBlack)
            styled.copy(
                surface = Color.Black,
                background = Color.Black,
                surfaceContainerLowest = Color.Black,
                surfaceContainerLow = Color(0xFF0A0D10),
                surfaceContainer = Color(0xFF12161A),
                surfaceContainerHigh = Color(0xFF1B2127),
                surfaceContainerHighest = Color(0xFF242B33),
            )
        else styled
    val textResources = dev.lad.i18n.rememberLocalizedText()
    CompositionLocalProvider(
        LocalLadSettings provides settings,
        dev.lad.i18n.LocalTextResources provides textResources,
    ) {
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
