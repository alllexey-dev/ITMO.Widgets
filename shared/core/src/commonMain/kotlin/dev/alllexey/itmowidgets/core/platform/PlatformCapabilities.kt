package dev.alllexey.itmowidgets.core.platform

/**
 * Which features the platform offers, so shared screens hide an entry point instead of showing a placeholder.
 * Android offers all of them; `IosPlatformCapabilities` lists what iOS offers.
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
    /** The launcher places a widget the app asks for (`Добавить` in the widgets gallery); iOS says how to add one. */
    val pinWidgets: Boolean,
    /** Lock Screen widgets and the pass Control, listed in the widgets gallery. */
    val lockScreenWidgets: Boolean,
    /**
     * The app's own switch of each push type (`PushCategoryPreferences`): the device drops a push of a type that is off.
     * iOS can hide a delivered push only with `com.apple.developer.usernotifications.filtering`.
     */
    val pushSwitches: Boolean,
)
