package dev.lad.domain

import kotlin.math.pow

object ReplayGain {
    fun factor(gainTag: String?, peakTag: String? = null): Float {
        val db =
            gainTag?.trim()?.substringBefore(' ')?.toDoubleOrNull()?.takeIf { it.isFinite() }
                ?: return 1f
        val gain = 10.0.pow(db.coerceIn(-60.0, 30.0) / 20.0)
        val peak = peakTag?.trim()?.toDoubleOrNull()?.takeIf { it.isFinite() && it > 0 }
        return (if (peak != null) minOf(gain, 1.0 / peak) else gain).toFloat()
    }
}
