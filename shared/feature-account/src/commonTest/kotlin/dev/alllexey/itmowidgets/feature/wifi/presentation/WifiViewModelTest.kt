package dev.alllexey.itmowidgets.feature.wifi.presentation

import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.feature.wifi.domain.WifiAccess
import dev.alllexey.itmowidgets.feature.wifi.domain.WifiCredentials
import dev.alllexey.itmowidgets.feature.wifi.domain.WifiRepository
import dev.alllexey.itmowidgets.testkit.TestMainDispatcher
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

class WifiViewModelTest {

    private val main = TestMainDispatcher()
    private val repository = FakeWifiRepository()

    @BeforeTest
    fun setUp() = main.install()

    @AfterTest
    fun tearDown() = main.reset()

    @Test
    fun showsTheStoredCredentialsWhileIsuIsReadAgain() = runTest(main.dispatcher) {
        repository.cached = OLD
        val viewModel = WifiViewModel(repository)

        viewModel.start()
        runCurrent()
        assertEquals(WifiUiState(OLD, status = WifiStatus.Ready(refreshing = true)), viewModel.uiState.value)

        repository.answer.complete(AppResult.Success(WifiAccess.Ready(NEW)))
        advanceUntilIdle()
        assertEquals(WifiUiState(NEW, status = WifiStatus.Ready()), viewModel.uiState.value)
    }

    @Test
    fun aFailedReadKeepsTheStoredCredentials() = runTest(main.dispatcher) {
        repository.cached = OLD
        repository.answer.complete(AppResult.Failure(AppError.Network))
        val viewModel = WifiViewModel(repository)

        viewModel.start()
        advanceUntilIdle()

        assertEquals(WifiUiState(OLD, status = WifiStatus.Ready()), viewModel.uiState.value)
    }

    @Test
    fun withoutStoredCredentialsTheSheetShowsWhatIsuSaid() = runTest(main.dispatcher) {
        val cases = listOf(
            AppResult.Success(WifiAccess.NoPassword) to WifiStatus.NoPassword,
            AppResult.Success(WifiAccess.SignInRequired) to WifiStatus.SignInRequired,
            AppResult.Failure(AppError.Network) to WifiStatus.Failed(AppError.Network),
        )
        cases.forEach { (answer, status) ->
            val repository = FakeWifiRepository().apply { this.answer.complete(answer) }
            val viewModel = WifiViewModel(repository)

            viewModel.start()
            advanceUntilIdle()

            assertEquals(WifiUiState(status = status), viewModel.uiState.value)
        }
    }

    @Test
    fun aRemovedPasswordLeavesTheScreen() = runTest(main.dispatcher) {
        repository.cached = OLD
        repository.answer.complete(AppResult.Success(WifiAccess.NoPassword))
        val viewModel = WifiViewModel(repository)

        viewModel.start()
        advanceUntilIdle()

        assertEquals(WifiUiState(status = WifiStatus.NoPassword), viewModel.uiState.value)
    }

    @Test
    fun thePasswordIsHiddenUntilShownAndAStartInProgressIsJoined() = runTest(main.dispatcher) {
        val viewModel = WifiViewModel(repository)
        assertFalse(viewModel.uiState.value.passwordShown)

        viewModel.togglePassword()
        assertTrue(viewModel.uiState.value.passwordShown)

        viewModel.start()
        viewModel.start()
        runCurrent()
        assertEquals(1, repository.refreshes)
    }

    private class FakeWifiRepository : WifiRepository {
        var cached: WifiCredentials? = null
        val answer = CompletableDeferred<AppResult<WifiAccess>>()
        var refreshes = 0

        override suspend fun cached(): WifiCredentials? = cached

        override suspend fun refresh(): AppResult<WifiAccess> {
            refreshes++
            return answer.await()
        }
    }

    private companion object {
        val OLD = WifiCredentials("123456", "11111111")
        val NEW = WifiCredentials("123456", "22222222")
    }
}
