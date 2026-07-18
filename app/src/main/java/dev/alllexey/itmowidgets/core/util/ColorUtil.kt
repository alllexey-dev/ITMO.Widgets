package dev.alllexey.itmowidgets.core.util

import android.content.Context
import android.util.TypedValue
import androidx.annotation.AttrRes
import androidx.annotation.ColorInt
import androidx.core.graphics.ColorUtils
import com.google.android.material.color.MaterialColors

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

@ColorInt
fun Context.resolveColor(@AttrRes attrColor: Int): Int {
    val typedValue = TypedValue()
    theme.resolveAttribute(attrColor, typedValue, true)
    return typedValue.data
}

val Context.color: ThemeColors
    get() = ThemeColors(this)

class ThemeColors(private val context: Context) {

    // Main
    val primary get() = context.resolveColor(android.R.attr.colorPrimary)
    val secondary get() = context.resolveColor(com.google.android.material.R.attr.colorSecondary)
    val tertiary get() = context.resolveColor(com.google.android.material.R.attr.colorTertiary)
    val error get() = context.resolveColor(android.R.attr.colorError)

    // On main
    val onPrimary get() = context.resolveColor(com.google.android.material.R.attr.colorOnPrimary)
    val onSecondary get() = context.resolveColor(com.google.android.material.R.attr.colorOnSecondary)
    val onTertiary get() = context.resolveColor(com.google.android.material.R.attr.colorOnTertiary)
    val onError get() = context.resolveColor(com.google.android.material.R.attr.colorOnError)

    // Containers
    val primaryContainer get() = context.resolveColor(com.google.android.material.R.attr.colorPrimaryContainer)
    val secondaryContainer get() = context.resolveColor(com.google.android.material.R.attr.colorSecondaryContainer)
    val tertiaryContainer get() = context.resolveColor(com.google.android.material.R.attr.colorTertiaryContainer)
    val errorContainer get() = context.resolveColor(com.google.android.material.R.attr.colorErrorContainer)

    // On Containers
    val onPrimaryContainer get() = context.resolveColor(com.google.android.material.R.attr.colorOnPrimaryContainer)
    val onSecondaryContainer get() = context.resolveColor(com.google.android.material.R.attr.colorOnSecondaryContainer)
    val onTertiaryContainer get() = context.resolveColor(com.google.android.material.R.attr.colorOnTertiaryContainer)
    val onErrorContainer get() = context.resolveColor(com.google.android.material.R.attr.colorOnErrorContainer)

    // Surface
    val surface get() = context.resolveColor(com.google.android.material.R.attr.colorSurface)
    val onSurface get() = context.resolveColor(com.google.android.material.R.attr.colorOnSurface)
    val surfaceVariant get() = context.resolveColor(com.google.android.material.R.attr.colorSurfaceVariant)
    val onSurfaceVariant get() = context.resolveColor(com.google.android.material.R.attr.colorOnSurfaceVariant)

    // Background
    val background get() = context.resolveColor(android.R.attr.colorBackground)
    val onBackground get() = context.resolveColor(com.google.android.material.R.attr.colorOnBackground)

    // Other
    val outline get() = context.resolveColor(com.google.android.material.R.attr.colorOutline)
    val outlineVariant get() = context.resolveColor(com.google.android.material.R.attr.colorOutlineVariant)

    fun resolve(@AttrRes attr: Int): Int {
        return MaterialColors.getColor(context, attr, 0)
    }
}
