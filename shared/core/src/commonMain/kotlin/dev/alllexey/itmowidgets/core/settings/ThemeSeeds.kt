package dev.alllexey.itmowidgets.core.settings

import com.materialkolor.dynamiccolor.ColorSpec
import com.materialkolor.dynamiccolor.DynamicColor
import com.materialkolor.dynamiccolor.MaterialDynamicColors
import com.materialkolor.hct.Hct
import com.materialkolor.scheme.DynamicScheme
import com.materialkolor.scheme.SchemeContent
import com.materialkolor.scheme.SchemeExpressive
import com.materialkolor.scheme.SchemeFidelity
import com.materialkolor.scheme.SchemeMonochrome
import com.materialkolor.scheme.SchemeNeutral
import com.materialkolor.scheme.SchemeTonalSpot
import com.materialkolor.scheme.SchemeVibrant

/** The seeds the appearance builds its scheme from (DS-ACC1, DS-ACC2). */
object ThemeSeeds {

    /** The launcher, shortcut and `lesson_type_lecture` blue: the brand scheme's seed. */
    const val BRAND: Int = 0xFF4984E2.toInt()

    /**
     * The preset seeds: hues around the brand blue. TonalSpot keeps only the hue, so a grey seed would come out tinted
     * and none is offered; Монохром is the grey look.
     */
    val PRESETS: Map<AccentColor, Int> = mapOf(
        AccentColor.TEAL to 0xFF009688.toInt(),
        AccentColor.GREEN to 0xFF43A047.toInt(),
        AccentColor.AMBER to 0xFFFFA000.toInt(),
        AccentColor.RED to 0xFFE53935.toInt(),
        AccentColor.PINK to 0xFFD81B60.toInt(),
        AccentColor.PURPLE to 0xFF8E24AA.toInt(),
    )
}

/** The seed this appearance builds from: the preset's, the user's own, the wallpaper's ([wallpaperSeed]) or the brand. */
fun ThemeSpec.seedArgb(wallpaperSeed: Int? = null): Int = when (accent) {
    AccentColor.WALLPAPER -> wallpaperSeed ?: ThemeSeeds.BRAND
    AccentColor.BRAND -> ThemeSeeds.BRAND
    AccentColor.CUSTOM -> customArgb
    else -> ThemeSeeds.PRESETS.getValue(accent)
}

/**
 * The widgets' roles of the scheme built from [seedArgb] with the picked style and contrast (2021 spec), light and
 * dark, without Compose: the design system's `resolveColorScheme` builds the same scheme through MaterialKolor, and
 * its `WidgetPalettesTest` holds the two equal. [ThemeSpec.pureBlack] stays out: a widget is a card on the home screen.
 * Android's wallpaper scheme itself is the design system's `widgetPalette(context)`.
 */
fun ThemeSpec.widgetPalette(wallpaperSeed: Int? = null): WidgetPalette {
    val seed = Hct.fromInt(seedArgb(wallpaperSeed))
    return WidgetPalette(light = widgetRoles(scheme(seed, dark = false)), dark = widgetRoles(scheme(seed, dark = true)))
}

private fun ThemeSpec.scheme(seed: Hct, dark: Boolean): DynamicScheme {
    val level = when (contrast) {
        ThemeContrast.STANDARD -> 0.0
        ThemeContrast.MEDIUM -> 0.5
        ThemeContrast.HIGH -> 1.0
    }
    val spec = ColorSpec.SpecVersion.SPEC_2021
    return when (style) {
        ThemeStyle.TONAL_SPOT -> SchemeTonalSpot(seed, dark, level, spec)
        ThemeStyle.VIBRANT -> SchemeVibrant(seed, dark, level, spec)
        ThemeStyle.EXPRESSIVE -> SchemeExpressive(seed, dark, level, spec)
        ThemeStyle.FIDELITY -> SchemeFidelity(seed, dark, level, spec)
        ThemeStyle.CONTENT -> SchemeContent(seed, dark, level, spec)
        ThemeStyle.NEUTRAL -> SchemeNeutral(seed, dark, level, spec)
        ThemeStyle.MONOCHROME -> SchemeMonochrome(seed, dark, level, spec)
    }
}

private fun widgetRoles(scheme: DynamicScheme): WidgetColorRoles {
    val colors = MaterialDynamicColors()
    fun rgb(color: DynamicColor) = color.getArgb(scheme) and RGB
    return WidgetColorRoles(
        surface = rgb(colors.surface()),
        surfaceContainer = rgb(colors.surfaceContainer()),
        onSurface = rgb(colors.onSurface()),
        onSurfaceVariant = rgb(colors.onSurfaceVariant()),
        outlineVariant = rgb(colors.outlineVariant()),
        primary = rgb(colors.primary()),
        primaryContainer = rgb(colors.primaryContainer()),
        onPrimaryContainer = rgb(colors.onPrimaryContainer()),
        surfaceContainerHigh = rgb(colors.surfaceContainerHigh()),
        tertiary = rgb(colors.tertiary()),
    )
}

private const val RGB = 0xFFFFFF
