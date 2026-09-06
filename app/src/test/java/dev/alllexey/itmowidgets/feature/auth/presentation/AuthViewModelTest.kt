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

    private class FakeSessionRepository(
        initialState: SessionState
    ) : SessionRepository {
        override val state: StateFlow<SessionState> = MutableStateFlow(initialState)

        override suspend fun initialize() = Unit

        override suspend fun completeItmoIdLogin(tokenResponseJson: String): AppResult<Unit> =
            AppResult.Success(Unit)

        override suspend fun signInWithRefreshToken(refreshToken: String): AppResult<Unit> =
            AppResult.Success(Unit)

        override suspend fun signOut() = Unit
    }
}
