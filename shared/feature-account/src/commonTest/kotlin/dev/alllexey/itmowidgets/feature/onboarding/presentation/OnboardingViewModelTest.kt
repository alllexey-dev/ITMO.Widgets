package dev.alllexey.itmowidgets.feature.onboarding.presentation

import androidx.lifecycle.SavedStateHandle
import dev.alllexey.itmowidgets.core.schedule.CalendarSyncResult
import dev.alllexey.itmowidgets.core.session.CurrentUser
import dev.alllexey.itmowidgets.core.session.SessionState
import dev.alllexey.itmowidgets.core.testing.FakeCalendarSync
import dev.alllexey.itmowidgets.core.testing.FakeCustomServicesRepository
import dev.alllexey.itmowidgets.core.testing.FakeOnboardingRepository
import dev.alllexey.itmowidgets.core.testing.FakeSessionRepository
import dev.alllexey.itmowidgets.core.platform.PlatformCapabilities
import dev.alllexey.itmowidgets.testkit.TestMainDispatcher
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest

/** The two steps of the first run: who signed in with the permissions one by one, the consent-gated connection. */
class OnboardingViewModelTest {

    private val main = TestMainDispatcher()

    @BeforeTest
    fun setUp() = main.install()

    @AfterTest
    fun tearDown() = main.reset()

    @Test
    fun theFlowWalksTwoStepsAndEndsWithNotNow() = runTest(main.dispatcher) {
        val fixture = Fixture()
        advanceUntilIdle()

        val walked = mutableListOf(fixture.viewModel.uiState.value.step)
        fixture.viewModel.next()
        walked += fixture.viewModel.uiState.value.step
        assertEquals(listOf(OnboardingStep.PERMISSIONS, OnboardingStep.CONNECTION), walked.toList())
        assertFalse(fixture.onboarding.observeCompleted().first())

        fixture.viewModel.back()
        assertEquals(OnboardingStep.PERMISSIONS, fixture.viewModel.uiState.value.step)
        fixture.viewModel.back()
        assertEquals(OnboardingStep.PERMISSIONS, fixture.viewModel.uiState.value.step, "the first step has nothing before it")
        fixture.viewModel.next()
        fixture.viewModel.notNow()
        advanceUntilIdle()

        assertTrue(fixture.onboarding.observeCompleted().first())
        assertTrue(fixture.viewModel.uiState.value.finished)
        assertEquals(emptyList(), fixture.services.requests, "not now leaves the connection off")
    }

    @Test
    fun theStepSurvivesProcessDeath() = runTest(main.dispatcher) {
        val saved = SavedStateHandle()
        Fixture(saved).viewModel.next()
        assertEquals(OnboardingStep.CONNECTION, Fixture(saved).viewModel.uiState.value.step)
    }

    @Test
    fun theFirstStepShowsTheFullNameAndTheIsu() = runTest(main.dispatcher) {
        val fixture = Fixture()
        advanceUntilIdle()

        assertEquals(OnboardingMe("Александрова Мария Сергеевна", "АМ", null, 412345), fixture.viewModel.uiState.value.me)
    }

    @Test
    fun withoutTheNamePartsTheCardTakesTheTokenName() = runTest(main.dispatcher) {
        val fixture = Fixture(user = CurrentUser(7, "Мария", null))
        advanceUntilIdle()

        assertEquals(OnboardingMe("Мария", "М", null, 7), fixture.viewModel.uiState.value.me)
        assertNull(Fixture(user = CurrentUser(7, null, null)).viewModel.uiState.value.me, "no name, no card")
    }

    @Test
    fun notificationsAskOnceThenOpenTheSystemPage() = runTest(main.dispatcher) {
        val fixture = Fixture()
        advanceUntilIdle()

        fixture.viewModel.requestNotifications()
        assertEquals(OnboardingEvent.RequestNotificationPermission, fixture.viewModel.events.first())
        fixture.viewModel.onNotificationPermission(granted = false)
        fixture.viewModel.requestNotifications()
        assertEquals(OnboardingEvent.OpenNotificationSettings, fixture.viewModel.events.first())
    }

