package dev.alllexey.itmowidgets.core.settings

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The app's colours for the home-screen widgets while «Виджеты в цвет темы» is on (`widgets_follow_app_theme`): the
 * roles of the app's light and dark scheme the widgets draw with, so the widget picks the mode the system is in. The
 * iOS App Group snapshots carry it as `palette` (docs/ios.md, Data sharing).
 */
@Serializable
data class WidgetPalette(
    @SerialName("light") val light: WidgetColorRoles,
    @SerialName("dark") val dark: WidgetColorRoles,
) {
    fun roles(dark: Boolean): WidgetColorRoles = if (dark) this.dark else light
}

/** The few scheme roles a widget uses, each an opaque `0xRRGGBB`. */
@Serializable
data class WidgetColorRoles(
    @SerialName("surface") val surface: Int,
    @SerialName("surfaceContainer") val surfaceContainer: Int,
    @SerialName("onSurface") val onSurface: Int,
    @SerialName("onSurfaceVariant") val onSurfaceVariant: Int,
    @SerialName("outlineVariant") val outlineVariant: Int,
    @SerialName("primary") val primary: Int,
)
