package dev.alllexey.itmowidgets.feature.onboarding.presentation

import dev.alllexey.itmowidgets.core.session.CurrentUser
import dev.alllexey.itmowidgets.core.session.SessionState
import dev.alllexey.itmowidgets.core.testing.FakeOnboardingRepository
import dev.alllexey.itmowidgets.core.testing.FakeSessionRepository
import dev.alllexey.itmowidgets.core.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingGateViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `the gate stays unknown until the stored flag is read`() = runTest(mainDispatcherRule.dispatcher) {
        val fixture = createFixture(session = SessionState.SignedIn(user()))
        advanceUntilIdle()

        assertEquals(OnboardingGate.Unknown, fixture.viewModel.state.value)
    }

    @Test
    fun `a first run of a signed-in session needs the flow`() = runTest(mainDispatcherRule.dispatcher) {
        val fixture = createFixture(session = SessionState.SignedIn(user()), completed = false)
        advanceUntilIdle()

        assertEquals(OnboardingGate.Required, fixture.viewModel.state.value)
    }

    @Test
    fun `a device that already passed the flow goes straight home`() = runTest(mainDispatcherRule.dispatcher) {
        val fixture = createFixture(session = SessionState.SignedIn(user()), completed = true)
        advanceUntilIdle()

        assertEquals(OnboardingGate.Passed, fixture.viewModel.state.value)
    }

    @Test
    fun `completing the flow flips the gate`() = runTest(mainDispatcherRule.dispatcher) {
        val fixture = createFixture(session = SessionState.SignedIn(user()), completed = false)
        advanceUntilIdle()

        fixture.onboarding.complete()
        advanceUntilIdle()

        assertEquals(OnboardingGate.Passed, fixture.viewModel.state.value)
    }

    @Test
    fun `a replay asks for the flow again`() = runTest(mainDispatcherRule.dispatcher) {
        val fixture = createFixture(session = SessionState.SignedIn(user()), completed = true)
        advanceUntilIdle()

        fixture.onboarding.reset()
        advanceUntilIdle()

        assertEquals(OnboardingGate.Required, fixture.viewModel.state.value)
    }

    @Test
    fun `the flag survives a sign-out and does not repeat the flow for the next account`() =
        runTest(mainDispatcherRule.dispatcher) {
            val fixture = createFixture(session = SessionState.SignedIn(user()), completed = true)
            advanceUntilIdle()

            fixture.session.mutableState.value = SessionState.SignedOut
            advanceUntilIdle()
            assertEquals(OnboardingGate.Unknown, fixture.viewModel.state.value)

            fixture.session.mutableState.value = SessionState.SignedIn(user(isu = 654321))
            advanceUntilIdle()
            assertEquals(OnboardingGate.Passed, fixture.viewModel.state.value)
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
