package dev.alllexey.itmowidgets.feature.settings.presentation

import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.schedule.CalendarSyncProblem
import dev.alllexey.itmowidgets.core.schedule.CalendarSyncResult
import dev.alllexey.itmowidgets.core.schedule.CalendarSyncState
import dev.alllexey.itmowidgets.core.testing.FakeCalendarSync
import dev.alllexey.itmowidgets.core.text.AppIcon
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.feature.settings.domain.LocalSettings
import dev.alllexey.itmowidgets.feature.settings.domain.SportDisplaySettings
import dev.alllexey.itmowidgets.shared.core.CoreStrings
import dev.alllexey.itmowidgets.shared.feature.settings.SettingsStrings
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
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

/** The schedule page built and handled by [SchedulePageProvider]. */
@OptIn(ExperimentalCoroutinesApi::class)
class SectionsPageProviderTest {

    private val main = TestMainDispatcher()

    @BeforeTest
    fun setUp() = main.install()

    @AfterTest
    fun tearDown() = main.reset()

    @Test
    fun scheduleAutoSignDisplayRemainsLocalAndRefreshesWidgetsForExplicitTogglesWithServicesDisabled() =
        runTest(main.dispatcher) {
            val fixture = createFixture(page = SettingsPage.SECTIONS, local = LocalSettings(showSportAutoSign = false))
            advanceUntilIdle()

            val section = fixture.viewModel.uiState.value.sections.single { section ->
                section.items.any { it.id == SettingRowId.SCHEDULE_SPORT_AUTO_SIGN }
            }
            val toggle = fixture.viewModel.toggle(SettingRowId.SCHEDULE_SPORT_AUTO_SIGN)
            assertEquals(toggle, section.items.last())
            assertEquals(UiText.Res(SettingsStrings.settings_sections_title), fixture.viewModel.uiState.value.page.title)
            assertEquals(UiText.Res(SettingsStrings.settings_schedule_sport_auto_sign_title), toggle.title)
            assertEquals(UiText.Res(SettingsStrings.settings_schedule_sport_auto_sign_description), toggle.description)
            assertEquals(UiText.Res(SettingsStrings.settings_schedule_footer), section.footer)
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
            assertEquals(null, fixture.viewModel.uiState.value.gallery)
        }

    @Test
    fun sectionsGroupTodayScheduleAndSportWithoutALinkToTheNotifications() =
        runTest(main.dispatcher) {
            val fixture = createFixture(page = SettingsPage.SECTIONS)
            advanceUntilIdle()

            val (today, schedule, sport) = fixture.viewModel.uiState.value.sections
            assertEquals(UiText.Res(CoreStrings.tab_today), today.title)
            assertEquals(listOf(SettingRowId.HOME_PASS_VISIBLE), today.items.map { it.id })
            assertEquals(UiText.Res(SettingsStrings.settings_group_schedule), schedule.title)
            assertEquals(
                listOf(SettingRowId.CALENDAR_SYNC, SettingRowId.ICS_EXPORT, SettingRowId.SCHEDULE_SPORT_AUTO_SIGN),
                schedule.items.map { it.id }
            )
            assertEquals(UiText.Res(SettingsStrings.settings_schedule_footer), schedule.footer)
            assertEquals(UiText.Res(SettingsStrings.settings_group_sport), sport.title)
            assertEquals(listOf(SettingRowId.SPORT_TEACHER_FILTER, SettingRowId.SPORT_TIME_FILTER), sport.items.map { it.id })
            assertTrue(fixture.viewModel.allItems().none { it is SettingItem.Navigation })
        }

    @Test
    fun homePassCanBeHiddenAndShownWithoutRefreshingWidgets() = runTest(main.dispatcher) {
        val fixture = createFixture(page = SettingsPage.SECTIONS)
        advanceUntilIdle()
        assertTrue(fixture.viewModel.toggle(SettingRowId.HOME_PASS_VISIBLE).checked)

        fixture.viewModel.onToggleChanged(SettingRowId.HOME_PASS_VISIBLE, false)
        advanceUntilIdle()
        assertFalse(fixture.viewModel.toggle(SettingRowId.HOME_PASS_VISIBLE).checked)
        assertFalse(fixture.repository.local.value.homePassVisible)

        fixture.viewModel.onToggleChanged(SettingRowId.HOME_PASS_VISIBLE, true)
        advanceUntilIdle()
        assertTrue(fixture.viewModel.toggle(SettingRowId.HOME_PASS_VISIBLE).checked)
        assertEquals(0, fixture.widgetRefresher.refreshCount)
    }

