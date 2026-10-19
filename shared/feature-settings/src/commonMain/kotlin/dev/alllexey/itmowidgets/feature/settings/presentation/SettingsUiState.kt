package dev.alllexey.itmowidgets.feature.settings.presentation

import dev.alllexey.itmowidgets.core.navigation.AppRoute
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.settings.ThemeSpec
import dev.alllexey.itmowidgets.core.text.UiText

/**
 * One settings page. [loaded] turns true only together with the first [sections] built from persisted values, so
 * nothing renders made-up defaults: otherwise every stored `true` would animate off -> on when the page opens.
 */
data class SettingsUiState(
    val page: SettingsPage,
    val sections: List<SettingSection> = emptyList(),
    val loaded: Boolean = false,
    /** The appearance a sample above the rows shows; null on pages without one. */
    val themePreview: ThemeSpec? = null,
    /** The widgets gallery above the rows of [SettingsPage.WIDGETS]; null on every other page. */
    val gallery: WidgetGallery? = null,
)

sealed interface SettingsEvent {
    data object WidgetsRefreshStarted : SettingsEvent
    data object ChooseCustomSpoiler : SettingsEvent
    data object ResetCustomSpoiler : SettingsEvent
    data object OpenDiagnostics : SettingsEvent
    data object CloseOverlays : SettingsEvent
    /** Asks the system to add the QR pass tile; the answer comes back through `onQrTileResult`. */
    data object RequestQrTile : SettingsEvent
    /** Asks for the calendar permission when needed, then reports back through `onCalendarAccessGranted`. */
    data object RequestCalendarAccess : SettingsEvent
    /** The «Выгрузить в .ics» sheet. */
    data object OpenIcsExport : SettingsEvent
    /** The BARS sign-in: an activity on Android, a sheet on iOS. */
    data object OpenBarsLogin : SettingsEvent
    /** A screen outside the settings, such as the web sign-in or the connected devices. */
    data class OpenRoute(val route: AppRoute) : SettingsEvent
    /** A page of the ITMO.Widgets site, [path] relative to its base address. */
    data class OpenWebPage(val path: String) : SettingsEvent
    /** [text] goes to the clipboard; [confirmation] where the platform does not confirm a copy itself. */
    data class CopyText(val text: String, val confirmation: UiText) : SettingsEvent
    data class ShowMessage(val text: UiText) : SettingsEvent
    data class ShowError(val error: AppError) : SettingsEvent
}
