package dev.alllexey.itmowidgets.util.qr

import android.graphics.Color
import android.view.ContextThemeWrapper
import com.google.android.material.color.MaterialColors
import dev.alllexey.itmowidgets.AppContainer
import dev.alllexey.itmowidgets.R

class QrColorResolver(private val appContainer: AppContainer) {

    fun getQrColors(): Pair<Int, Int> {
        val dynamicColorsState = appContainer.storage.settings.getDynamicQrColorsState()
        return getQrColors(dynamicColorsState)
    }

    // [background, foreground]
    fun getQrColors(dynamic: Boolean): Pair<Int, Int> {
        var darkModule: Int
        var lightBg: Int

        if (dynamic) {
            val themedContext = ContextThemeWrapper(appContainer.context, R.style.AppTheme)
            lightBg = MaterialColors.getColor(
                themedContext,
                com.google.android.material.R.attr.colorSurface,
                Color.WHITE
            )
            darkModule = MaterialColors.getColor(
                themedContext,
                com.google.android.material.R.attr.colorOnSurfaceVariant,
                Color.BLACK
            )

            // swap
            if (lightBg.isDark()) {
                lightBg = darkModule.also { darkModule = lightBg }
            }

            val darkModuleVariant = MaterialColors.getColor(
                themedContext,
                com.google.android.material.R.attr.colorOnSurface,
                Color.BLACK
            )

            darkModule = maxOf(
                darkModule,
                darkModuleVariant,
                Comparator.comparingDouble { value -> value.darkness() })
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