package dev.alllexey.itmowidgets.feature.qr.ui.rendering

import android.graphics.Color
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.alllexey.itmowidgets.core.settings.WidgetPalette
import dev.alllexey.itmowidgets.core.settings.WidgetPaletteSource
import dev.alllexey.itmowidgets.core.ui.color
import dev.alllexey.itmowidgets.core.ui.widget.rolesFor
import dev.alllexey.itmowidgets.feature.qr.domain.QrAppearancePreferences
import javax.inject.Inject

/**
 * The QR widget's colours. Precedence: without the QR widget's dynamic colours the code is black on white whatever
 * the theme option says, since that always scans; with them, the app's palette while the widgets follow the theme
 * (`widgets_follow_app_theme`), the system theme otherwise.
 */
class QrColorResolver @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val preferences: QrAppearancePreferences,
    private val palettes: WidgetPaletteSource
) {

    suspend fun getQrColors(): Pair<Int, Int> {
        val dynamic = preferences.useDynamicColors()
        val palette = if (dynamic) palettes.current() else null
        return getQrColors(context, dynamic, palette)
    }

    // [background, foreground]
    fun getQrColors(dynamic: Boolean): Pair<Int, Int> = getQrColors(context, dynamic)

    /** [palette] is the app's theme while the widgets follow it; it takes the place of [themeContext]'s colours. */
    fun getQrColors(themeContext: Context, dynamic: Boolean, palette: WidgetPalette? = null): Pair<Int, Int> {
        if (!dynamic) return Color.WHITE to Color.BLACK

        val roles = palette?.rolesFor(themeContext)
        if (roles != null) {
            return resolve(opaque(roles.surface), opaque(roles.onSurfaceVariant), opaque(roles.onSurface))
        }
        val color = themeContext.color
        return resolve(color.surface, color.onSurfaceVariant, color.onSurface)
    }

    private fun resolve(surface: Int, onSurfaceVariant: Int, onSurface: Int): Pair<Int, Int> {
        var lightBg = surface
        var darkModule = onSurfaceVariant

        // swap
        if (lightBg.isDark()) {
            lightBg = darkModule.also { darkModule = lightBg }
        }

        darkModule = maxOf(
            darkModule,
            onSurface,
            Comparator.comparingDouble { value -> value.darkness() }
        )

        // Theme attributes do not resolve outside an activity, and a translucent code
        // is unreadable for a scanner. Plain black on white always works.
        if (!lightBg.isOpaque() || !darkModule.isOpaque()) {
            return Color.WHITE to Color.BLACK
        }

        return lightBg to darkModule
    }

    private fun opaque(rgb: Int): Int = Color.BLACK or rgb

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
