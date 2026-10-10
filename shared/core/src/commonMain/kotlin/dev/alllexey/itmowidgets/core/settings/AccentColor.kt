package dev.alllexey.itmowidgets.core.settings

/**
 * The accent colour setting: the seed of the app's colour scheme (DS-ACC1). The names are stored in `app_accent_color` and
 * are stable identifiers; the design system maps each one to its scheme.
 */
enum class AccentColor {
    /** The wallpaper's colours on Android 12+, the brand scheme elsewhere: the default, today's behaviour. */
    WALLPAPER,
    BRAND,
    TEAL,
    GREEN,
    AMBER,
    RED,
    PINK,
    PURPLE,
}
