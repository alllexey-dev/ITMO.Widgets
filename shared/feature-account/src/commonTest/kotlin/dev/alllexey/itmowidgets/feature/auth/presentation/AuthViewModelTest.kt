package dev.alllexey.itmowidgets.feature.auth.presentation

import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.session.SessionRepository
import dev.alllexey.itmowidgets.core.session.SessionState
import dev.alllexey.itmowidgets.core.testing.FakeSessionRepository
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.shared.feature.account.Res
import dev.alllexey.itmowidgets.shared.feature.account.auth_error_invalid_credentials
import dev.alllexey.itmowidgets.shared.feature.account.auth_error_network
import dev.alllexey.itmowidgets.shared.feature.account.auth_error_unknown
import dev.alllexey.itmowidgets.testkit.TestMainDispatcher
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

class AuthViewModelTest {

    private val main = TestMainDispatcher()

    @BeforeTest
    fun setUp() = main.install()

    @AfterTest
    fun tearDown() = main.reset()

    @Test
    fun showsAuthContentImmediatelyWhileSignOutCleanupIsRunning() =
        runTest(main.dispatcher) {
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
    fun followsTheSessionItObserves() = runTest(main.dispatcher) {
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
    fun logoTapsDuringASignOutNeverStartTheDemo() = runTest(main.dispatcher) {
        val repository = FakeSessionRepository(SessionState.SigningOut)
        val viewModel = AuthViewModel(repository)

        repeat(5) { viewModel.onLogoTap(atMillis = 1_000L + it * 300L) }
        advanceUntilIdle()

        assertEquals(0, repository.demoStarts)
    }

    @Test
    fun theFifthQuickTapOnTheLogoStartsTheDemoOnce() =
        runTest(main.dispatcher) {
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

    @Test
    fun aDemoStartedWhileNobodyCollectsReachesTheNextCollector() = runTest(main.dispatcher) {
        val viewModel = AuthViewModel(FakeSessionRepository(SessionState.SignedOut))
        val first = backgroundScope.launch { viewModel.events.collect {} }
        runCurrent()
        first.cancel()

        repeat(5) { viewModel.onLogoTap(atMillis = 1_000L + it * 300L) }
        advanceUntilIdle()

        assertEquals(AuthEvent.DemoStarted, viewModel.events.first())
    }

    @Test
    fun eachFailureOfTheRefreshTokenSignInHasItsOwnText() = runTest(main.dispatcher) {
        val failures = listOf(AppError.Network, AppError.Unauthorized, AppError.Unknown(IllegalStateException()))
        val texts = failures.map { error ->
            val viewModel = AuthViewModel(FailingSession(error))
            viewModel.signInWithRefreshToken("synthetic-refresh-token")
            advanceUntilIdle()
            assertFalse(viewModel.uiState.value.manualLoginInProgress)
            viewModel.uiState.value.error
        }

        assertEquals(
            listOf(
                UiText.Res(Res.string.auth_error_network),
                UiText.Res(Res.string.auth_error_invalid_credentials),
                UiText.Res(Res.string.auth_error_unknown),
            ),
            texts,
        )
    }

    /** A signed-out session whose refresh-token sign-in fails with [error]. */
    private class FailingSession(private val error: AppError) : SessionRepository by FakeSessionRepository(
        SessionState.SignedOut
    ) {
        override suspend fun signInWithRefreshToken(refreshToken: String): AppResult<Unit> = AppResult.Failure(error)
    }
}
