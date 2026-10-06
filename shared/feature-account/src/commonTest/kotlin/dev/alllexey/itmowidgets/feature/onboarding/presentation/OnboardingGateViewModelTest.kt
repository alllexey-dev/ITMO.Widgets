package dev.alllexey.itmowidgets.feature.onboarding.presentation

import dev.alllexey.itmowidgets.core.session.CurrentUser
import dev.alllexey.itmowidgets.core.session.SessionState
import dev.alllexey.itmowidgets.core.testing.FakeOnboardingRepository
import dev.alllexey.itmowidgets.core.testing.FakeSessionRepository
import dev.alllexey.itmowidgets.testkit.TestMainDispatcher
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest

class OnboardingGateViewModelTest {

    private val main = TestMainDispatcher()

    @BeforeTest
    fun setUp() = main.install()

    @AfterTest
    fun tearDown() = main.reset()

    @Test
    fun theGateStaysUnknownUntilTheStoredFlagIsRead() = runTest(main.dispatcher) {
        val fixture = createFixture(session = SessionState.SignedIn(user()))
        advanceUntilIdle()

        assertEquals(OnboardingGate.Unknown, fixture.viewModel.uiState.value)
    }

    @Test
    fun aFirstRunOfASignedInSessionNeedsTheFlow() = runTest(main.dispatcher) {
        val fixture = createFixture(session = SessionState.SignedIn(user()), completed = false)
        advanceUntilIdle()

        assertEquals(OnboardingGate.Required, fixture.viewModel.uiState.value)
    }

    @Test
    fun aDeviceThatAlreadyPassedTheFlowGoesStraightHome() = runTest(main.dispatcher) {
        val fixture = createFixture(session = SessionState.SignedIn(user()), completed = true)
        advanceUntilIdle()

        assertEquals(OnboardingGate.Passed, fixture.viewModel.uiState.value)
    }

    @Test
    fun completingTheFlowFlipsTheGate() = runTest(main.dispatcher) {
        val fixture = createFixture(session = SessionState.SignedIn(user()), completed = false)
        advanceUntilIdle()

        fixture.onboarding.complete()
        advanceUntilIdle()

        assertEquals(OnboardingGate.Passed, fixture.viewModel.uiState.value)
    }

    @Test
    fun aReplayAsksForTheFlowAgain() = runTest(main.dispatcher) {
        val fixture = createFixture(session = SessionState.SignedIn(user()), completed = true)
        advanceUntilIdle()

        fixture.onboarding.reset()
        advanceUntilIdle()

        assertEquals(OnboardingGate.Required, fixture.viewModel.uiState.value)
    }

    @Test
    fun theFlagSurvivesASignOutAndDoesNotRepeatTheFlowForTheNextAccount() =
        runTest(main.dispatcher) {
            val fixture = createFixture(session = SessionState.SignedIn(user()), completed = true)
            advanceUntilIdle()

            fixture.session.mutableState.value = SessionState.SignedOut
            advanceUntilIdle()
            assertEquals(OnboardingGate.Unknown, fixture.viewModel.uiState.value)

            fixture.session.mutableState.value = SessionState.SignedIn(user(isu = 654321))
            advanceUntilIdle()
            assertEquals(OnboardingGate.Passed, fixture.viewModel.uiState.value)
        }

    private fun createFixture(
        session: SessionState,
        completed: Boolean? = null
    ): Fixture {
        val sessionRepository = FakeSessionRepository(session)
        val onboarding = FakeOnboardingRepository(completed)
        return Fixture(
            session = sessionRepository,
            onboarding = onboarding,
            viewModel = OnboardingGateViewModel(sessionRepository, onboarding)
        )
    }

    private fun user(isu: Int = 123456) = CurrentUser(isu = isu, name = "Иванов Иван", pictureUrl = null)

    private data class Fixture(
        val session: FakeSessionRepository,
        val onboarding: FakeOnboardingRepository,
        val viewModel: OnboardingGateViewModel
    )
}
