package dev.alllexey.itmowidgets.feature.auth.presentation

import dev.alllexey.itmowidgets.core.session.SessionState
import dev.alllexey.itmowidgets.core.testing.FakeSessionRepository
import dev.alllexey.itmowidgets.core.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.flow.first
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `shows auth content immediately while sign out cleanup is running`() =
        runTest(mainDispatcherRule.dispatcher) {
            val viewModel = AuthViewModel(
                FakeSessionRepository(SessionState.SigningOut)
            )

            assertFalse(viewModel.uiState.value.initializing)
            assertTrue(viewModel.uiState.value.sessionTransitionInProgress)

            advanceUntilIdle()

            assertFalse(viewModel.uiState.value.initializing)
            assertTrue(viewModel.uiState.value.sessionTransitionInProgress)
        }

    @Test
    fun `follows the session it observes`() = runTest(mainDispatcherRule.dispatcher) {
        val repository = FakeSessionRepository(SessionState.Initializing)
        val viewModel = AuthViewModel(repository)
        assertTrue(viewModel.uiState.value.initializing)

        repository.mutableState.value = SessionState.ReauthenticationRequired
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.initializing)
        assertTrue(viewModel.uiState.value.reauthenticationRequired)
        assertFalse(viewModel.uiState.value.sessionTransitionInProgress)
    }

    @Test
    fun `logo taps during a sign out never start the demo`() = runTest(mainDispatcherRule.dispatcher) {
        val repository = FakeSessionRepository(SessionState.SigningOut)
        val viewModel = AuthViewModel(repository)

        repeat(5) { viewModel.onLogoTap(atMillis = 1_000L + it * 300L) }
        advanceUntilIdle()

        assertEquals(0, repository.demoStarts)
    }

    @Test
    fun `the fifth quick tap on the logo starts the demo once`() =
        runTest(mainDispatcherRule.dispatcher) {
            val repository = FakeSessionRepository(SessionState.SignedOut)
            val viewModel = AuthViewModel(repository)
            repeat(4) { viewModel.onLogoTap(atMillis = 1_000L + it * 300L) }
            advanceUntilIdle()
            assertEquals(0, repository.demoStarts)

            viewModel.onLogoTap(atMillis = 2_200L)
            advanceUntilIdle()

            assertEquals(1, repository.demoStarts)
            assertEquals(AuthEvent.DemoStarted, viewModel.events.first())
        }
}
