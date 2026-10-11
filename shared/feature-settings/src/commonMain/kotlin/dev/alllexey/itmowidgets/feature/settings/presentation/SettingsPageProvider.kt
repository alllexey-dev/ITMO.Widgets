package dev.alllexey.itmowidgets.feature.settings.presentation

import dev.alllexey.itmowidgets.core.schedule.CalendarSyncState
import dev.alllexey.itmowidgets.core.settings.ThemeSpec
import dev.alllexey.itmowidgets.core.settings.WidgetPreviewSettings
import dev.alllexey.itmowidgets.feature.settings.domain.LocalSettings
import dev.alllexey.itmowidgets.feature.settings.domain.SharingSettingsState
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

    /** The widget [page] configures, drawn above its rows. */
    fun preview(page: SettingsPage, local: LocalSettings): WidgetPreviewSettings? = null

    /** The appearance [page] shows a sample of above its rows. */
    fun themePreview(page: SettingsPage, local: LocalSettings): ThemeSpec? = null

    fun onToggleChanged(scope: SettingsPageScope, id: SettingRowId, checked: Boolean) = Unit

    fun onChoiceChanged(scope: SettingsPageScope, id: SettingRowId, optionKey: String) = Unit

    fun onAction(scope: SettingsPageScope, id: SettingRowId) = Unit
}

/**
 * Everything a settings page is built from. [calendar] and [diagnosticsCount] are read only by the pages that show
 * them; other pages keep the defaults.
 */
data class SettingsPageState(
    val local: LocalSettings,
    val sharing: SharingSettingsState,
    /** Null until the screen has asked the system. */
    val notificationsGranted: Boolean?,
    val hasCustomSpoiler: Boolean,
    val imageBusy: Boolean,
    /** True only once Android is known to restrict the app in the background. */
    val backgroundWorkRestricted: Boolean,
    val calendar: CalendarSyncState = CalendarSyncState(),
    val diagnosticsCount: Int = 0
)
