package dev.alllexey.itmowidgets.designsystem.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.materialkolor.blend.Blend

/**
 * `ColorUtils.blendARGB`: a per-channel sRGB mix truncated to whole channels. Compose's `lerp` mixes in Oklab and
 * gives other values, so the View screens' containers would not match.
 */
internal fun blendArgb(from: Color, to: Color, ratio: Float): Color {
    val start = from.toArgb()
    val end = to.toArgb()
    fun channel(shift: Int): Int =
        ((start ushr shift and 0xFF) * (1f - ratio) + (end ushr shift and 0xFF) * ratio).toInt()
    return Color((channel(24) shl 24) or (channel(16) shl 16) or (channel(8) shl 8) or channel(0))
}

/** `MaterialColors.harmonize`: turns [color]'s hue up to 15 degrees towards [towards], keeping chroma and tone. */
internal fun harmonize(color: Color, towards: Color): Color = Color(Blend.harmonize(color.toArgb(), towards.toArgb()))
