package dev.alllexey.itmowidgets.util

import android.content.Context
import android.util.TypedValue
import android.view.ContextThemeWrapper
import androidx.annotation.AttrRes
import androidx.annotation.ColorInt
import androidx.annotation.ColorRes
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import com.google.android.material.color.MaterialColors
import dev.alllexey.itmowidgets.R

class ColorUtil(val context: Context) {

    val themedContext by lazy { ContextThemeWrapper(context, R.style.AppTheme) }

    @ColorInt
    fun getColor(@ColorRes colorAttributeResId: Int): Int {
        return ContextCompat.getColor(context, colorAttributeResId)
    }

    @ColorInt
    fun getDynamicColor(@AttrRes colorAttributeResId: Int, @ColorInt default: Int): Int {
        return MaterialColors.getColor(
            themedContext,
            colorAttributeResId,
            default
        )
    }

    @ColorInt
    fun getPrimaryColor(@ColorInt default: Int): Int {
        return getDynamicColor(android.R.attr.colorPrimary, default)
    }

    @ColorInt
    fun getSecondaryColor(@ColorInt default: Int): Int {
        return getDynamicColor(com.google.android.material.R.attr.colorSecondary, default)
    }

    @ColorInt
    fun getTertiaryColor(@ColorInt default: Int): Int {
        return getDynamicColor(com.google.android.material.R.attr.colorTertiary, default)
    }
}

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
fun Context.getColorFromAttr(@AttrRes attrColor: Int): Int {
    val typedValue = TypedValue()
    theme.resolveAttribute(attrColor, typedValue, true)
    return typedValue.data
}