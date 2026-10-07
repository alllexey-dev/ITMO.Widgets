package dev.alllexey.itmowidgets.feature.settings.presentation

import dev.alllexey.itmowidgets.core.platform.PlatformCapabilities
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.schedule.CalendarSync
import dev.alllexey.itmowidgets.core.schedule.CalendarSyncProblem
import dev.alllexey.itmowidgets.core.schedule.CalendarSyncResult
import dev.alllexey.itmowidgets.core.schedule.CalendarSyncState
import dev.alllexey.itmowidgets.core.schedule.ScheduleChangeTracking
import dev.alllexey.itmowidgets.core.text.AppIcon
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.feature.settings.domain.SettingsRepository
import dev.alllexey.itmowidgets.shared.feature.settings.Res
import dev.alllexey.itmowidgets.shared.feature.settings.calendar_access_denied
import dev.alllexey.itmowidgets.shared.feature.settings.settings_calendar_sync_calendar_missing
import dev.alllexey.itmowidgets.shared.feature.settings.settings_calendar_sync_description
import dev.alllexey.itmowidgets.shared.feature.settings.settings_calendar_sync_no_permission
import dev.alllexey.itmowidgets.shared.feature.settings.settings_calendar_sync_title
import dev.alllexey.itmowidgets.shared.feature.settings.settings_ics_export_description
import dev.alllexey.itmowidgets.shared.feature.settings.settings_ics_export_title
import dev.alllexey.itmowidgets.shared.feature.settings.settings_schedule_changes_description
import dev.alllexey.itmowidgets.shared.feature.settings.settings_schedule_changes_notifications_off
import dev.alllexey.itmowidgets.shared.feature.settings.settings_schedule_changes_title
import dev.alllexey.itmowidgets.shared.feature.settings.settings_schedule_footer
import dev.alllexey.itmowidgets.shared.feature.settings.settings_schedule_sport_auto_sign_description
import dev.alllexey.itmowidgets.shared.feature.settings.settings_schedule_sport_auto_sign_title
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * Schedule changes, sport auto sign in the schedule, and the calendar group. Only this page reads the calendar
 * state, and only where the platform offers calendar export. It also handles the background work row, which the
 * recordbook page shows too.
 */
class SchedulePageProvider(
    private val repository: SettingsRepository,
    private val tracking: ScheduleChangeTracking,
    private val calendarSync: CalendarSync,
    private val capabilities: PlatformCapabilities = EveryPlatformCapability
) : SettingsPageProvider {

    override val pages = setOf(SettingsPage.SCHEDULE)

    override val rows = setOf(
        SettingRowId.SCHEDULE_CHANGES,
        SettingRowId.BACKGROUND_WORK,
        SettingRowId.SCHEDULE_SPORT_AUTO_SIGN,
        SettingRowId.CALENDAR_SYNC,
        SettingRowId.ICS_EXPORT
    )

    override fun observeState(page: SettingsPage, state: Flow<SettingsPageState>): Flow<SettingsPageState> =
        if (capabilities.calendarExport) {
            combine(state, calendarSync.observeState()) { pageState, calendar -> pageState.copy(calendar = calendar) }
        } else {
            state
        }

    override fun sections(page: SettingsPage, state: SettingsPageState) = listOfNotNull(
        SettingSection(
            title = null,
            items = listOfNotNull(
                SettingItem.Toggle(
                    id = SettingRowId.SCHEDULE_CHANGES,
                    title = UiText.Res(Res.string.settings_schedule_changes_title),
                    description = UiText.Res(
                        if (state.notificationsGranted == false) {
                            Res.string.settings_schedule_changes_notifications_off
                        } else {
                            Res.string.settings_schedule_changes_description
                        }
                    ),
                    checked = state.local.scheduleChangesEnabled
                ),
                SettingRows.backgroundWork()
                    .takeIf { state.backgroundWorkRestricted && state.local.scheduleChangesEnabled }
            )
        ),
        SettingSection(
            title = null,
            items = listOf(
                SettingItem.Toggle(
                    id = SettingRowId.SCHEDULE_SPORT_AUTO_SIGN,
                    title = UiText.Res(Res.string.settings_schedule_sport_auto_sign_title),
                    description = UiText.Res(Res.string.settings_schedule_sport_auto_sign_description),
                    checked = state.local.showSportAutoSign
                )
            ),
            footer = UiText.Res(Res.string.settings_schedule_footer)
        ),
        calendarSection(state.calendar).takeIf { capabilities.calendarExport }
    )

    override fun onToggleChanged(scope: SettingsPageScope, id: SettingRowId, checked: Boolean) {
        when (id) {
            SettingRowId.SCHEDULE_CHANGES -> scope.updateBackgroundCheck(checked) { tracking.setEnabled(checked) }
            SettingRowId.SCHEDULE_SPORT_AUTO_SIGN -> scope.updateWidgetSetting {
                repository.setScheduleSportAutoSignEnabled(checked)
            }
            SettingRowId.CALENDAR_SYNC -> if (checked) {
                scope.send(SettingsEvent.RequestCalendarAccess)
            } else {
                scope.updateLocalSetting { calendarSync.disable() }
            }
            else -> Unit
        }
    }

    override fun onAction(scope: SettingsPageScope, id: SettingRowId) {
        when (id) {
            SettingRowId.BACKGROUND_WORK -> scope.send(SettingsEvent.OpenBackgroundWorkSettings)
            SettingRowId.ICS_EXPORT -> scope.send(SettingsEvent.OpenIcsExport)
            else -> Unit
        }
    }

    /** The calendar permission is there: synchronization turns on into the app's own calendar. */
    fun onCalendarAccessGranted(scope: SettingsPageScope) {
        scope.launch {
            when (calendarSync.enable()) {
                CalendarSyncResult.DONE -> Unit
                CalendarSyncResult.NO_PERMISSION ->
                    scope.emit(SettingsEvent.ShowMessage(UiText.Res(Res.string.calendar_access_denied)))
                CalendarSyncResult.FAILED -> scope.emit(SettingsEvent.ShowError(AppError.Unknown()))
                CalendarSyncResult.DEMO_UNAVAILABLE -> scope.emit(SettingsEvent.ShowError(AppError.DemoUnavailable))
            }
        }
    }

    /** The switch says where the lessons go, or why it turned itself off. */
    private fun calendarSection(calendar: CalendarSyncState) = SettingSection(
        title = null,
        items = listOf(
            SettingItem.Toggle(
                id = SettingRowId.CALENDAR_SYNC,
                title = UiText.Res(Res.string.settings_calendar_sync_title),
                description = UiText.Res(
                    when (calendar.problem) {
                        CalendarSyncProblem.NO_PERMISSION -> Res.string.settings_calendar_sync_no_permission
                        CalendarSyncProblem.CALENDAR_MISSING -> Res.string.settings_calendar_sync_calendar_missing
                        null -> Res.string.settings_calendar_sync_description
                    }
                ),
                checked = calendar.enabled
            ),
            SettingItem.Action(
                id = SettingRowId.ICS_EXPORT,
                title = UiText.Res(Res.string.settings_ics_export_title),
                description = UiText.Res(Res.string.settings_ics_export_description),
                trailingIcon = AppIcon.DOWNLOAD
            )
        )
    )
}
