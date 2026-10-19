package dev.alllexey.itmowidgets.designsystem.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import dev.alllexey.itmowidgets.core.settings.WidgetColorRoles
import dev.alllexey.itmowidgets.core.settings.WidgetPalette

/**
 * The widgets' roles of a light and a dark scheme: Android's wallpaper scheme. Every seeded appearance takes core's
 * `ThemeSpec.widgetPalette`, which builds the same scheme without Compose (`WidgetPalettesTest`).
 */
internal fun widgetPaletteOf(light: ColorScheme, dark: ColorScheme) =
    WidgetPalette(light = light.widgetRoles(), dark = dark.widgetRoles())

private fun ColorScheme.widgetRoles() = WidgetColorRoles(
    surface = surface.rgb(),
    surfaceContainer = surfaceContainer.rgb(),
    onSurface = onSurface.rgb(),
    onSurfaceVariant = onSurfaceVariant.rgb(),
    outlineVariant = outlineVariant.rgb(),
    primary = primary.rgb(),
    primaryContainer = primaryContainer.rgb(),
    onPrimaryContainer = onPrimaryContainer.rgb(),
    surfaceContainerHigh = surfaceContainerHigh.rgb(),
    tertiary = tertiary.rgb(),
)

/** Opaque `0xRRGGBB`: every role of a generated scheme is opaque. */
private fun Color.rgb(): Int = toArgb() and RGB

private const val RGB = 0xFFFFFF
