package dev.alllexey.itmowidgets.feature.settings.presentation

import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.schedule.CalendarSyncProblem
import dev.alllexey.itmowidgets.core.schedule.CalendarSyncResult
import dev.alllexey.itmowidgets.core.schedule.CalendarSyncState
import dev.alllexey.itmowidgets.core.testing.FakeCalendarSync
import dev.alllexey.itmowidgets.core.text.AppIcon
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.feature.settings.domain.LocalSettings
import dev.alllexey.itmowidgets.shared.feature.settings.Res
import dev.alllexey.itmowidgets.shared.feature.settings.background_work_hint
import dev.alllexey.itmowidgets.shared.feature.settings.calendar_access_denied
import dev.alllexey.itmowidgets.shared.feature.settings.settings_background_work_title
import dev.alllexey.itmowidgets.shared.feature.settings.settings_calendar_sync_calendar_missing
import dev.alllexey.itmowidgets.shared.feature.settings.settings_calendar_sync_description
import dev.alllexey.itmowidgets.shared.feature.settings.settings_calendar_sync_no_permission
import dev.alllexey.itmowidgets.shared.feature.settings.settings_calendar_sync_title
import dev.alllexey.itmowidgets.shared.feature.settings.settings_group_schedule
import dev.alllexey.itmowidgets.shared.feature.settings.settings_ics_export_description
import dev.alllexey.itmowidgets.shared.feature.settings.settings_ics_export_title
import dev.alllexey.itmowidgets.shared.feature.settings.settings_schedule_changes_description
import dev.alllexey.itmowidgets.shared.feature.settings.settings_schedule_changes_notifications_off
import dev.alllexey.itmowidgets.shared.feature.settings.settings_schedule_changes_title
import dev.alllexey.itmowidgets.shared.feature.settings.settings_schedule_footer
import dev.alllexey.itmowidgets.shared.feature.settings.settings_schedule_sport_auto_sign_description
import dev.alllexey.itmowidgets.shared.feature.settings.settings_schedule_sport_auto_sign_title
import dev.alllexey.itmowidgets.testkit.TestMainDispatcher
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

/** The schedule page built and handled by [SchedulePageProvider]. */
@OptIn(ExperimentalCoroutinesApi::class)
class SchedulePageProviderTest {

    private val main = TestMainDispatcher()

    @BeforeTest
    fun setUp() = main.install()

    @AfterTest
    fun tearDown() = main.reset()

    @Test
    fun scheduleAutoSignDisplayRemainsLocalAndRefreshesWidgetsForExplicitTogglesWithServicesDisabled() =
        runTest(main.dispatcher) {
            val fixture = createFixture(page = SettingsPage.SCHEDULE)
            advanceUntilIdle()

            val section = fixture.viewModel.uiState.value.sections.single { section ->
                section.items.any { it.id == SettingRowId.SCHEDULE_SPORT_AUTO_SIGN }
            }
            val toggle = fixture.viewModel.toggle(SettingRowId.SCHEDULE_SPORT_AUTO_SIGN)
            assertEquals(listOf(toggle), section.items)
            assertEquals(UiText.Res(Res.string.settings_group_schedule), fixture.viewModel.uiState.value.page.title)
            assertEquals(UiText.Res(Res.string.settings_schedule_sport_auto_sign_title), toggle.title)
            assertEquals(UiText.Res(Res.string.settings_schedule_sport_auto_sign_description), toggle.description)
            assertEquals(UiText.Res(Res.string.settings_schedule_footer), section.footer)
            assertTrue(toggle.enabled)
            assertTrue(toggle.stateKnown)
            assertFalse(toggle.checked)
            assertEquals(0, fixture.widgetRefresher.refreshCount)

            fixture.viewModel.onToggleChanged(SettingRowId.SCHEDULE_SPORT_AUTO_SIGN, true)
            advanceUntilIdle()
            assertTrue(fixture.viewModel.toggle(SettingRowId.SCHEDULE_SPORT_AUTO_SIGN).checked)
            assertTrue(fixture.repository.local.value.showSportAutoSign)
            assertEquals(1, fixture.widgetRefresher.refreshCount)

            fixture.viewModel.onToggleChanged(SettingRowId.SCHEDULE_SPORT_AUTO_SIGN, false)
            advanceUntilIdle()
            assertFalse(fixture.viewModel.toggle(SettingRowId.SCHEDULE_SPORT_AUTO_SIGN).checked)
            assertEquals(listOf(true, false), fixture.repository.scheduleSportAutoSignRequests)
            assertFalse(fixture.repository.local.value.customServicesEnabled)
            assertTrue(fixture.customServicesRepository.requests.isEmpty())
            assertEquals(0, fixture.repository.refreshSharingCount)
            assertTrue(fixture.repository.scheduleSharingRequests.isEmpty())
            assertTrue(fixture.repository.sportSharingRequests.isEmpty())
            assertEquals(2, fixture.widgetRefresher.refreshCount)
            assertEquals(null, fixture.viewModel.uiState.value.previewSettings)
        }

