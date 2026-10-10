package dev.alllexey.itmowidgets.feature.settings.presentation

import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.settings.ThemeSpec
import dev.alllexey.itmowidgets.core.settings.WidgetPreviewSettings
import dev.alllexey.itmowidgets.core.text.UiText

/**
 * One settings page. [loaded] turns true only together with the first [sections] built from persisted values, so
 * nothing renders made-up defaults: otherwise every stored `true` would animate off -> on when the page opens.
 */
data class SettingsUiState(
    val page: SettingsPage,
    val sections: List<SettingSection> = emptyList(),
    val loaded: Boolean = false,
    /** The widget the page configures, drawn above its rows; null on pages without a widget. */
    val previewSettings: WidgetPreviewSettings? = null,
    /** The appearance a sample above the rows shows; null on pages without one. */
    val themePreview: ThemeSpec? = null
)

sealed interface SettingsEvent {
    data object WidgetsRefreshStarted : SettingsEvent
    data object OpenNotificationSettings : SettingsEvent
    /** Asks for the Android 13 permission, or opens the system page when it cannot be asked. */
    data object RequestNotificationPermission : SettingsEvent
    data object ChooseCustomSpoiler : SettingsEvent
    data object ResetCustomSpoiler : SettingsEvent
    data object OpenDiagnostics : SettingsEvent
    data object CloseOverlays : SettingsEvent
    /** Opens the system page where Android stops restricting the app in the background. */
    data object OpenBackgroundWorkSettings : SettingsEvent
    /** The one-time dialog about background work, offered when a background check is turned on. */
    data object ShowBackgroundWorkHint : SettingsEvent
    /** Asks the system to add the QR pass tile; the answer comes back through `onQrTileResult`. */
    data object RequestQrTile : SettingsEvent
    /** Asks for the calendar permission when needed, then reports back through `onCalendarAccessGranted`. */
    data object RequestCalendarAccess : SettingsEvent
    /** The «Выгрузить в .ics» sheet. */
    data object OpenIcsExport : SettingsEvent
    /** A page of the ITMO.Widgets site, [path] relative to its base address. */
    data class OpenWebPage(val path: String) : SettingsEvent
    data class ShowMessage(val text: UiText) : SettingsEvent
    data class ShowError(val error: AppError) : SettingsEvent
}
