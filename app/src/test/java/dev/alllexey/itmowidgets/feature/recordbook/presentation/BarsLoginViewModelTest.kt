package dev.alllexey.itmowidgets.feature.recordbook.presentation

import androidx.lifecycle.SavedStateHandle
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.testing.MainDispatcherRule
import dev.alllexey.itmowidgets.feature.recordbook.domain.BarsSessionRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BarsLoginViewModelTest {
    @get:Rule val dispatcher = MainDispatcherRule()
    @Test fun `duplicate callback is exchanged once with the attempt state`() = runTest {
        val pending = CompletableDeferred<AppResult<Unit>>()
        var calls = 0
        val repo = object : BarsSessionRepository {
            override suspend fun completeLogin(callbackUrl: String, expectedState: String): AppResult<Unit> {
                calls++
                assertEquals("synthetic-state", expectedState)
                return pending.await()
            }
        }
        val vm = BarsLoginViewModel(repo, SavedStateHandle(mapOf("bars_oauth_state" to "synthetic-state")))
        val event = async { vm.events.first() }
        vm.complete("synthetic-callback"); vm.complete("synthetic-callback"); runCurrent()
        assertEquals(1, calls)
        assertTrue(vm.uiState.value.completing)
        pending.complete(AppResult.Success(Unit)); advanceUntilIdle()
        assertEquals(Unit, event.await())
    }
    @Test fun `failed sign in is retryable with a new state and no completion event`() = runTest {
        val repo = object : BarsSessionRepository {
            override suspend fun completeLogin(callbackUrl: String, expectedState: String) = AppResult.Failure(AppError.Forbidden)
        }
        val vm = BarsLoginViewModel(repo, SavedStateHandle())
        val initial = vm.loginUrl
        vm.complete("synthetic-callback"); advanceUntilIdle()
        assertEquals(AppError.Forbidden, vm.uiState.value.error)
        assertFalse(vm.uiState.value.completing)
        vm.retry()
        assertNotEquals(initial, vm.loginUrl)
        assertNull(vm.uiState.value.error)
    }
}
