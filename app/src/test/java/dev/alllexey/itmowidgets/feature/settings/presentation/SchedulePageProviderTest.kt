package dev.alllexey.itmowidgets.feature.settings.presentation

import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.schedule.CalendarSyncProblem
import dev.alllexey.itmowidgets.core.schedule.CalendarSyncResult
import dev.alllexey.itmowidgets.core.schedule.CalendarSyncState
import dev.alllexey.itmowidgets.core.testing.FakeCalendarSync
import dev.alllexey.itmowidgets.core.testing.MainDispatcherRule
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.feature.settings.domain.LocalSettings
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** The schedule page built and handled by [SchedulePageProvider]. */
@OptIn(ExperimentalCoroutinesApi::class)
class SchedulePageProviderTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `schedule auto sign display remains local and refreshes widgets for explicit toggles with services disabled`() =
        runTest(mainDispatcherRule.dispatcher) {
            val fixture = createFixture(page = SettingsPage.SCHEDULE)
            advanceUntilIdle()

            val section = fixture.viewModel.uiState.value.sections.single { section ->
                section.items.any { it.id == SettingRowId.SCHEDULE_SPORT_AUTO_SIGN }
            }
            val toggle = fixture.viewModel.toggle(SettingRowId.SCHEDULE_SPORT_AUTO_SIGN)
            assertEquals(listOf(toggle), section.items)
            assertEquals(UiText.Resource(R.string.settings_group_schedule), fixture.viewModel.uiState.value.page.title)
            assertEquals(UiText.Resource(R.string.settings_schedule_sport_auto_sign_title), toggle.title)
            assertEquals(UiText.Resource(R.string.settings_schedule_sport_auto_sign_description), toggle.description)
            assertEquals(UiText.Resource(R.string.settings_schedule_footer), section.footer)
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
    fun `schedule page puts the changes switch in its own section above auto sign`() =
        runTest(mainDispatcherRule.dispatcher) {
            val fixture = createFixture(page = SettingsPage.SCHEDULE, local = LocalSettings(scheduleChangesEnabled = false))
            advanceUntilIdle()

            val (changes, autoSign) = fixture.viewModel.uiState.value.sections
            val toggle = fixture.viewModel.toggle(SettingRowId.SCHEDULE_CHANGES)
            assertEquals(listOf(toggle), changes.items)
            assertEquals(null, changes.title)
            assertEquals(null, changes.footer)
            assertEquals(UiText.Resource(R.string.settings_schedule_changes_title), toggle.title)
            assertFalse(toggle.checked)
            assertEquals(listOf(SettingRowId.SCHEDULE_SPORT_AUTO_SIGN), autoSign.items.map { it.id })
            assertEquals(UiText.Resource(R.string.settings_schedule_footer), autoSign.footer)

            fixture.tracking.enabled.value = true
            advanceUntilIdle()
            assertTrue(fixture.viewModel.toggle(SettingRowId.SCHEDULE_CHANGES).checked)
        }

    @Test
    fun `schedule changes switch goes through tracking and asks for notifications only when they are off`() =
        runTest(mainDispatcherRule.dispatcher) {
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
    fun `schedule changes description says when notifications are off`() =
        runTest(mainDispatcherRule.dispatcher) {
            val fixture = createFixture(page = SettingsPage.SCHEDULE)
            advanceUntilIdle()
            val description = { fixture.viewModel.toggle(SettingRowId.SCHEDULE_CHANGES).description }
            assertEquals(UiText.Resource(R.string.settings_schedule_changes_description), description())

            fixture.viewModel.onNotificationPermissionChanged(granted = false)
            advanceUntilIdle()
            assertEquals(UiText.Resource(R.string.settings_schedule_changes_notifications_off), description())

            fixture.viewModel.onNotificationPermissionChanged(granted = true)
            advanceUntilIdle()
            assertEquals(UiText.Resource(R.string.settings_schedule_changes_description), description())
        }

    @Test
    fun `schedule page shows the background work row after the changes switch only while restricted and on`() =
        runTest(mainDispatcherRule.dispatcher) {
            val fixture = createFixture(page = SettingsPage.SCHEDULE, backgroundWork = FakeBackgroundWorkAccess(unrestricted = false))
            advanceUntilIdle()
            val keys = { fixture.viewModel.uiState.value.sections.first().items.map { it.id } }
            // Not asked yet: the row stays out rather than flash in.
            assertEquals(listOf(SettingRowId.SCHEDULE_CHANGES), keys())

            fixture.viewModel.onBackgroundWorkChanged()
            advanceUntilIdle()
            assertEquals(listOf(SettingRowId.SCHEDULE_CHANGES, SettingRowId.BACKGROUND_WORK), keys())
            val row = fixture.viewModel.action(SettingRowId.BACKGROUND_WORK)
            assertEquals(UiText.Resource(R.string.settings_background_work_title), row.title)
            assertEquals(UiText.Resource(R.string.background_work_hint), row.description)
            assertEquals(R.drawable.ic_open_in_new, row.trailingIconRes)
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
    fun `the background work row opens the system page`() =
        runTest(mainDispatcherRule.dispatcher) {
            val fixture = createFixture(page = SettingsPage.SCHEDULE, backgroundWork = FakeBackgroundWorkAccess(unrestricted = false))
            advanceUntilIdle()

            fixture.viewModel.onAction(SettingRowId.BACKGROUND_WORK)

            assertEquals(SettingsEvent.OpenBackgroundWorkSettings, fixture.viewModel.events.first())
        }

    @Test
    fun `schedule auto sign display refreshes widgets only after persistence completes`() =
        runTest(mainDispatcherRule.dispatcher) {
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
    fun `schedule auto sign persistence failure does not refresh widgets or enable services`() =
        runTest(mainDispatcherRule.dispatcher) {
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
    fun `schedule auto sign display waits for and follows persisted values`() =
        runTest(mainDispatcherRule.dispatcher) {
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
    fun `schedule page ends with the calendar group`() = runTest(mainDispatcherRule.dispatcher) {
        val fixture = createFixture(page = SettingsPage.SCHEDULE)
        advanceUntilIdle()

        val calendar = fixture.viewModel.uiState.value.sections.last()
        assertEquals(null, calendar.title)
        assertEquals(null, calendar.footer)
        assertEquals(listOf(SettingRowId.CALENDAR_SYNC, SettingRowId.ICS_EXPORT), calendar.items.map { it.id })
        val toggle = fixture.viewModel.toggle(SettingRowId.CALENDAR_SYNC)
        assertEquals(UiText.Resource(R.string.settings_calendar_sync_title), toggle.title)
        assertEquals(UiText.Resource(R.string.settings_calendar_sync_description), toggle.description)
        assertFalse(toggle.checked)
        val export = fixture.viewModel.action(SettingRowId.ICS_EXPORT)
        assertEquals(UiText.Resource(R.string.settings_ics_export_title), export.title)
        assertEquals(UiText.Resource(R.string.settings_ics_export_description), export.description)
        assertEquals(R.drawable.ic_download, export.trailingIconRes)
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
    fun `a sync that turned itself off says why in one line`() = runTest(mainDispatcherRule.dispatcher) {
        val sync = FakeCalendarSync(CalendarSyncState(problem = CalendarSyncProblem.NO_PERMISSION))
        val fixture = createFixture(page = SettingsPage.SCHEDULE, calendarSync = sync)
        advanceUntilIdle()

        assertEquals(
            UiText.Resource(R.string.settings_calendar_sync_no_permission),
            fixture.viewModel.toggle(SettingRowId.CALENDAR_SYNC).description
        )
        sync.state.value = CalendarSyncState(problem = CalendarSyncProblem.CALENDAR_MISSING)
        advanceUntilIdle()
        assertEquals(
            UiText.Resource(R.string.settings_calendar_sync_calendar_missing),
            fixture.viewModel.toggle(SettingRowId.CALENDAR_SYNC).description
        )
    }

    @Test
    fun `turning sync on asks for the permission, then turns on the app calendar, and off turns it off`() =
        runTest(mainDispatcherRule.dispatcher) {
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
    fun `a refused enable leaves the switch off with a message`() = runTest(mainDispatcherRule.dispatcher) {
        val sync = FakeCalendarSync()
        val fixture = createFixture(page = SettingsPage.SCHEDULE, calendarSync = sync)
        val events = recordEvents(fixture)
        advanceUntilIdle()

        for ((result, expected) in listOf(
            CalendarSyncResult.NO_PERMISSION to SettingsEvent.ShowMessage(UiText.Resource(R.string.calendar_access_denied)),
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
    fun `the ics row opens the export sheet`() = runTest(mainDispatcherRule.dispatcher) {
        val fixture = createFixture(page = SettingsPage.SCHEDULE)
        val events = recordEvents(fixture)
        advanceUntilIdle()

        fixture.viewModel.onAction(SettingRowId.ICS_EXPORT)

        assertEquals(listOf<SettingsEvent>(SettingsEvent.OpenIcsExport), events)
    }

    @Test
    fun `pages other than schedule do not read the calendar state`() = runTest(mainDispatcherRule.dispatcher) {
        val sync = FakeCalendarSync(CalendarSyncState(enabled = true))
        val fixture = createFixture(page = SettingsPage.RECORDBOOK, calendarSync = sync)
        advanceUntilIdle()

        assertTrue(fixture.viewModel.allItems().none { it.id.key.startsWith("calendar") || it.id == SettingRowId.ICS_EXPORT })
    }
}
