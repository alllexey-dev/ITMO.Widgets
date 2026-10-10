package dev.alllexey.itmowidgets.designsystem.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import dev.alllexey.itmowidgets.core.settings.ThemeSpec
import dev.alllexey.itmowidgets.core.settings.WidgetColorRoles
import dev.alllexey.itmowidgets.core.settings.WidgetPalette

/**
 * The widgets' roles of the scheme [resolveColorScheme] gives for this appearance, light and dark; [wallpaper] as there
 * (Android's `widgetPalette(context)` reads it). [ThemeSpec.pureBlack] stays out: a widget is a card on the home screen,
 * so it keeps the scheme's surface tone instead of a black tile.
 */
fun ThemeSpec.widgetPalette(wallpaper: WallpaperPalette? = null): WidgetPalette {
    val spec = copy(pureBlack = false)
    return widgetPaletteOf(
        light = resolveColorScheme(spec, dark = false, wallpaper = wallpaper),
        dark = resolveColorScheme(spec, dark = true, wallpaper = wallpaper),
    )
}

internal fun widgetPaletteOf(light: ColorScheme, dark: ColorScheme) =
    WidgetPalette(light = light.widgetRoles(), dark = dark.widgetRoles())

private fun ColorScheme.widgetRoles() = WidgetColorRoles(
    surface = surface.rgb(),
    surfaceContainer = surfaceContainer.rgb(),
    onSurface = onSurface.rgb(),
    onSurfaceVariant = onSurfaceVariant.rgb(),
    outlineVariant = outlineVariant.rgb(),
    primary = primary.rgb(),
)

/** Opaque `0xRRGGBB`: every role of a generated scheme is opaque. */
private fun Color.rgb(): Int = toArgb() and RGB

private const val RGB = 0xFFFFFF
