package dev.alllexey.itmowidgets.feature.settings.presentation.notifications

import dev.alllexey.itmowidgets.core.platform.PlatformCapabilities
import dev.alllexey.itmowidgets.core.testing.FakeMarkTracking
import dev.alllexey.itmowidgets.core.testing.FakeScheduleChangeTracking
import dev.alllexey.itmowidgets.feature.settings.domain.LocalSettings
import dev.alllexey.itmowidgets.feature.settings.domain.SharingSettingsState
import dev.alllexey.itmowidgets.feature.settings.presentation.EveryPlatformCapability
import dev.alllexey.itmowidgets.feature.settings.presentation.FakeBackgroundWorkAccess
import dev.alllexey.itmowidgets.feature.settings.presentation.FakeSettingsRepository
import dev.alllexey.itmowidgets.testkit.TestMainDispatcher
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

/** «Уведомления»: the local checks, the permission and background hint around them, the pushes, the subtitle. */
@OptIn(ExperimentalCoroutinesApi::class)
class NotificationSettingsViewModelTest {

    private val main = TestMainDispatcher()

    @BeforeTest
    fun setUp() = main.install()

    @AfterTest
    fun tearDown() = main.reset()

    private class Fixture(local: LocalSettings, unrestricted: Boolean, capabilities: PlatformCapabilities) {
        val tracking = FakeScheduleChangeTracking(enabled = local.scheduleChangesEnabled)
        val marks = FakeMarkTracking()
        val settings = FakeSettingsRepository(local, SharingSettingsState.Disabled, true, tracking.enabled)
        val background = FakeBackgroundWorkAccess(unrestricted)
        val viewModel = NotificationSettingsViewModel(settings, tracking, marks, background, capabilities)
    }

    private fun fixture(
        local: LocalSettings = LocalSettings(customServicesEnabled = true),
        unrestricted: Boolean = true,
        capabilities: PlatformCapabilities = EveryPlatformCapability,
    ) = Fixture(local, unrestricted, capabilities)

    private fun TestScope.events(fixture: Fixture): List<NotificationSettingsEvent> {
        val events = mutableListOf<NotificationSettingsEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { fixture.viewModel.events.toList(events) }
        return events
    }

    @Test
    fun thePageListsEveryTypeWithTheStoredSwitches() = runTest(main.dispatcher) {
        val fixture = fixture(LocalSettings(customServicesEnabled = true, barsMarksEnabled = false, sheetMarksEnabled = false))
        fixture.viewModel.onPermissionChanged(granted = true)
        advanceUntilIdle()

        val state = fixture.viewModel.uiState.value
        assertTrue(state.loaded)
        assertEquals(NotificationPermission.ALLOWED, state.permission)
        assertEquals(
            listOf(
                MarkCheckSwitch(MarkCheckSource.MY_ITMO, true),
                MarkCheckSwitch(MarkCheckSource.BARS, false),
                MarkCheckSwitch(MarkCheckSource.SHEETS, false),
            ),
            state.marks?.sources,
        )
        assertTrue(state.scheduleChanges)
        assertEquals(PushKind.entries, state.pushes.map { it.kind })
        assertTrue(state.connected)
        assertTrue(state.pushes.all { it.enabled })
        assertFalse(state.systemChannelsOff)
        assertFalse(state.backgroundWorkRestricted)
    }

    @Test
    fun barsIsListedOnlyOnceItHasAnsweredAndMarksOnlyWithTheCapability() = runTest(main.dispatcher) {
        val before = fixture()
        val ios = fixture(capabilities = EveryPlatformCapability.copy(marks = false))
        advanceUntilIdle()

        assertEquals(listOf(MarkCheckSource.MY_ITMO, MarkCheckSource.SHEETS), before.viewModel.uiState.value.marks?.sources?.map { it.source })
        assertNull(ios.viewModel.uiState.value.marks)
    }

    @Test
    fun marksAsAWholeSwitchEverySourceBarsOnlyOnceAnswered() = runTest(main.dispatcher) {
        val unanswered = fixture()
        val answered = fixture(LocalSettings(barsMarksEnabled = true))
        advanceUntilIdle()

        unanswered.viewModel.setMarks(false)
        answered.viewModel.setMarks(false)
        advanceUntilIdle()

        assertEquals(listOf(false), unanswered.marks.myItmoCalls)
        assertEquals(emptyList(), unanswered.marks.barsCalls)
        assertEquals(listOf(false), unanswered.marks.sheetsCalls)
        assertEquals(listOf(false), answered.marks.barsCalls)
    }

    @Test
    fun turningACheckOnWithoutThePermissionAsksAndOffersTheHintOnceWhileRestricted() = runTest(main.dispatcher) {
        val fixture = fixture(unrestricted = false)
        val events = events(fixture)
        fixture.viewModel.onPermissionChanged(granted = false)
        fixture.viewModel.onBackgroundWorkChanged()
        advanceUntilIdle()

        fixture.viewModel.setScheduleChanges(false)
        advanceUntilIdle()
        assertEquals(emptyList(), events)

        fixture.viewModel.setScheduleChanges(true)
        advanceUntilIdle()
        assertEquals(
            listOf(NotificationSettingsEvent.RequestPermission, NotificationSettingsEvent.ShowBackgroundWorkHint),
            events,
        )
        assertEquals(listOf(false, true), fixture.tracking.setCalls)

        fixture.viewModel.setMarkSource(MarkCheckSource.MY_ITMO, true)
        advanceUntilIdle()
        assertEquals(1, fixture.settings.hintShownCalls, "the hint is once per device")
        assertEquals(listOf(true), fixture.marks.myItmoCalls)
    }