    @Test
    fun schedulePagePutsTheChangesSwitchInItsOwnSectionAboveAutoSign() =
        runTest(main.dispatcher) {
            val fixture = createFixture(page = SettingsPage.SCHEDULE, local = LocalSettings(scheduleChangesEnabled = false))
            advanceUntilIdle()

            val (changes, autoSign) = fixture.viewModel.uiState.value.sections
            val toggle = fixture.viewModel.toggle(SettingRowId.SCHEDULE_CHANGES)
            assertEquals(listOf(toggle), changes.items)
            assertEquals(null, changes.title)
            assertEquals(null, changes.footer)
            assertEquals(UiText.Res(Res.string.settings_schedule_changes_title), toggle.title)
            assertFalse(toggle.checked)
            assertEquals(listOf(SettingRowId.SCHEDULE_SPORT_AUTO_SIGN), autoSign.items.map { it.id })
            assertEquals(UiText.Res(Res.string.settings_schedule_footer), autoSign.footer)

            fixture.tracking.enabled.value = true
            advanceUntilIdle()
            assertTrue(fixture.viewModel.toggle(SettingRowId.SCHEDULE_CHANGES).checked)
        }

    @Test
    fun scheduleChangesSwitchGoesThroughTrackingAndAsksForNotificationsOnlyWhenTheyAreOff() =
        runTest(main.dispatcher) {
            val fixture = createFixture(page = SettingsPage.SCHEDULE)
            fixture.viewModel.onNotificationPermissionChanged(granted = true)
            advanceUntilIdle()

            fixture.viewModel.onToggleChanged(SettingRowId.SCHEDULE_CHANGES, false)
            advanceUntilIdle()
            assertEquals(listOf(false), fixture.tracking.setCalls)
            assertFalse(fixture.viewModel.toggle(SettingRowId.SCHEDULE_CHANGES).checked)

            fixture.viewModel.onToggleChanged(SettingRowId.SCHEDULE_CHANGES, true)
            advanceUntilIdle()
            assertEquals(listOf(false, true), fixture.tracking.setCalls)

            fixture.viewModel.onNotificationPermissionChanged(granted = false)
            fixture.viewModel.onToggleChanged(SettingRowId.SCHEDULE_CHANGES, true)
            advanceUntilIdle()
            assertEquals(listOf(false, true, true), fixture.tracking.setCalls)
            assertEquals(listOf(SettingsEvent.RequestNotificationPermission), fixture.viewModel.events.take(1).toList())
            assertEquals(0, fixture.widgetRefresher.refreshCount)
            assertTrue(fixture.repository.homeCardRequests.isEmpty())
        }