    @Test
    fun theSportFiltersMapToTheStoredHideFlags() = runTest(main.dispatcher) {
        val fixture = createFixture(
            page = SettingsPage.SECTIONS,
            local = LocalSettings(sport = SportDisplaySettings(hideTeacherSelector = true, hideTimeSelector = false))
        )
        advanceUntilIdle()
        assertFalse(fixture.viewModel.toggle(SettingRowId.SPORT_TEACHER_FILTER).checked)
        assertTrue(fixture.viewModel.toggle(SettingRowId.SPORT_TIME_FILTER).checked)

        fixture.viewModel.onToggleChanged(SettingRowId.SPORT_TEACHER_FILTER, true)
        fixture.viewModel.onToggleChanged(SettingRowId.SPORT_TIME_FILTER, false)
        advanceUntilIdle()

        assertEquals(listOf(false), fixture.repository.teacherSelectorHiddenRequests)
        assertEquals(listOf(true), fixture.repository.timeSelectorHiddenRequests)
        assertEquals(0, fixture.widgetRefresher.refreshCount)
    }

    @Test
    fun scheduleAutoSignDisplayRefreshesWidgetsOnlyAfterPersistenceCompletes() =
        runTest(main.dispatcher) {
            val fixture = createFixture(page = SettingsPage.SECTIONS, local = LocalSettings(showSportAutoSign = false))
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
            val fixture = createFixture(page = SettingsPage.SECTIONS, local = LocalSettings(showSportAutoSign = false))
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
                page = SettingsPage.SECTIONS,
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
    fun theScheduleGroupStartsWithTheCalendarRows() = runTest(main.dispatcher) {
        val fixture = createFixture(page = SettingsPage.SECTIONS)
        advanceUntilIdle()

        val toggle = fixture.viewModel.toggle(SettingRowId.CALENDAR_SYNC)
        assertEquals(UiText.Res(SettingsStrings.settings_calendar_sync_title), toggle.title)
        assertEquals(UiText.Res(SettingsStrings.settings_calendar_sync_description), toggle.description)
        assertFalse(toggle.checked)
        val export = fixture.viewModel.action(SettingRowId.ICS_EXPORT)
        assertEquals(UiText.Res(SettingsStrings.settings_ics_export_title), export.title)
        assertEquals(UiText.Res(SettingsStrings.settings_ics_export_description), export.description)
        assertEquals(AppIcon.DOWNLOAD, export.trailingIcon)
        assertTrue(export.enabled)

        fixture.calendarSync.state.value = CalendarSyncState(enabled = true)
        advanceUntilIdle()
        assertTrue(fixture.viewModel.toggle(SettingRowId.CALENDAR_SYNC).checked)
    }

    @Test
    fun aSyncThatTurnedItselfOffSaysWhyInOneLine() = runTest(main.dispatcher) {
        val sync = FakeCalendarSync(CalendarSyncState(problem = CalendarSyncProblem.NO_PERMISSION))
        val fixture = createFixture(page = SettingsPage.SECTIONS, calendarSync = sync)
        advanceUntilIdle()

        assertEquals(
            UiText.Res(SettingsStrings.settings_calendar_sync_no_permission),
            fixture.viewModel.toggle(SettingRowId.CALENDAR_SYNC).description
        )
        sync.state.value = CalendarSyncState(problem = CalendarSyncProblem.CALENDAR_MISSING)
        advanceUntilIdle()
        assertEquals(
            UiText.Res(SettingsStrings.settings_calendar_sync_calendar_missing),
            fixture.viewModel.toggle(SettingRowId.CALENDAR_SYNC).description
        )
    }

    @Test
    fun turningSyncOnAsksForThePermissionThenTurnsOnTheAppCalendarAndOffTurnsItOff() =
        runTest(main.dispatcher) {
            val sync = FakeCalendarSync()
            val fixture = createFixture(page = SettingsPage.SECTIONS, calendarSync = sync)
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
        val fixture = createFixture(page = SettingsPage.SECTIONS, calendarSync = sync)
        val events = recordEvents(fixture)
        advanceUntilIdle()

        for ((result, expected) in listOf(
            CalendarSyncResult.NO_PERMISSION to SettingsEvent.ShowMessage(UiText.Res(SettingsStrings.calendar_access_denied)),
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
        val fixture = createFixture(page = SettingsPage.SECTIONS)
        val events = recordEvents(fixture)
        advanceUntilIdle()

        fixture.viewModel.onAction(SettingRowId.ICS_EXPORT)

        assertEquals(listOf<SettingsEvent>(SettingsEvent.OpenIcsExport), events)
    }

    @Test
    fun pagesOtherThanSectionsDoNotReadTheCalendarState() = runTest(main.dispatcher) {
        val sync = FakeCalendarSync(CalendarSyncState(enabled = true))
        val fixture = createFixture(page = SettingsPage.ABOUT, calendarSync = sync)
        advanceUntilIdle()

        assertTrue(fixture.viewModel.allItems().none { it.id.key.startsWith("calendar") || it.id == SettingRowId.ICS_EXPORT })
    }
}