    @Test
    fun anAllowedUnrestrictedAppAsksNothing() = runTest(main.dispatcher) {
        val fixture = fixture()
        val events = events(fixture)
        fixture.viewModel.onPermissionChanged(granted = true)
        fixture.viewModel.onBackgroundWorkChanged()
        advanceUntilIdle()

        fixture.viewModel.setScheduleChanges(true)
        advanceUntilIdle()

        assertEquals(emptyList(), events)
        assertEquals(0, fixture.settings.hintShownCalls)
    }

    @Test
    fun theBackgroundRowLeavesWhenTheSystemSavesTheChoiceAfterTheScreenReturns() = runTest(main.dispatcher) {
        val fixture = fixture(unrestricted = false)
        advanceUntilIdle()
        assertFalse(fixture.viewModel.uiState.value.backgroundWorkRestricted, "not asked yet: the row stays out")

        fixture.viewModel.onBackgroundWorkChanged()
        runCurrent()
        assertTrue(fixture.viewModel.uiState.value.backgroundWorkRestricted)

        fixture.background.unrestricted = true
        advanceTimeBy(999)
        runCurrent()
        assertTrue(fixture.viewModel.uiState.value.backgroundWorkRestricted)
        advanceTimeBy(1)
        runCurrent()
        assertFalse(fixture.viewModel.uiState.value.backgroundWorkRestricted)
    }

    @Test
    fun aPushSwitchIsStoredAndAsksForThePermissionWithoutTheBackgroundHint() = runTest(main.dispatcher) {
        val fixture = fixture(unrestricted = false)
        val events = events(fixture)
        fixture.viewModel.onPermissionChanged(granted = false)
        fixture.viewModel.onBackgroundWorkChanged()
        advanceUntilIdle()

        fixture.viewModel.setPush(PushKind.FRIENDS, false)
        advanceUntilIdle()
        assertEquals(setOf("friends"), fixture.settings.local.value.disabledPushes)
        assertEquals(
            listOf(PushNotifications(PushKind.SPORT, true), PushNotifications(PushKind.FRIENDS, false), PushNotifications(PushKind.DEVICES, true)),
            fixture.viewModel.uiState.value.pushes,
        )
        assertEquals(emptyList(), events)

        fixture.viewModel.setPush(PushKind.FRIENDS, true)
        advanceUntilIdle()
        assertEquals(emptySet(), fixture.settings.local.value.disabledPushes)
        assertEquals(listOf(NotificationSettingsEvent.RequestPermission), events)
        assertEquals(0, fixture.settings.hintShownCalls)
    }

    @Test
    fun aChannelOffInTheSystemCountsOnlyForATypeThatIsOnAndWhileAllowed() = runTest(main.dispatcher) {
        val fixture = fixture(LocalSettings(customServicesEnabled = true, disabledPushes = setOf("sport")))
        fixture.viewModel.onPermissionChanged(granted = true)
        val on = mapOf("sport" to true, "friends" to true, "connections" to true, "marks" to true, "schedule_changes" to true)

        fixture.viewModel.onChannelsChanged(on + ("sport" to false))
        advanceUntilIdle()
        assertFalse(fixture.viewModel.uiState.value.systemChannelsOff, "sport is off in the app anyway")

        fixture.viewModel.onChannelsChanged(on + ("schedule_changes" to false))
        advanceUntilIdle()
        assertTrue(fixture.viewModel.uiState.value.systemChannelsOff)

        fixture.viewModel.onPermissionChanged(granted = false)
        advanceUntilIdle()
        assertFalse(fixture.viewModel.uiState.value.systemChannelsOff, "the permission gate stands alone")
    }

    @Test
    fun pushesWaitForTheConnection() = runTest(main.dispatcher) {
        val fixture = fixture(LocalSettings(customServicesEnabled = false))
        val events = events(fixture)
        fixture.viewModel.onChannelsChanged(mapOf("friends" to false))
        advanceUntilIdle()
        assertFalse(fixture.viewModel.uiState.value.connected)
        assertFalse(fixture.viewModel.uiState.value.systemChannelsOff, "nothing arrives without the connection")

        fixture.viewModel.openConnection()
        advanceUntilIdle()
        assertEquals(listOf(NotificationSettingsEvent.OpenConnection), events)
    }

    @Test
    fun theSubtitleSaysDeniedOrCountsTheSwitchesThatAreOn() = runTest(main.dispatcher) {
        val fixture = fixture(LocalSettings(myItmoMarksEnabled = false, sheetMarksEnabled = false))
        advanceUntilIdle()
        assertEquals(
            dev.alllexey.itmowidgets.core.text.UiText.Res(
                dev.alllexey.itmowidgets.shared.feature.settings.SettingsStrings.notifications_subtitle_count,
                listOf(1, 2),
            ),
            fixture.viewModel.uiState.value.subtitle,
        )
        val connected = fixture(
            LocalSettings(customServicesEnabled = true, myItmoMarksEnabled = false, sheetMarksEnabled = false, disabledPushes = setOf("friends")),
        )
        advanceUntilIdle()
        assertEquals(
            dev.alllexey.itmowidgets.core.text.UiText.Res(
                dev.alllexey.itmowidgets.shared.feature.settings.SettingsStrings.notifications_subtitle_count,
                listOf(3, 5),
            ),
            connected.viewModel.uiState.value.subtitle,
        )

        fixture.viewModel.onPermissionChanged(granted = false)
        advanceUntilIdle()
        assertEquals(
            dev.alllexey.itmowidgets.core.text.UiText.Res(
                dev.alllexey.itmowidgets.shared.feature.settings.SettingsStrings.notifications_subtitle_denied,
            ),
            fixture.viewModel.uiState.value.subtitle,
        )
    }
}
