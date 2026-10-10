package dev.alllexey.itmowidgets.core.platform

/**
 * Which features the platform offers, so shared screens hide an entry point instead of showing a placeholder.
 * Android offers all of them, so it shows exactly what 2.2 showed; iOS turns each one on with the card that ships it.
 */
data class PlatformCapabilities(
    /** The QR quick settings tile and its settings row. */
    val quickSettingsTile: Boolean,
    /** The background work row: battery optimisation and the Xiaomi autostart screens. */
    val backgroundWorkSettings: Boolean,
    /** The update channel row and the in-app update check. */
    val updateChannel: Boolean,
    /** Calendar export: the device calendar sync and the `.ics` file. */
    val calendarExport: Boolean,
    /** The recordbook tab and its settings page. */
    val recordbook: Boolean,
    /** Mark tracking and the BARS sign-in. */
    val marks: Boolean,
    /** Teacher reviews and subject links: user-generated content with its reporting. */
    val reviews: Boolean,
    /** The animated QR widget and its setting. */
    val qrWidgetAnimation: Boolean,
    /** A custom image for the QR spoiler. */
    val qrCustomSpoiler: Boolean,
    /** The wallpaper's colours as a choice of the accent colour setting: Android 12+ only. */
    val wallpaperColors: Boolean,
)
