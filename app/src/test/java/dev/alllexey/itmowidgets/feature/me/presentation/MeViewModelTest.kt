package dev.alllexey.itmowidgets.feature.me.presentation

import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.session.CurrentUser
import dev.alllexey.itmowidgets.core.session.SessionRepository
import dev.alllexey.itmowidgets.core.session.SessionState
import dev.alllexey.itmowidgets.core.testing.MainDispatcherRule
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MeViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `renders user from the shared session`() = runTest(mainDispatcherRule.dispatcher) {
        val user = CurrentUser(123456, "Иванов Иван", null)
        val repository = FakeSessionRepository(SessionState.SignedIn(user))

        val viewModel = MeViewModel(repository)
        advanceUntilIdle()

        assertEquals(user, viewModel.uiState.value.user)
    }

    @Test
    fun `delegates sign out once`() = runTest(mainDispatcherRule.dispatcher) {
        val repository = FakeSessionRepository(SessionState.SignedIn(null))
        val viewModel = MeViewModel(repository)

        viewModel.signOut()
        viewModel.signOut()
        advanceUntilIdle()

        assertEquals(1, repository.signOutRequests)
        assertEquals(false, viewModel.uiState.value.signOutInProgress)
    }

    private class FakeSessionRepository(
        initialState: SessionState
    ) : SessionRepository {
        private val mutableState = MutableStateFlow(initialState)
        override val state: StateFlow<SessionState> = mutableState
        var signOutRequests = 0

        override suspend fun initialize() = Unit

        override suspend fun completeItmoIdLogin(tokenResponseJson: String): AppResult<Unit> =
            AppResult.Success(Unit)

        override suspend fun signInWithRefreshToken(refreshToken: String): AppResult<Unit> =
            AppResult.Success(Unit)

        override suspend fun signOut() {
            signOutRequests += 1
            mutableState.value = SessionState.SignedOut
        }
    }
}
