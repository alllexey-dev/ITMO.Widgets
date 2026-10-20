package dev.alllexey.itmowidgets.feature.settings.presentation.appearance

import dev.alllexey.itmowidgets.core.settings.ThemeSpec
import dev.alllexey.itmowidgets.core.settings.ThemeStyle
import dev.alllexey.itmowidgets.core.settings.WidgetColorRoles
import dev.alllexey.itmowidgets.core.settings.widgetPalette

/**
 * The colours of «Оформление» where Compose does not draw it (iOS): the same scheme the widgets take
 * (`ThemeSpec.widgetPalette`), each `0xRRGGBB`. Android draws its swatches from the app's own scheme, the wallpaper
 * included.
 */
object AppearanceColors {

    /** The scheme of [theme] in the light or the dark appearance, for the sample. */
    fun roles(theme: ThemeSpec, dark: Boolean): WidgetColorRoles = theme.widgetPalette().let { if (dark) it.dark else it.light }

    /**
     * The swatch of [theme]'s accent: the primary of its scheme. Under `Монохром` every accent is grey, so the swatch
     * keeps the default palette's hue to stay told apart, as Android's swatches do.
     */
    fun swatch(theme: ThemeSpec, dark: Boolean): Int {
        val shown = if (theme.style == ThemeStyle.MONOCHROME) theme.copy(style = ThemeStyle.TONAL_SPOT) else theme
        return roles(shown, dark).primary
    }

    /** What [style] makes of [theme]'s colour: its primary, the primary's container and the tertiary. */
    fun palette(theme: ThemeSpec, style: ThemeStyle, dark: Boolean): List<Int> =
        roles(theme.copy(style = style), dark).let { listOf(it.primary, it.primaryContainer, it.tertiary) }
}
