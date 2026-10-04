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
        val repo = FakeBarsSessionRepository { expectedState ->
            assertEquals("synthetic-state", expectedState)
            pending.await()
        }
        val vm = BarsLoginViewModel(repo, SavedStateHandle(mapOf("bars_oauth_state" to "synthetic-state")))
        val event = async { vm.events.first() }
        vm.complete("synthetic-callback"); vm.complete("synthetic-callback"); runCurrent()
        assertEquals(1, repo.completions)
        assertTrue(vm.uiState.value.completing)
        pending.complete(AppResult.Success(Unit)); advanceUntilIdle()
        assertEquals(Unit, event.await())
    }
    @Test fun `login url and callback check come from the session port for the saved state`() {
        val repo = FakeBarsSessionRepository { AppResult.Failure(AppError.Forbidden) }
        val vm = BarsLoginViewModel(repo, SavedStateHandle(mapOf("bars_oauth_state" to "synthetic-state")))
        assertEquals("https://bars.example/login?state=synthetic-state", vm.loginUrl)
        assertEquals(vm.loginUrl, vm.loginUrl)
        assertTrue(vm.isCallback("https://bars.example/callback?state=other"))
        assertFalse(vm.isCallback("https://bars.example/login"))
    }
    @Test fun `any https page is navigable and other schemes are not`() {
        val vm = BarsLoginViewModel(FakeBarsSessionRepository { AppResult.Failure(AppError.Forbidden) }, SavedStateHandle())
        assertTrue(vm.isNavigable("https://id.itmo.ru/auth/realms/itmo/protocol/openid-connect/auth"))
        assertTrue(vm.isNavigable("https://oauth.vk.com/authorize"))
        assertFalse(vm.isNavigable("http://id.itmo.ru/"))
        assertFalse(vm.isNavigable("vk://authorize"))
    }
    @Test fun `failed sign in is retryable with a new state and no completion event`() = runTest {
        val vm = BarsLoginViewModel(FakeBarsSessionRepository { AppResult.Failure(AppError.Forbidden) }, SavedStateHandle())
        val initial = vm.loginUrl
        vm.complete("synthetic-callback"); advanceUntilIdle()
        assertEquals(AppError.Forbidden, vm.uiState.value.error)
        assertFalse(vm.uiState.value.completing)
        vm.retry()
        assertNotEquals(initial, vm.loginUrl)
        assertNull(vm.uiState.value.error)
    }

    private class FakeBarsSessionRepository(
        private val completion: suspend (expectedState: String) -> AppResult<Unit>
    ) : BarsSessionRepository {
        var completions = 0
            private set

        override fun loginUrl(state: String) = "https://bars.example/login?state=$state"

        override fun isCallback(url: String) = url.startsWith("https://bars.example/callback")

        override suspend fun completeLogin(callbackUrl: String, expectedState: String): AppResult<Unit> {
            completions++
            return completion(expectedState)
        }
    }
}
