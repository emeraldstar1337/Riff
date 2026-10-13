package dev.lad.data

data class GlowPreset(
    val id: String,
    val name: String,
    val strength: Float,
    val radius: Float,
    val saturation: Float,
) {
    fun apply(s: Settings) =
        s.copy(
            glowPreset = id,
            glowStrength = strength,
            glowRadius = radius,
            glowSaturation = saturation,
            ambientBackground = true,
        )
}

data class LyricsPreset(
    val id: String,
    val name: String,
    val size: Float,
    val spacing: Float,
    val alignment: String,
    val dim: Float,
) {
    fun apply(s: Settings) =
        s.copy(
            lyricsPreset = id,
            lyricsSize = size,
            lyricsSpacing = spacing,
            lyricsAlignment = alignment,
            lyricsDim = dim,
        )
}

object AppearancePresets {
    val glow =
        listOf(
            GlowPreset("quiet", "Quiet", 0.35f, 0.75f, 0.55f),
            GlowPreset("soft", "Soft", 1f, 1f, 0.8f),
            GlowPreset("halo", "Halo", 1.45f, 0.8f, 1f),
            GlowPreset("wide", "Atmosphere", 1.1f, 1.4f, 0.65f),
        )
    val lyrics =
        listOf(
            LyricsPreset("balanced", "Balanced", 24f, 1.3f, "start", 0.46f),
            LyricsPreset("stage", "Stage", 30f, 1.45f, "center", 0.28f),
            LyricsPreset("reading", "Reading", 20f, 1.5f, "start", 0.65f),
            LyricsPreset("compact", "Compact", 18f, 1.2f, "start", 0.5f),
        )
}
