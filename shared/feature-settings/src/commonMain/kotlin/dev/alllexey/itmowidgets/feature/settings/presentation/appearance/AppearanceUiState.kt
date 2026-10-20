package dev.alllexey.itmowidgets.feature.settings.presentation.appearance

import dev.alllexey.itmowidgets.core.platform.PlatformCapabilities
import dev.alllexey.itmowidgets.core.settings.AccentColor
import dev.alllexey.itmowidgets.core.settings.ThemeContrast
import dev.alllexey.itmowidgets.core.settings.ThemeSpec
import dev.alllexey.itmowidgets.core.settings.ThemeStyle
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.shared.feature.settings.SettingsStrings

/**
 * «Оформление» (docs/settings.md, Appearance): the stored [theme] with the choices around it, in the order the page
 * shows them: the colour, the palette, the contrast and the black background. [loaded] turns true with the first
 * stored value, so the preview never flashes the default colours.
 */
data class AppearanceUiState(
    val loaded: Boolean = false,
    /** What the app draws now; the preview shows it in the light and the dark theme. */
    val theme: ThemeSpec = ThemeSpec(),
    /** The colours to pick from: `Как обои` only where the wallpaper has colours. */
    val accents: List<AccentOption> = emptyList(),
    /** The picked colour as the page shows it: the stored `Как обои` reads as `Фирменный` without wallpaper colours. */
    val accent: AccentColor = AccentColor.BRAND,
    /** False under `Монохром`, where every colour comes out grey: the colours stay visible but cannot be picked. */
    val accentApplies: Boolean = true,
    /** The line under the colours: where `Как обои` comes from, or why the colours are off under `Монохром`. */
    val accentNote: UiText? = null,
    val styles: List<StyleOption> = emptyList(),
    val contrasts: List<ContrastOption> = emptyList(),
    /** `Чёрный фон`; null where the app keeps the system's dark background (iOS). */
    val blackBackground: Boolean? = null,
    /** What else takes these colours: the widgets, and on iOS only they. */
    val footer: UiText? = null,
) {
    /** `Свой цвет` is picked: its colour under the presets, for the picker. */
    val customArgb: Int?
        get() = theme.customArgb.takeIf { accent == AccentColor.CUSTOM }

    /** The picked palette with what it does in a few words. */
    val style: StyleOption?
        get() = styles.firstOrNull { it.style == theme.style }
}

/** The page for the stored [theme] on a platform with [capabilities]; the previews draw it too. */
fun appearanceState(theme: ThemeSpec, capabilities: PlatformCapabilities): AppearanceUiState {
    val accents = AccentColor.entries.filter { it != AccentColor.WALLPAPER || capabilities.wallpaperColors }
    val accent = theme.accent.takeIf { it in accents } ?: AccentColor.BRAND
    val monochrome = theme.style == ThemeStyle.MONOCHROME
    return AppearanceUiState(
        loaded = true,
        theme = theme,
        accents = accents.map { AccentOption(it, it.label) },
        accent = accent,
        accentApplies = !monochrome,
        accentNote = when {
            monochrome -> UiText.Res(SettingsStrings.settings_appearance_monochrome_note)
            accent == AccentColor.WALLPAPER -> UiText.Res(SettingsStrings.settings_appearance_wallpaper_note)
            else -> null
        },
        styles = ThemeStyle.entries.map { StyleOption(it, it.label, it.description) },
        contrasts = ThemeContrast.entries.map { ContrastOption(it, it.label) },
        blackBackground = theme.pureBlack.takeIf { capabilities.appColors },
        footer = UiText.Res(
            if (capabilities.appColors) {
                SettingsStrings.settings_appearance_widgets_note
            } else {
                SettingsStrings.settings_appearance_widgets_only_note
            }
        ),
    )
}

data class AccentOption(val accent: AccentColor, val label: UiText)

data class StyleOption(val style: ThemeStyle, val label: UiText, val description: UiText)

data class ContrastOption(val contrast: ThemeContrast, val label: UiText)

/** The name of [this] colour, which the settings root shows as the value of `Оформление` too. */
val AccentColor.label: UiText
    get() = UiText.Res(
        when (this) {
            AccentColor.WALLPAPER -> SettingsStrings.settings_accent_color_wallpaper
            AccentColor.BRAND -> SettingsStrings.settings_accent_color_brand
            AccentColor.TEAL -> SettingsStrings.settings_accent_color_teal
            AccentColor.GREEN -> SettingsStrings.settings_accent_color_green
            AccentColor.AMBER -> SettingsStrings.settings_accent_color_amber
            AccentColor.RED -> SettingsStrings.settings_accent_color_red
            AccentColor.PINK -> SettingsStrings.settings_accent_color_pink
            AccentColor.PURPLE -> SettingsStrings.settings_accent_color_purple
            AccentColor.CUSTOM -> SettingsStrings.settings_accent_color_custom
        }
    )

internal val ThemeStyle.label: UiText
    get() = UiText.Res(
        when (this) {
            ThemeStyle.TONAL_SPOT -> SettingsStrings.settings_theme_style_tonal_spot
            ThemeStyle.VIBRANT -> SettingsStrings.settings_theme_style_vibrant
            ThemeStyle.EXPRESSIVE -> SettingsStrings.settings_theme_style_expressive
            ThemeStyle.FIDELITY -> SettingsStrings.settings_theme_style_fidelity
            ThemeStyle.CONTENT -> SettingsStrings.settings_theme_style_content
            ThemeStyle.NEUTRAL -> SettingsStrings.settings_theme_style_neutral
            ThemeStyle.MONOCHROME -> SettingsStrings.settings_theme_style_monochrome
        }
    )

internal val ThemeStyle.description: UiText
    get() = UiText.Res(
        when (this) {
            ThemeStyle.TONAL_SPOT -> SettingsStrings.settings_theme_style_tonal_spot_description
            ThemeStyle.VIBRANT -> SettingsStrings.settings_theme_style_vibrant_description
            ThemeStyle.EXPRESSIVE -> SettingsStrings.settings_theme_style_expressive_description
            ThemeStyle.FIDELITY -> SettingsStrings.settings_theme_style_fidelity_description
            ThemeStyle.CONTENT -> SettingsStrings.settings_theme_style_content_description
            ThemeStyle.NEUTRAL -> SettingsStrings.settings_theme_style_neutral_description
            ThemeStyle.MONOCHROME -> SettingsStrings.settings_theme_style_monochrome_description
        }
    )

internal val ThemeContrast.label: UiText
    get() = UiText.Res(
        when (this) {
            ThemeContrast.STANDARD -> SettingsStrings.settings_theme_contrast_standard
            ThemeContrast.MEDIUM -> SettingsStrings.settings_theme_contrast_medium
            ThemeContrast.HIGH -> SettingsStrings.settings_theme_contrast_high
        }
    )
