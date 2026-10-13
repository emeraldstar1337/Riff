package dev.lad.ui

import androidx.compose.material3.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.*
import org.junit.Test

class ColorContrastTest {
    private fun contrast(a: Color, b: Color): Float {
        val x = a.luminance()
        val y = b.luminance()
        return (maxOf(x, y) + 0.05f) / (minOf(x, y) + 0.05f)
    }

    @Test
    fun recapTextAndControlsHaveReadableOpaqueColorPairs() {
        (AccentColors.values + listOf(Color.Black, Color.White, Color.Red, Color.Green, Color.Blue))
            .forEach { seed ->
                val s = recapScheme(seed)
                assertTrue("Surface for $seed", contrast(s.onSurface, s.surface) >= 4.5f)
                assertTrue(
                    "Panel for $seed",
                    contrast(s.onPrimaryContainer, s.primaryContainer) >= 4.5f,
                )
                assertTrue("Button for $seed", contrast(s.onPrimary, s.primary) >= 4.5f)
            }
    }

    @Test
    fun playerColorPairsWorkInBothThemes() {
        listOf(darkColorScheme(), lightColorScheme()).forEach { base ->
            (AccentColors.values + listOf(Color.Black, Color.White)).forEach { seed ->
                val s = coverScheme(seed, base)
                assertTrue(contrast(s.onSurface, s.surface) >= 4.5f)
                assertTrue(contrast(s.onPrimary, s.primary) >= 4.5f)
                assertTrue(contrast(s.onPrimaryContainer, s.primaryContainer) >= 4.5f)
            }
        }
    }
}