    @Test
    fun scheduleChangesDescriptionSaysWhenNotificationsAreOff() =
        runTest(main.dispatcher) {
            val fixture = createFixture(page = SettingsPage.SCHEDULE)
            advanceUntilIdle()
            val description = { fixture.viewModel.toggle(SettingRowId.SCHEDULE_CHANGES).description }
            assertEquals(UiText.Res(Res.string.settings_schedule_changes_description), description())

            fixture.viewModel.onNotificationPermissionChanged(granted = false)
            advanceUntilIdle()
            assertEquals(UiText.Res(Res.string.settings_schedule_changes_notifications_off), description())

            fixture.viewModel.onNotificationPermissionChanged(granted = true)
            advanceUntilIdle()
            assertEquals(UiText.Res(Res.string.settings_schedule_changes_description), description())
        }

    @Test
    fun schedulePageShowsTheBackgroundWorkRowAfterTheChangesSwitchOnlyWhileRestrictedAndOn() =
        runTest(main.dispatcher) {
            val fixture = createFixture(page = SettingsPage.SCHEDULE, backgroundWork = FakeBackgroundWorkAccess(unrestricted = false))
            advanceUntilIdle()
            val keys = { fixture.viewModel.uiState.value.sections.first().items.map { it.id } }
            // Not asked yet: the row stays out rather than flash in.
            assertEquals(listOf(SettingRowId.SCHEDULE_CHANGES), keys())

            fixture.viewModel.onBackgroundWorkChanged()
            advanceUntilIdle()
            assertEquals(listOf(SettingRowId.SCHEDULE_CHANGES, SettingRowId.BACKGROUND_WORK), keys())
            val row = fixture.viewModel.action(SettingRowId.BACKGROUND_WORK)
            assertEquals(UiText.Res(Res.string.settings_background_work_title), row.title)
            assertEquals(UiText.Res(Res.string.background_work_hint), row.description)
            assertEquals(AppIcon.OPEN_IN_NEW, row.trailingIcon)
            assertTrue(row.enabled)

            fixture.tracking.enabled.value = false
            advanceUntilIdle()
            assertEquals(listOf(SettingRowId.SCHEDULE_CHANGES), keys())

            fixture.tracking.enabled.value = true
            fixture.backgroundWork.unrestricted = true
            fixture.viewModel.onBackgroundWorkChanged()
            advanceUntilIdle()
            assertEquals(listOf(SettingRowId.SCHEDULE_CHANGES), keys())
        }

    @Test
    fun theBackgroundWorkRowOpensTheSystemPage() =
        runTest(main.dispatcher) {
            val fixture = createFixture(page = SettingsPage.SCHEDULE, backgroundWork = FakeBackgroundWorkAccess(unrestricted = false))
            advanceUntilIdle()

            fixture.viewModel.onAction(SettingRowId.BACKGROUND_WORK)

            assertEquals(SettingsEvent.OpenBackgroundWorkSettings, fixture.viewModel.events.first())
        }

    @Test
    fun scheduleAutoSignDisplayRefreshesWidgetsOnlyAfterPersistenceCompletes() =
        runTest(main.dispatcher) {
            val fixture = createFixture(page = SettingsPage.SCHEDULE)
            val persisted = CompletableDeferred<Unit>()
            fixture.repository.scheduleSportAutoSignWrite = { persisted.await() }
            advanceUntilIdle()

            fixture.viewModel.onToggleChanged(SettingRowId.SCHEDULE_SPORT_AUTO_SIGN, true)
            runCurrent()

            assertEquals(listOf(true), fixture.repository.scheduleSportAutoSignRequests)
            assertFalse(fixture.repository.local.value.showSportAutoSign)
            assertEquals(0, fixture.widgetRefresher.refreshCount)

            persisted.complete(Unit)
            advanceUntilIdle()

            assertTrue(fixture.repository.local.value.showSportAutoSign)
            assertTrue(fixture.viewModel.toggle(SettingRowId.SCHEDULE_SPORT_AUTO_SIGN).checked)
            assertEquals(1, fixture.widgetRefresher.refreshCount)
        }

