package dev.alllexey.itmowidgets.feature.recordbook.presentation

import kotlin.math.roundToInt

/** Clamp only the drawing, not the reported score. Missing points are not zero points. */
data class RecordbookProgress(val score: Double?, val maximum: Double? = 100.0) {
    val value: Double? = score?.takeIf { it.isFinite() && it >= 0 }
    val limit: Double? = maximum?.takeIf { it.isFinite() && it > 0 }
    val isAvailable: Boolean get() = value != null && limit != null
    val progress: Int
        get() = if (isAvailable) ((value!! / limit!!).coerceIn(0.0, 1.0) * SCALE).roundToInt() else 0

    companion object {
        const val SCALE = 1000
    }
}
