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
import kotlin.test.assertNull
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
    fun showsLoadingUntilIsuAnswersThenTheCredentials() = runTest(main.dispatcher) {
        val viewModel = WifiViewModel(repository)

        viewModel.start()
        runCurrent()
        assertEquals(WifiUiState(status = WifiStatus.Loading), viewModel.uiState.value)

        repository.answer(AppResult.Success(WifiAccess.Ready(NEW)))
        advanceUntilIdle()
        assertEquals(WifiUiState(status = WifiStatus.Ready(NEW)), viewModel.uiState.value)
        assertEquals(NEW, viewModel.uiState.value.credentials)
    }

    @Test
    fun everyStartReadsIsuAgainAndHidesTheEarlierAnswerMeanwhile() = runTest(main.dispatcher) {
        val viewModel = WifiViewModel(repository)
        viewModel.start()
        repository.answer(AppResult.Success(WifiAccess.Ready(OLD)))
        advanceUntilIdle()

        viewModel.start()
        runCurrent()

        assertEquals(2, repository.loads)
        assertEquals(WifiUiState(status = WifiStatus.Loading), viewModel.uiState.value)
        assertNull(viewModel.uiState.value.credentials)
    }

    @Test
    fun aFailedReadShowsTheErrorAndNoEarlierPassword() = runTest(main.dispatcher) {
        val viewModel = WifiViewModel(repository)
        viewModel.start()
        repository.answer(AppResult.Success(WifiAccess.Ready(OLD)))
        advanceUntilIdle()

        viewModel.start()
        repository.answer(AppResult.Failure(AppError.Network))
        advanceUntilIdle()

        assertEquals(WifiUiState(status = WifiStatus.Failed(AppError.Network)), viewModel.uiState.value)
        assertNull(viewModel.uiState.value.credentials)
    }

    @Test
    fun theSheetShowsWhatIsuSaid() = runTest(main.dispatcher) {
        val cases = listOf(
            AppResult.Success(WifiAccess.NoPassword) to WifiStatus.NoPassword,
            AppResult.Success(WifiAccess.SignInRequired) to WifiStatus.SignInRequired,
            AppResult.Failure(AppError.Network) to WifiStatus.Failed(AppError.Network),
        )
        cases.forEach { (result, status) ->
            val repository = FakeWifiRepository().apply { answer(result) }
            val viewModel = WifiViewModel(repository)

            viewModel.start()
            advanceUntilIdle()

            assertEquals(WifiUiState(status = status), viewModel.uiState.value)
        }
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
        assertEquals(1, repository.loads)
    }

    /** A load waits for [answer]; an answer given before it is the next load's. */
    private class FakeWifiRepository : WifiRepository {
        private val queued = ArrayDeque<AppResult<WifiAccess>>()
        private var waiting: CompletableDeferred<AppResult<WifiAccess>>? = null
        var loads = 0

        fun answer(result: AppResult<WifiAccess>) {
            waiting?.takeIf { it.isActive }?.complete(result) ?: queued.addLast(result)
        }

        override suspend fun load(): AppResult<WifiAccess> {
            loads++
            queued.removeFirstOrNull()?.let { return it }
            return CompletableDeferred<AppResult<WifiAccess>>().also { waiting = it }.await()
        }
    }

    private companion object {
        val OLD = WifiCredentials("123456", "11111111")
        val NEW = WifiCredentials("123456", "22222222")
    }
}
