package dev.alllexey.itmowidgets.feature.settings.presentation

import dev.alllexey.itmowidgets.core.settings.WidgetFormat
import dev.alllexey.itmowidgets.core.platform.PlatformCapabilities
import dev.alllexey.itmowidgets.core.schedule.CalendarSyncProblem
import dev.alllexey.itmowidgets.core.schedule.CalendarSyncState
import dev.alllexey.itmowidgets.core.settings.QrWidgetSettings
import dev.alllexey.itmowidgets.core.testing.FakeCalendarSync
import dev.alllexey.itmowidgets.feature.settings.domain.LocalSettings
import dev.alllexey.itmowidgets.testkit.TestMainDispatcher
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest

/**
 * The rows a platform without a feature does not list (iOS until each feature's IO card): the pages show no entry
 * point to it, never a placeholder, and keep every other row.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PlatformCapabilitiesPagesTest {

    private val main = TestMainDispatcher()

    private val none = PlatformCapabilities(
        quickSettingsTile = false,
        backgroundWorkSettings = false,
        updateChannel = false,
        calendarExport = false,
        recordbook = false,
        marks = false,
        reviews = false,
        qrWidgetAnimation = false,
        qrCustomSpoiler = false,
        wallpaperColors = false,
        pinWidgets = false,
        lockScreenWidgets = false,
    )

    @BeforeTest
    fun setUp() = main.install()

    @AfterTest
    fun tearDown() = main.reset()

    @Test
    fun qrPageDropsTheTileTheAnimationAndTheCustomImageWithoutTheirCapabilities() = runTest(main.dispatcher) {
        val local = LocalSettings(qrWidget = QrWidgetSettings(spoilerEnabled = true))
        val fixture = createFixture(
            page = SettingsPage.WIDGETS,
            local = local,
            tileAccess = FakeQuickSettingsTileAccess(canRequest = true),
            capabilities = none
        )
        advanceUntilIdle()

        val pass = fixture.viewModel.card(WidgetFormat.PASS)
        assertEquals(listOf(SettingRowId.QR_DYNAMIC_COLORS, SettingRowId.QR_SPOILER), pass.settings.flatMap { it.items }.map { it.id })
        assertEquals(1, pass.settings.size)
        // No launcher pinning and no Lock Screen widgets without the capabilities.
        assertEquals(WidgetCardAction.NONE, pass.action)
        assertTrue(checkNotNull(fixture.viewModel.uiState.value.gallery).howToAdd)
    }

    @Test
    fun qrPageKeepsTheSpoilerGroupWhenOnlyTheAnimationIsOffered() = runTest(main.dispatcher) {
        val fixture = createFixture(
            page = SettingsPage.WIDGETS,
            capabilities = none.copy(qrWidgetAnimation = true)
        )
        advanceUntilIdle()

        assertEquals(
            listOf(SettingRowId.QR_DYNAMIC_COLORS, SettingRowId.QR_SPOILER, SettingRowId.QR_ANIMATION),
            fixture.viewModel.card(WidgetFormat.PASS).settings.flatMap { it.items }.map { it.id }
        )
    }

    @Test
    fun schedulePageHasNoCalendarRowsWithoutCalendarExport() = runTest(main.dispatcher) {
        val calendar = FakeCalendarSync(CalendarSyncState(enabled = true, problem = CalendarSyncProblem.NO_PERMISSION))
        val fixture = createFixture(page = SettingsPage.SCHEDULE, calendarSync = calendar, capabilities = none)
        advanceUntilIdle()

        val ids = fixture.viewModel.allItems().map { it.id }
        assertEquals(listOf(SettingRowId.PAGE_NOTIFICATIONS, SettingRowId.SCHEDULE_SPORT_AUTO_SIGN), ids)
        assertEquals(2, fixture.viewModel.uiState.value.sections.size)
        assertTrue(fixture.viewModel.uiState.value.loaded)
    }
}
