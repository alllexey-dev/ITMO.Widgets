package dev.alllexey.itmowidgets.feature.settings.presentation

import dev.alllexey.itmowidgets.core.schedule.CalendarSyncState
import dev.alllexey.itmowidgets.core.settings.WidgetFormat
import dev.alllexey.itmowidgets.feature.settings.domain.BarsConnection
import dev.alllexey.itmowidgets.feature.settings.domain.LocalSettings
import kotlinx.coroutines.flow.Flow

/**
 * One group of settings pages: it builds their rows and handles the rows it owns. A row's visibility condition sits
 * in the provider that builds it, so a platform check is one line there.
 */
interface SettingsPageProvider {

    /** The pages this provider builds; every page has exactly one provider. */
    val pages: Set<SettingsPage>

    /** The rows whose actions this provider handles; every row except navigation has exactly one handler. */
    val rows: Set<SettingRowId>

    fun sections(page: SettingsPage, state: SettingsPageState): List<SettingSection>

    /**
     * The state [page] is built from. A provider whose page reads a source of its own adds it here, so no other page
     * waits on that source.
     */
    fun observeState(page: SettingsPage, state: Flow<SettingsPageState>): Flow<SettingsPageState> = state

    /** The widgets gallery [page] shows above its rows. */
    fun gallery(page: SettingsPage, state: SettingsPageState): WidgetGallery? = null

    fun onToggleChanged(scope: SettingsPageScope, id: SettingRowId, checked: Boolean) = Unit

    fun onChoiceChanged(scope: SettingsPageScope, id: SettingRowId, optionKey: String) = Unit

    fun onAction(scope: SettingsPageScope, id: SettingRowId) = Unit
}

/**
 * Everything a settings page is built from. [calendar], [diagnosticsCount] and [bars] are read only by the pages that
 * show them; other pages keep the defaults.
 */
data class SettingsPageState(
    val local: LocalSettings,
    /** Null until the screen has asked the system. */
    val notificationsGranted: Boolean?,
    val hasCustomSpoiler: Boolean,
    val imageBusy: Boolean,
    val calendar: CalendarSyncState = CalendarSyncState(),
    val diagnosticsCount: Int = 0,
    val bars: BarsConnection = BarsConnection.NOT_CONNECTED,
    /** How many widgets of each format the home screen holds, as the gallery screen last counted; empty until then. */
    val placedWidgets: Map<WidgetFormat, Int> = emptyMap(),
)
