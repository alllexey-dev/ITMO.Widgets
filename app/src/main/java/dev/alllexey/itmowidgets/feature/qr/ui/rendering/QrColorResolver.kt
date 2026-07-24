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
    fun getQrColors(dynamic: Boolean): Pair<Int, Int> {
        var darkModule: Int
        var lightBg: Int

        if (dynamic) {
            val color = context.color

            lightBg = color.surface
            darkModule = color.onSurfaceVariant

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
        } else {
            darkModule = Color.BLACK
            lightBg = Color.WHITE
        }

        return lightBg to darkModule
    }

    fun Int.isDark(): Boolean {
        return darkness() >= 0.5
    }

    fun Int.darkness(): Double {
        return 1 - (0.299 * Color.red(this) + 0.587 * Color.green(this) + 0.114 * Color.blue(this)) / 255
    }
}
