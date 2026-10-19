package dev.alllexey.itmowidgets.feature.settings.presentation

import dev.alllexey.itmowidgets.core.platform.PlatformCapabilities
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.schedule.CalendarSync
import dev.alllexey.itmowidgets.core.schedule.CalendarSyncProblem
import dev.alllexey.itmowidgets.core.schedule.CalendarSyncResult
import dev.alllexey.itmowidgets.core.schedule.CalendarSyncState
import dev.alllexey.itmowidgets.core.text.AppIcon
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.feature.settings.domain.SettingsRepository
import dev.alllexey.itmowidgets.shared.core.CoreStrings
import dev.alllexey.itmowidgets.shared.feature.settings.SettingsStrings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * «Разделы»: the pass button of Сегодня, then Расписание (the calendar, `.ics` and sport auto sign in the schedule),
 * then the filters the sport screen shows (a switch on means the filter is shown). Only this page reads the calendar
 * state, and only where the platform offers calendar export.
 */
class SectionsPageProvider(
    private val repository: SettingsRepository,
    private val calendarSync: CalendarSync,
    private val capabilities: PlatformCapabilities = EveryPlatformCapability
) : SettingsPageProvider {

    override val pages = setOf(SettingsPage.SECTIONS)

    override val rows = setOf(
        SettingRowId.HOME_PASS_VISIBLE,
        SettingRowId.SCHEDULE_SPORT_AUTO_SIGN,
        SettingRowId.CALENDAR_SYNC,
        SettingRowId.ICS_EXPORT,
        SettingRowId.SPORT_TEACHER_FILTER,
        SettingRowId.SPORT_TIME_FILTER
    )

    override fun observeState(page: SettingsPage, state: Flow<SettingsPageState>): Flow<SettingsPageState> =
        if (capabilities.calendarExport) {
            combine(state, calendarSync.observeState()) { pageState, calendar -> pageState.copy(calendar = calendar) }
        } else {
            state
        }

    override fun sections(page: SettingsPage, state: SettingsPageState) = listOf(
        SettingSection(
            title = UiText.Res(CoreStrings.tab_today),
            items = listOf(
                SettingItem.Toggle(
                    id = SettingRowId.HOME_PASS_VISIBLE,
                    title = UiText.Res(SettingsStrings.settings_home_pass_title),
                    checked = state.local.homePassVisible
                )
            ),
            footer = UiText.Res(SettingsStrings.settings_home_pass_description)
        ),
        SettingSection(
            title = UiText.Res(SettingsStrings.settings_group_schedule),
            items = calendarRows(state.calendar).takeIf { capabilities.calendarExport }.orEmpty() + SettingItem.Toggle(
                id = SettingRowId.SCHEDULE_SPORT_AUTO_SIGN,
                title = UiText.Res(SettingsStrings.settings_schedule_sport_auto_sign_title),
                description = UiText.Res(SettingsStrings.settings_schedule_sport_auto_sign_description),
                checked = state.local.showSportAutoSign
            ),
            footer = UiText.Res(SettingsStrings.settings_schedule_footer)
        ),
        SettingSection(
            title = UiText.Res(SettingsStrings.settings_group_sport),
            items = listOf(
                SettingItem.Toggle(
                    id = SettingRowId.SPORT_TEACHER_FILTER,
                    title = UiText.Res(SettingsStrings.settings_sport_teacher_filter_title),
                    checked = !state.local.sport.hideTeacherSelector
                ),
                SettingItem.Toggle(
                    id = SettingRowId.SPORT_TIME_FILTER,
                    title = UiText.Res(SettingsStrings.settings_sport_time_filter_title),
                    checked = !state.local.sport.hideTimeSelector
                )
            )
        )
    )

    override fun onToggleChanged(scope: SettingsPageScope, id: SettingRowId, checked: Boolean) {
        when (id) {
            SettingRowId.HOME_PASS_VISIBLE -> scope.updateLocalSetting { repository.setHomePassVisible(checked) }
            SettingRowId.SPORT_TEACHER_FILTER -> scope.updateLocalSetting { repository.setTeacherSelectorHidden(!checked) }
            SettingRowId.SPORT_TIME_FILTER -> scope.updateLocalSetting { repository.setTimeSelectorHidden(!checked) }
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
                    scope.emit(SettingsEvent.ShowMessage(UiText.Res(SettingsStrings.calendar_access_denied)))
                CalendarSyncResult.FAILED -> scope.emit(SettingsEvent.ShowError(AppError.Unknown()))
                CalendarSyncResult.DEMO_UNAVAILABLE -> scope.emit(SettingsEvent.ShowError(AppError.DemoUnavailable))
            }
        }
    }

    /** The switch says where the lessons go, or why it turned itself off. */
    private fun calendarRows(calendar: CalendarSyncState) = listOf(
        SettingItem.Toggle(
            id = SettingRowId.CALENDAR_SYNC,
            title = UiText.Res(SettingsStrings.settings_calendar_sync_title),
            description = UiText.Res(
                when (calendar.problem) {
                    CalendarSyncProblem.NO_PERMISSION -> SettingsStrings.settings_calendar_sync_no_permission
                    CalendarSyncProblem.CALENDAR_MISSING -> SettingsStrings.settings_calendar_sync_calendar_missing
                    null -> SettingsStrings.settings_calendar_sync_description
                }
            ),
            checked = calendar.enabled
        ),
        SettingItem.Action(
            id = SettingRowId.ICS_EXPORT,
            title = UiText.Res(SettingsStrings.settings_ics_export_title),
            description = UiText.Res(SettingsStrings.settings_ics_export_description),
            trailingIcon = AppIcon.DOWNLOAD
        )
    )
}
