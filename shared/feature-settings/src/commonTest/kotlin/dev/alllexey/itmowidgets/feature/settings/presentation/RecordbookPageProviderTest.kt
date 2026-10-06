package dev.alllexey.itmowidgets.feature.settings.presentation

import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.feature.settings.domain.LocalSettings
import dev.alllexey.itmowidgets.shared.feature.settings.Res
import dev.alllexey.itmowidgets.shared.feature.settings.settings_marks_bars_title
import dev.alllexey.itmowidgets.shared.feature.settings.settings_marks_footer
import dev.alllexey.itmowidgets.shared.feature.settings.settings_marks_myitmo_title
import dev.alllexey.itmowidgets.shared.feature.settings.settings_marks_notifications_off
import dev.alllexey.itmowidgets.shared.feature.settings.settings_marks_sheets_title
import dev.alllexey.itmowidgets.testkit.TestMainDispatcher
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest

/** The recordbook page built and handled by [RecordbookPageProvider]. */
@OptIn(ExperimentalCoroutinesApi::class)
class RecordbookPageProviderTest {

    private val main = TestMainDispatcher()

    @BeforeTest
    fun setUp() = main.install()

    @AfterTest
    fun tearDown() = main.reset()

    @Test
    fun recordbookPageShowsTheBARSSwitchOnlyOnceBARSHasAnswered() =
        runTest(main.dispatcher) {
            val fixture = createFixture(page = SettingsPage.RECORDBOOK, local = LocalSettings(myItmoMarksEnabled = false))
            advanceUntilIdle()

            val section = fixture.viewModel.uiState.value.sections.single()
            assertEquals(null, section.title)
            assertEquals(listOf(SettingRowId.MYITMO_MARKS, SettingRowId.SHEET_MARKS), section.items.map { it.id })
            val myItmo = fixture.viewModel.toggle(SettingRowId.MYITMO_MARKS)
            assertEquals(UiText.Res(Res.string.settings_marks_myitmo_title), myItmo.title)
            assertFalse(myItmo.checked)

            for (bars in listOf(false, true)) {
                fixture.repository.barsMarks.value = bars
                advanceUntilIdle()
                assertEquals(
                    listOf(SettingRowId.MYITMO_MARKS, SettingRowId.BARS_MARKS, SettingRowId.SHEET_MARKS),
                    fixture.viewModel.uiState.value.sections.single().items.map { it.id }
                )
                val toggle = fixture.viewModel.toggle(SettingRowId.BARS_MARKS)
                assertEquals(UiText.Res(Res.string.settings_marks_bars_title), toggle.title)
                assertEquals(bars, toggle.checked)
            }
        }

    @Test
    fun recordbookPagePutsTheSheetsSwitchAfterMyITMOAndBARS() =
        runTest(main.dispatcher) {
            val fixture = createFixture(page = SettingsPage.RECORDBOOK, local = LocalSettings(sheetMarksEnabled = false))
            advanceUntilIdle()
            val keys = { fixture.viewModel.uiState.value.sections.single().items.map { it.id } }

            assertEquals(listOf(SettingRowId.MYITMO_MARKS, SettingRowId.SHEET_MARKS), keys())
            val sheets = fixture.viewModel.toggle(SettingRowId.SHEET_MARKS)
            assertEquals(UiText.Res(Res.string.settings_marks_sheets_title), sheets.title)
            assertFalse(sheets.checked)

            fixture.repository.barsMarks.value = true
            fixture.repository.sheetMarks.value = true
            advanceUntilIdle()
            assertEquals(
                listOf(SettingRowId.MYITMO_MARKS, SettingRowId.BARS_MARKS, SettingRowId.SHEET_MARKS),
                keys()
            )
            assertTrue(fixture.viewModel.toggle(SettingRowId.SHEET_MARKS).checked)
        }

    @Test
    fun theSheetsSwitchGoesThroughTrackingAndAsksForNotificationsThenOffersTheHint() =
        runTest(main.dispatcher) {
            val fixture = createFixture(page = SettingsPage.RECORDBOOK, backgroundWork = FakeBackgroundWorkAccess(unrestricted = false))
            val events = recordEvents(fixture)
            fixture.viewModel.onNotificationPermissionChanged(granted = false)
            fixture.viewModel.onBackgroundWorkChanged()
            advanceUntilIdle()

            fixture.viewModel.onToggleChanged(SettingRowId.SHEET_MARKS, false)
            advanceUntilIdle()
            assertEquals(listOf(false), fixture.markTracking.sheetsCalls)
            assertEquals(emptyList<SettingsEvent>(), events)

            fixture.viewModel.onToggleChanged(SettingRowId.SHEET_MARKS, true)
            advanceUntilIdle()
            assertEquals(listOf(false, true), fixture.markTracking.sheetsCalls)
            assertEquals(listOf(SettingsEvent.RequestNotificationPermission, SettingsEvent.ShowBackgroundWorkHint), events)
            assertTrue(fixture.markTracking.myItmoCalls.isEmpty())
            assertTrue(fixture.markTracking.barsCalls.isEmpty())
        }

