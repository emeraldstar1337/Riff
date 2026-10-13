package dev.lad.domain

import org.junit.Assert.*
import org.junit.Test

class CoverPaletteTest {
    @Test
    fun greenAreaWinsOverRedLogo() {
        val pixels =
            IntArray(1000) {
                when {
                    it < 650 -> 0xff198a57.toInt()
                    it < 800 -> 0xff357f95.toInt()
                    it < 850 -> 0xffed2020.toInt()
                    else -> 0xff181818.toInt()
                }
            }
        val color = CoverPalette.seed(pixels)!!
        assertTrue((color shr 8 and 255) > (color shr 16 and 255) * 2)
    }

    @Test
    fun warmBrownSurvivesDarkPortrait() {
        val pixels = IntArray(1000) { if (it < 550) 0xff70361b.toInt() else 0xff161616.toInt() }
        assertEquals(0xff70361b.toInt(), CoverPalette.seed(pixels))
    }

    @Test
    fun cyanFoliageDoesNotBecomePurple() {
        val color = CoverPalette.seed(IntArray(100) { 0xff189ca0.toInt() })!!
        assertEquals(0xff189ca0.toInt(), color)
    }

    @Test
    fun monochromeAndTransparentHaveNoFalseHue() {
        assertNull(
            CoverPalette.seed(intArrayOf(0xff222222.toInt(), 0xffcccccc.toInt(), 0x0022ff44))
        )
        assertNull(CoverPalette.seed(intArrayOf()))
    }

    @Test
    fun tinyBrightLogoOnMonochromeCoverDoesNotInventDominantHue() {
        assertNull(
            CoverPalette.seed(
                IntArray(1000) { if (it < 20) 0xffff1111.toInt() else 0xff333333.toInt() }
            )
        )
    }

    @Test
    fun largeBrightGreenRegionWinsOverDarkBlueClothingAndRedDetail() {
        val pixels =
            IntArray(1000) {
                when {
                    it < 500 -> 0xff203344.toInt()
                    it < 820 -> 0xff248e68.toInt()
                    it < 840 -> 0xffef2222.toInt()
                    else -> 0xffaaaaaa.toInt()
                }
            }
        val color = CoverPalette.seed(pixels)!!
        assertTrue((color shr 8 and 255) > (color shr 16 and 255) * 2)
    }
}