    @Test
    fun scheduleAutoSignPersistenceFailureDoesNotRefreshWidgetsOrEnableServices() =
        runTest(main.dispatcher) {
            val fixture = createFixture(page = SettingsPage.SCHEDULE)
            val failure = IllegalStateException("Preference write failed")
            fixture.repository.scheduleSportAutoSignWrite = { throw failure }
            advanceUntilIdle()

            fixture.viewModel.onToggleChanged(SettingRowId.SCHEDULE_SPORT_AUTO_SIGN, true)
            advanceUntilIdle()

            assertEquals(listOf(true), fixture.repository.scheduleSportAutoSignRequests)
            assertFalse(fixture.repository.local.value.showSportAutoSign)
            assertFalse(fixture.viewModel.toggle(SettingRowId.SCHEDULE_SPORT_AUTO_SIGN).checked)
            assertEquals(0, fixture.widgetRefresher.refreshCount)
            assertFalse(fixture.repository.local.value.customServicesEnabled)
            assertTrue(fixture.customServicesRepository.requests.isEmpty())
            assertEquals(SettingsEvent.ShowError(AppError.Unknown(failure)), fixture.viewModel.events.first())
        }

    @Test
    fun scheduleAutoSignDisplayWaitsForAndFollowsPersistedValues() =
        runTest(main.dispatcher) {
            val fixture = createFixture(
                page = SettingsPage.SCHEDULE,
                local = LocalSettings(showSportAutoSign = true),
                localInitiallyAvailable = false
            )
            advanceUntilIdle()
            assertTrue(fixture.viewModel.uiState.value.sections.isEmpty())

            fixture.repository.publishLocalSettings()
            advanceUntilIdle()
            assertTrue(fixture.viewModel.toggle(SettingRowId.SCHEDULE_SPORT_AUTO_SIGN).checked)

            fixture.repository.local.value = fixture.repository.local.value.copy(showSportAutoSign = false)
            advanceUntilIdle()
            assertFalse(fixture.viewModel.toggle(SettingRowId.SCHEDULE_SPORT_AUTO_SIGN).checked)
            assertTrue(fixture.repository.scheduleSportAutoSignRequests.isEmpty())
            assertEquals(0, fixture.widgetRefresher.refreshCount)
        }

    @Test
    fun schedulePageEndsWithTheCalendarGroup() = runTest(main.dispatcher) {
        val fixture = createFixture(page = SettingsPage.SCHEDULE)
        advanceUntilIdle()

        val calendar = fixture.viewModel.uiState.value.sections.last()
        assertEquals(null, calendar.title)
        assertEquals(null, calendar.footer)
        assertEquals(listOf(SettingRowId.CALENDAR_SYNC, SettingRowId.ICS_EXPORT), calendar.items.map { it.id })
        val toggle = fixture.viewModel.toggle(SettingRowId.CALENDAR_SYNC)
        assertEquals(UiText.Res(Res.string.settings_calendar_sync_title), toggle.title)
        assertEquals(UiText.Res(Res.string.settings_calendar_sync_description), toggle.description)
        assertFalse(toggle.checked)
        val export = fixture.viewModel.action(SettingRowId.ICS_EXPORT)
        assertEquals(UiText.Res(Res.string.settings_ics_export_title), export.title)
        assertEquals(UiText.Res(Res.string.settings_ics_export_description), export.description)
        assertEquals(AppIcon.DOWNLOAD, export.trailingIcon)
        assertTrue(export.enabled)

        fixture.calendarSync.state.value = CalendarSyncState(enabled = true)
        advanceUntilIdle()
        assertTrue(fixture.viewModel.toggle(SettingRowId.CALENDAR_SYNC).checked)
        assertEquals(
            listOf(SettingRowId.CALENDAR_SYNC, SettingRowId.ICS_EXPORT),
            fixture.viewModel.uiState.value.sections.last().items.map { it.id }
        )
    }

