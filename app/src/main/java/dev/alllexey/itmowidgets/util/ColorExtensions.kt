package dev.alllexey.itmowidgets.util

import androidx.annotation.ColorInt
import androidx.core.graphics.ColorUtils

@ColorInt
fun Int.withSaturation(factor: Float): Int {
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(this, hsl)
    hsl[1] = (hsl[1] * factor).coerceIn(0f, 1f)
    return ColorUtils.HSLToColor(hsl)
}

@ColorInt
fun Int.withLightness(factor: Float): Int {
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(this, hsl)
    hsl[2] = (hsl[2] * factor).coerceIn(0f, 1f)
    return ColorUtils.HSLToColor(hsl)
}