    @Test
    fun theBackgroundWorkRowFollowsTheSheetsSwitchAlone() =
        runTest(main.dispatcher) {
            val fixture = createFixture(
                page = SettingsPage.RECORDBOOK,
                local = LocalSettings(myItmoMarksEnabled = false, barsMarksEnabled = false),
                backgroundWork = FakeBackgroundWorkAccess(unrestricted = false)
            )
            fixture.viewModel.onBackgroundWorkChanged()
            advanceUntilIdle()
            val keys = { fixture.viewModel.uiState.value.sections.single().items.map { it.id } }
            assertEquals(SettingRowId.BACKGROUND_WORK, keys().last())

            fixture.repository.sheetMarks.value = false
            advanceUntilIdle()
            assertFalse(SettingRowId.BACKGROUND_WORK in keys())
        }

    @Test
    fun recordbookFooterSaysWhenNotificationsAreOff() =
        runTest(main.dispatcher) {
            val fixture = createFixture(page = SettingsPage.RECORDBOOK)
            advanceUntilIdle()
            val footer = { fixture.viewModel.uiState.value.sections.single().footer }
            assertEquals(UiText.Res(Res.string.settings_marks_footer), footer())

            fixture.viewModel.onNotificationPermissionChanged(granted = false)
            advanceUntilIdle()
            assertEquals(UiText.Res(Res.string.settings_marks_notifications_off), footer())

            fixture.viewModel.onNotificationPermissionChanged(granted = true)
            advanceUntilIdle()
            assertEquals(UiText.Res(Res.string.settings_marks_footer), footer())
        }

    @Test
    fun markSwitchesGoThroughTrackingAndTurningOneOnAsksForNotificationsOnlyWhenTheyAreOff() =
        runTest(main.dispatcher) {
            val fixture = createFixture(page = SettingsPage.RECORDBOOK, local = LocalSettings(barsMarksEnabled = false))
            fixture.viewModel.onNotificationPermissionChanged(granted = false)
            advanceUntilIdle()

            fixture.viewModel.onToggleChanged(SettingRowId.MYITMO_MARKS, false)
            advanceUntilIdle()
            assertEquals(listOf(false), fixture.markTracking.myItmoCalls)

            fixture.viewModel.onToggleChanged(SettingRowId.BARS_MARKS, true)
            advanceUntilIdle()
            assertEquals(listOf(true), fixture.markTracking.barsCalls)
            // Only the switch turned on asked; turning My ITMO off did not.
            assertEquals(listOf(SettingsEvent.RequestNotificationPermission), fixture.viewModel.events.take(1).toList())
            assertTrue(fixture.tracking.setCalls.isEmpty())
            assertEquals(0, fixture.widgetRefresher.refreshCount)
        }

    @Test
    fun recordbookPageShowsTheBackgroundWorkRowWhileAnyMarkCheckIsOn() =
        runTest(main.dispatcher) {
            val fixture = createFixture(page = SettingsPage.RECORDBOOK, backgroundWork = FakeBackgroundWorkAccess(unrestricted = false))
            fixture.viewModel.onBackgroundWorkChanged()
            advanceUntilIdle()
            val keys = { fixture.viewModel.uiState.value.sections.single().items.map { it.id } }
            assertEquals(
                listOf(SettingRowId.MYITMO_MARKS, SettingRowId.SHEET_MARKS, SettingRowId.BACKGROUND_WORK),
                keys()
            )

            fixture.repository.myItmoMarks.value = false
            fixture.repository.sheetMarks.value = false
            advanceUntilIdle()
            assertEquals(listOf(SettingRowId.MYITMO_MARKS, SettingRowId.SHEET_MARKS), keys())

            fixture.repository.barsMarks.value = false
            advanceUntilIdle()
            assertEquals(
                listOf(SettingRowId.MYITMO_MARKS, SettingRowId.BARS_MARKS, SettingRowId.SHEET_MARKS),
                keys()
            )

            fixture.repository.barsMarks.value = true
            advanceUntilIdle()
            assertEquals(
                listOf(
                    SettingRowId.MYITMO_MARKS,
                    SettingRowId.BARS_MARKS,
                    SettingRowId.SHEET_MARKS,
                    SettingRowId.BACKGROUND_WORK
                ),
                keys()
            )
        }
}