    @Test
    fun aSyncThatTurnedItselfOffSaysWhyInOneLine() = runTest(main.dispatcher) {
        val sync = FakeCalendarSync(CalendarSyncState(problem = CalendarSyncProblem.NO_PERMISSION))
        val fixture = createFixture(page = SettingsPage.SCHEDULE, calendarSync = sync)
        advanceUntilIdle()

        assertEquals(
            UiText.Res(Res.string.settings_calendar_sync_no_permission),
            fixture.viewModel.toggle(SettingRowId.CALENDAR_SYNC).description
        )
        sync.state.value = CalendarSyncState(problem = CalendarSyncProblem.CALENDAR_MISSING)
        advanceUntilIdle()
        assertEquals(
            UiText.Res(Res.string.settings_calendar_sync_calendar_missing),
            fixture.viewModel.toggle(SettingRowId.CALENDAR_SYNC).description
        )
    }

    @Test
    fun turningSyncOnAsksForThePermissionThenTurnsOnTheAppCalendarAndOffTurnsItOff() =
        runTest(main.dispatcher) {
            val sync = FakeCalendarSync()
            val fixture = createFixture(page = SettingsPage.SCHEDULE, calendarSync = sync)
            val events = recordEvents(fixture)
            advanceUntilIdle()

            fixture.viewModel.onToggleChanged(SettingRowId.CALENDAR_SYNC, true)
            advanceUntilIdle()
            assertEquals(listOf<SettingsEvent>(SettingsEvent.RequestCalendarAccess), events)
            assertEquals(0, sync.enables)

            fixture.viewModel.onCalendarAccessGranted()
            advanceUntilIdle()
            assertEquals(1, sync.enables)
            assertTrue(fixture.viewModel.toggle(SettingRowId.CALENDAR_SYNC).checked)

            fixture.viewModel.onToggleChanged(SettingRowId.CALENDAR_SYNC, false)
            advanceUntilIdle()
            assertEquals(1, sync.disables)
            assertEquals(1, events.size)
        }

    @Test
    fun aRefusedEnableLeavesTheSwitchOffWithAMessage() = runTest(main.dispatcher) {
        val sync = FakeCalendarSync()
        val fixture = createFixture(page = SettingsPage.SCHEDULE, calendarSync = sync)
        val events = recordEvents(fixture)
        advanceUntilIdle()

        for ((result, expected) in listOf(
            CalendarSyncResult.NO_PERMISSION to SettingsEvent.ShowMessage(UiText.Res(Res.string.calendar_access_denied)),
            CalendarSyncResult.FAILED to SettingsEvent.ShowError(AppError.Unknown())
        )) {
            sync.enableResult = result
            fixture.viewModel.onCalendarAccessGranted()
            advanceUntilIdle()
            assertEquals(expected, events.last())
            assertFalse(fixture.viewModel.toggle(SettingRowId.CALENDAR_SYNC).checked)
        }
    }

    @Test
    fun theIcsRowOpensTheExportSheet() = runTest(main.dispatcher) {
        val fixture = createFixture(page = SettingsPage.SCHEDULE)
        val events = recordEvents(fixture)
        advanceUntilIdle()

        fixture.viewModel.onAction(SettingRowId.ICS_EXPORT)

        assertEquals(listOf<SettingsEvent>(SettingsEvent.OpenIcsExport), events)
    }

    @Test
    fun pagesOtherThanScheduleDoNotReadTheCalendarState() = runTest(main.dispatcher) {
        val sync = FakeCalendarSync(CalendarSyncState(enabled = true))
        val fixture = createFixture(page = SettingsPage.RECORDBOOK, calendarSync = sync)
        advanceUntilIdle()

        assertTrue(fixture.viewModel.allItems().none { it.id.key.startsWith("calendar") || it.id == SettingRowId.ICS_EXPORT })
    }
}
