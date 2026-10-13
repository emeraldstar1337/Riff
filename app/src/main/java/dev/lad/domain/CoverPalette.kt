package dev.lad.domain

import kotlin.math.*

object CoverPalette {
    private class Histogram {
        val weights = DoubleArray(36)
        val counts = IntArray(36)
        val reds = DoubleArray(36)
        val greens = DoubleArray(36)
        val blues = DoubleArray(36)

        fun add(bin: Int, r: Double, g: Double, b: Double, weight: Double) {
            weights[bin] += weight
            counts[bin]++
            reds[bin] += r * weight
            greens[bin] += g * weight
            blues[bin] += b * weight
        }

        fun region(center: Int) = (-2..2).map { (center + it + 36) % 36 }

        fun winner() = weights.indices.maxBy { i -> region(i).sumOf { weights[it] } }

        fun population(center: Int) = region(center).sumOf { counts[it] }

        fun color(center: Int): Int {
            val ids = region(center)
            val w = ids.sumOf { weights[it] }
            fun component(values: DoubleArray) =
                (ids.sumOf { values[it] } / w * 255).roundToInt().coerceIn(0, 255)
            return (0xff shl 24) or
                (component(reds) shl 16) or
                (component(greens) shl 8) or
                component(blues)
        }
    }

    fun seed(pixels: IntArray): Int? {
        if (pixels.isEmpty()) return null
        val all = Histogram()
        val bright = Histogram()
        var colored = 0
        for (pixel in pixels) {
            if ((pixel ushr 24) < 128) continue
            val r = (pixel shr 16 and 255) / 255.0
            val g = (pixel shr 8 and 255) / 255.0
            val b = (pixel and 255) / 255.0
            val hi = max(r, max(g, b))
            val lo = min(r, min(g, b))
            val d = hi - lo
            if (hi < .08 || d < .04 || d / hi < .12) continue
            val saturation = d / hi
            val hue =
                ((when (hi) {
                    r -> (g - b) / d
                    g -> (b - r) / d + 2
                    else -> (r - g) / d + 4
                }) * 60 + 360) % 360
            val bin = (hue / 10).toInt().coerceIn(0, 35)
            all.add(bin, r, g, b, (.15 + .85 * saturation * saturation) * hi)
            colored++
            if (hi >= .25 && saturation >= .25)
                bright.add(bin, r, g, b, saturation * saturation * hi)
        }
        if (colored < max(1, (pixels.size * .08).toInt())) return null
        val candidate = bright.winner()

        return if (
            bright.population(candidate) >= max(1, max(pixels.size * .08, colored * .12).toInt())
        )
            bright.color(candidate)
        else all.color(all.winner())
    }
}
