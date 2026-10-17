package dev.alllexey.itmowidgets.feature.onboarding.presentation

import androidx.lifecycle.SavedStateHandle
import dev.alllexey.itmowidgets.core.lesson.DayLesson
import dev.alllexey.itmowidgets.core.lesson.LessonDay
import dev.alllexey.itmowidgets.core.lesson.LessonState
import dev.alllexey.itmowidgets.core.lesson.LessonTarget
import dev.alllexey.itmowidgets.core.navigation.LessonDetailsArgs
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.schedule.CalendarSyncResult
import dev.alllexey.itmowidgets.core.session.CurrentUser
import dev.alllexey.itmowidgets.core.session.SessionState
import dev.alllexey.itmowidgets.core.sport.SportScorePeriod
import dev.alllexey.itmowidgets.core.sport.SportScoreSummary
import dev.alllexey.itmowidgets.core.testing.FakeCalendarSync
import dev.alllexey.itmowidgets.core.testing.FakeCustomServicesRepository
import dev.alllexey.itmowidgets.core.testing.FakeDayLessonsGateway
import dev.alllexey.itmowidgets.core.testing.FakeOnboardingRepository
import dev.alllexey.itmowidgets.core.testing.FakeSessionRepository
import dev.alllexey.itmowidgets.core.testing.FakeSportScoreRepository
import dev.alllexey.itmowidgets.core.testing.FixedAcademicTime
import dev.alllexey.itmowidgets.core.platform.PlatformCapabilities
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.shared.feature.account.AccountStrings
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
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime

/** The three steps of the first run: the user's own data, the permissions one by one, the consent-gated connection. */
class OnboardingViewModelTest {

    private val main = TestMainDispatcher()

    @BeforeTest
    fun setUp() = main.install()

    @AfterTest
    fun tearDown() = main.reset()

    @Test
    fun theFlowWalksThreeStepsAndEndsWithNotNow() = runTest(main.dispatcher) {
        val fixture = Fixture()
        advanceUntilIdle()

        val walked = mutableListOf<OnboardingStep>(fixture.viewModel.uiState.value.step)
        repeat(2) { fixture.viewModel.next(); walked += fixture.viewModel.uiState.value.step }
        assertEquals(OnboardingStep.entries.toList(), walked.toList())
        assertFalse(fixture.onboarding.observeCompleted().first())

        fixture.viewModel.back()
        assertEquals(OnboardingStep.PERMISSIONS, fixture.viewModel.uiState.value.step)
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
        assertEquals(OnboardingStep.PERMISSIONS, Fixture(saved).viewModel.uiState.value.step)
    }

    @Test
    fun theFirstStepShowsThePersonTheLessonNowAndTheSportScore() = runTest(main.dispatcher) {
        val fixture = Fixture()
        advanceUntilIdle()

        val state = fixture.viewModel.uiState.value
        assertEquals(OnboardingMe("Александрова Мария", "АМ", null), state.me)
        assertEquals(UiText.Res(AccountStrings.onboarding_value_now), state.lesson?.title)
        assertEquals("Программирование", state.lesson?.lesson?.subject)
        assertEquals(LessonState.NOW, state.lesson?.lesson?.state)
        assertEquals(UiText.Res(AccountStrings.onboarding_value_sport_score, listOf(90)), state.sport)
        assertEquals(listOf(10L), fixture.sport.scoreRequests, "the current period")
        assertEquals(1, fixture.days.refreshes)
    }

    @Test
    fun afterTodaysLessonsTheFirstStepShowsTomorrowsFirst() = runTest(main.dispatcher) {
        val fixture = Fixture(time = FixedAcademicTime(LocalDateTime(Today, LocalTime(19, 0))))
        advanceUntilIdle()

        assertEquals(UiText.Res(AccountStrings.onboarding_value_tomorrow), fixture.viewModel.uiState.value.lesson?.title)
        assertEquals("Физика", fixture.viewModel.uiState.value.lesson?.lesson?.subject)
    }

    @Test
    fun withoutDataTheFirstStepKeepsItsDescriptions() = runTest(main.dispatcher) {
        val fixture = Fixture(days = emptyList())
        fixture.sport.periods = AppResult.Failure(dev.alllexey.itmowidgets.core.result.AppError.Network)
        advanceUntilIdle()

        assertNull(fixture.viewModel.uiState.value.lesson)
        assertNull(fixture.viewModel.uiState.value.sport)
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
        time: FixedAcademicTime = FixedAcademicTime(LocalDateTime(Today, LocalTime(12, 0))),
        days: List<LessonDay> = Week,
        connected: Boolean = false,
        capabilities: PlatformCapabilities = Capabilities,
    ) {
        val onboarding = FakeOnboardingRepository(completed = false)
        val services = FakeCustomServicesRepository(enabled = connected)
        val session = FakeSessionRepository(
            SessionState.SignedIn(CurrentUser(1, "Александрова Мария", null, familyName = "Александрова", givenName = "Мария")),
        )
        val days = FakeDayLessonsGateway(days)
        val sport = FakeSportScoreRepository().apply {
            periods = AppResult.Success(listOf(SportScorePeriod(9, "Осень"), SportScorePeriod(10, "Весна", current = true)))
            score = AppResult.Success(SportScoreSummary(64, 26))
        }
        val calendar = FakeCalendarSync()
        val viewModel = OnboardingViewModel(
            onboarding, services, session, this.days, sport, calendar, time, capabilities, saved,
        )
    }

    private companion object {
        val Today = LocalDate(2026, 9, 7)

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
        )

        fun lesson(subject: String, start: LocalTime, end: LocalTime) = DayLesson(
            start = start,
            end = end,
            subject = subject,
            typeId = 2,
            teacher = null,
            room = "1506",
            building = "Кронверкский пр., 49",
            online = false,
            target = LessonTarget.Lesson(
                LessonDetailsArgs(1, Today.toString(), subject, 2, "", "$start", "$end", null, null, "1506", null, null, null, null, null, null, null),
            ),
        )

        val Week = listOf(
            LessonDay(Today, listOf(lesson("Программирование", LocalTime(11, 40), LocalTime(13, 10))), null),
            LessonDay(LocalDate(2026, 9, 8), listOf(lesson("Физика", LocalTime(10, 0), LocalTime(11, 30))), null),
        )
    }
}
