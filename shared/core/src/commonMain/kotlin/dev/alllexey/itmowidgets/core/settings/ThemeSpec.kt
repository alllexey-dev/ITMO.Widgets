package dev.alllexey.itmowidgets.core.settings

/**
 * Everything the user chose about the app's colours (DS-ACC1, DS-ACC2), as `app_preferences` stores it. The design
 * system's `resolveColorScheme` turns it into the light or dark scheme; the defaults are the scheme the app had before
 * the choices existed.
 */
data class ThemeSpec(
    val accent: AccentColor = AccentColor.WALLPAPER,
    /** The seed of [AccentColor.CUSTOM] as opaque ARGB; kept while another accent is picked. */
    val customArgb: Int = DEFAULT_CUSTOM_ARGB,
    val style: ThemeStyle = ThemeStyle.TONAL_SPOT,
    val contrast: ThemeContrast = ThemeContrast.STANDARD,
    /** Black background and surfaces in the dark theme; the light theme ignores it. */
    val pureBlack: Boolean = false,
) {
    companion object {
        /** The brand blue `#4984E2`: what «Свой цвет» starts from. */
        const val DEFAULT_CUSTOM_ARGB: Int = 0xFF4984E2.toInt()
    }
}

/** How the palette is built from the seed: Material's scheme variants. Names are stored in `app_theme_style`. */
enum class ThemeStyle {
    TONAL_SPOT,
    VIBRANT,
    EXPRESSIVE,
    FIDELITY,
    CONTENT,
    NEUTRAL,
    MONOCHROME,
}

/** Material's contrast levels 0, 0.5 and 1. Names are stored in `app_theme_contrast`. */
enum class ThemeContrast {
    STANDARD,
    MEDIUM,
    HIGH,
}

/** `#RRGGBB` for the custom colour's text field and its iOS bridge; alpha is always opaque. */
object HexColor {

    private val PATTERN = Regex("#?[0-9A-Fa-f]{6}")

    /** The opaque colour of `RRGGBB` with or without `#`, or null for anything else. */
    fun parse(text: String): Int? {
        val trimmed = text.trim()
        if (!PATTERN.matches(trimmed)) return null
        return (0xFF000000.toInt()) or trimmed.removePrefix("#").toInt(16)
    }

    fun format(argb: Int): String = "#" + (argb and 0xFFFFFF).toString(16).uppercase().padStart(6, '0')
}
