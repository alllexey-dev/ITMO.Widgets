package dev.alllexey.itmowidgets.feature.qr.ui.rendering

import android.graphics.Color
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.alllexey.itmowidgets.core.util.color
import dev.alllexey.itmowidgets.feature.qr.domain.QrAppearancePreferences
import javax.inject.Inject

class QrColorResolver @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val preferences: QrAppearancePreferences
) {

    suspend fun getQrColors(): Pair<Int, Int> {
        val dynamicColorsState = preferences.useDynamicColors()
        return getQrColors(dynamicColorsState)
    }

    // [background, foreground]
    fun getQrColors(dynamic: Boolean): Pair<Int, Int> = getQrColors(context, dynamic)

    fun getQrColors(themeContext: Context, dynamic: Boolean): Pair<Int, Int> {
        if (!dynamic) return Color.WHITE to Color.BLACK

        val color = themeContext.color

        var lightBg = color.surface
        var darkModule = color.onSurfaceVariant
        val darkModuleVariant = color.onSurface

        // swap
        if (lightBg.isDark()) {
            lightBg = darkModule.also { darkModule = lightBg }
        }

        darkModule = maxOf(
            darkModule,
            darkModuleVariant,
            Comparator.comparingDouble { value -> value.darkness() }
        )

        // Theme attributes do not resolve outside an activity, and a translucent code
        // is unreadable for a scanner. Plain black on white always works.
        if (!lightBg.isOpaque() || !darkModule.isOpaque()) {
            return Color.WHITE to Color.BLACK
        }

        return lightBg to darkModule
    }

    private fun Int.isOpaque(): Boolean = Color.alpha(this) == OPAQUE_ALPHA

    fun Int.isDark(): Boolean {
        return darkness() >= 0.5
    }

    fun Int.darkness(): Double {
        return 1 - (0.299 * Color.red(this) + 0.587 * Color.green(this) + 0.114 * Color.blue(this)) / 255
    }

    private companion object {
        const val OPAQUE_ALPHA = 255
    }
}