    @Test
    fun theCalendarTurnsOnAfterItsPermissionAndOnlyWhereOffered() = runTest(main.dispatcher) {
        val fixture = Fixture()
        val without = Fixture(capabilities = Capabilities.copy(calendarExport = false))
        advanceUntilIdle()
        assertEquals(false, fixture.viewModel.uiState.value.calendarSynced)
        assertNull(without.viewModel.uiState.value.calendarSynced)

        fixture.viewModel.addLessonsToCalendar()
        assertEquals(OnboardingEvent.RequestCalendarAccess, fixture.viewModel.events.first())
        fixture.viewModel.onCalendarAccessGranted()
        advanceUntilIdle()

        assertEquals(1, fixture.calendar.enables)
        assertEquals(true, fixture.viewModel.uiState.value.calendarSynced)
    }

    @Test
    fun aFailedCalendarSaysSo() = runTest(main.dispatcher) {
        val fixture = Fixture()
        fixture.calendar.enableResult = CalendarSyncResult.FAILED
        advanceUntilIdle()

        fixture.viewModel.onCalendarAccessGranted()
        advanceUntilIdle()

        assertTrue(fixture.viewModel.events.first() is OnboardingEvent.ShowError)
    }

    @Test
    fun connectingGoesThroughTheConsentAndEndsTheFlow() = runTest(main.dispatcher) {
        val fixture = Fixture()
        advanceUntilIdle()

        fixture.viewModel.connect()
        assertEquals(OnboardingEvent.ShowConsent, fixture.viewModel.events.first())
        assertEquals(emptyList(), fixture.services.requests, "nothing turns on before the consent")

        fixture.viewModel.onConsent()
        advanceUntilIdle()

        assertEquals(listOf(true), fixture.services.requests)
        assertTrue(fixture.viewModel.uiState.value.finished)
        assertTrue(fixture.onboarding.observeCompleted().first())
    }

    @Test
    fun aFailedConnectionStaysOnTheStep() = runTest(main.dispatcher) {
        val fixture = Fixture()
        fixture.services.failing = true
        advanceUntilIdle()

        fixture.viewModel.onConsent()
        advanceUntilIdle()

        assertTrue(fixture.viewModel.events.first() is OnboardingEvent.ShowError)
        assertFalse(fixture.viewModel.uiState.value.finished)
        assertFalse(fixture.viewModel.uiState.value.servicesBusy)
    }

    @Test
    fun alreadyConnectedTheLastButtonFinishes() = runTest(main.dispatcher) {
        val fixture = Fixture(connected = true)
        advanceUntilIdle()

        fixture.viewModel.connect()
        advanceUntilIdle()

        assertTrue(fixture.viewModel.uiState.value.finished)
        assertEquals(emptyList(), fixture.services.requests)
    }

    private class Fixture(
        saved: SavedStateHandle = SavedStateHandle(),
        user: CurrentUser = Person,
        connected: Boolean = false,
        capabilities: PlatformCapabilities = Capabilities,
    ) {
        val onboarding = FakeOnboardingRepository(completed = false)
        val services = FakeCustomServicesRepository(enabled = connected)
        val session = FakeSessionRepository(SessionState.SignedIn(user))
        val calendar = FakeCalendarSync()
        val viewModel = OnboardingViewModel(onboarding, services, session, calendar, capabilities, saved)
    }

    private companion object {
        val Person = CurrentUser(
            412345,
            "Александрова Мария",
            null,
            familyName = "Александрова",
            givenName = "Мария",
            middleName = "Сергеевна",
        )

        val Capabilities = PlatformCapabilities(
            quickSettingsTile = true,
            backgroundWorkSettings = true,
            updateChannel = true,
            calendarExport = true,
            recordbook = true,
            marks = true,
            reviews = true,
            qrWidgetAnimation = true,
            qrCustomSpoiler = true,
            wallpaperColors = true,
            pinWidgets = true,
            lockScreenWidgets = true,
            pushSwitches = true,
        )
    }
}
