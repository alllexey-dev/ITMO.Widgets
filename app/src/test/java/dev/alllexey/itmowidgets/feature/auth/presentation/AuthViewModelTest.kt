package dev.alllexey.itmowidgets.feature.auth.presentation

import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.session.SessionRepository
import dev.alllexey.itmowidgets.core.session.SessionState
import dev.alllexey.itmowidgets.core.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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

    private class FakeSessionRepository(
        initialState: SessionState
    ) : SessionRepository {
        override val state: StateFlow<SessionState> = MutableStateFlow(initialState)

        override suspend fun initialize() = Unit

        override suspend fun completeItmoIdLogin(tokenResponseJson: String): AppResult<Unit> =
            AppResult.Success(Unit)

        override suspend fun signInWithRefreshToken(refreshToken: String): AppResult<Unit> =
            AppResult.Success(Unit)

        var demoStarts = 0

        override suspend fun startDemo() {
            demoStarts += 1
        }

        override suspend fun signOut() = Unit
    }
}
