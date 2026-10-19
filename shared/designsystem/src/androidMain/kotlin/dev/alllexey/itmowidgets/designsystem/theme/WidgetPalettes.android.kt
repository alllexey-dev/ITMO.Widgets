package dev.alllexey.itmowidgets.designsystem.theme

import android.content.Context
import dev.alllexey.itmowidgets.core.settings.ThemeSpec
import dev.alllexey.itmowidgets.core.settings.WidgetPalette
import dev.alllexey.itmowidgets.core.settings.widgetPalette

/**
 * The widgets' palette as [ItmoTheme] draws this appearance: the wallpaper's own scheme on Android 12+ where
 * [resolveColorScheme] keeps it, otherwise core's palette from the wallpaper's or the picked seed.
 */
fun ThemeSpec.widgetPalette(context: Context): WidgetPalette {
    val wallpaper = wallpaperPalette(context)
    return wallpaper?.takeIf { followsSystemScheme }?.let { widgetPaletteOf(it.light, it.dark) }
        ?: widgetPalette(wallpaperSeed = wallpaper?.seedArgb)
}
