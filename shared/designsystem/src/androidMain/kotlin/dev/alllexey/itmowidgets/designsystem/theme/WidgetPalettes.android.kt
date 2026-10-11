package dev.alllexey.itmowidgets.designsystem.theme

import android.content.Context
import dev.alllexey.itmowidgets.core.settings.ThemeSpec
import dev.alllexey.itmowidgets.core.settings.WidgetPalette

/** [widgetPalette] with the wallpaper's colours on Android 12+, as [ItmoTheme] draws them. */
fun ThemeSpec.widgetPalette(context: Context): WidgetPalette = widgetPalette(wallpaperPalette(context))
