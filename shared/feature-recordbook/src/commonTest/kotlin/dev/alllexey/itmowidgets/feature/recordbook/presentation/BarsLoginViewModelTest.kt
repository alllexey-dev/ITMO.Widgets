package dev.alllexey.itmowidgets.feature.recordbook.presentation

import androidx.lifecycle.SavedStateHandle
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.feature.recordbook.domain.BarsSessionRepository
import dev.alllexey.itmowidgets.testkit.TestMainDispatcher
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class BarsLoginViewModelTest {
    private val main = TestMainDispatcher()

    @BeforeTest fun setUp() = main.install()

    @AfterTest fun tearDown() = main.reset()

    @Test fun duplicateCallbackIsExchangedOnceWithTheAttemptState() = runTest(main.dispatcher) {
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
    @Test fun completionWhileNobodyCollectsArrivesOnTheNextCollection() = runTest(main.dispatcher) {
        val vm = BarsLoginViewModel(FakeBarsSessionRepository { AppResult.Success(Unit) }, SavedStateHandle())
        vm.complete("synthetic-callback"); advanceUntilIdle()
        assertEquals(Unit, vm.events.first())
    }
    @Test fun loginUrlAndCallbackCheckComeFromTheSessionPortForTheSavedState() {
        val repo = FakeBarsSessionRepository { AppResult.Failure(AppError.Forbidden) }
        val vm = BarsLoginViewModel(repo, SavedStateHandle(mapOf("bars_oauth_state" to "synthetic-state")))
        assertEquals("https://bars.example/login?state=synthetic-state", vm.loginUrl)
        assertEquals(vm.loginUrl, vm.loginUrl)
        assertTrue(vm.isCallback("https://bars.example/callback?state=other"))
        assertFalse(vm.isCallback("https://bars.example/login"))
    }
    @Test fun anyHttpsPageIsNavigableAndOtherSchemesAreNot() {
        val vm = BarsLoginViewModel(FakeBarsSessionRepository { AppResult.Failure(AppError.Forbidden) }, SavedStateHandle())
        assertTrue(vm.isNavigable("https://id.itmo.ru/auth/realms/itmo/protocol/openid-connect/auth"))
        assertTrue(vm.isNavigable("https://oauth.vk.com/authorize"))
        assertFalse(vm.isNavigable("http://id.itmo.ru/"))
        assertFalse(vm.isNavigable("vk://authorize"))
    }
    @Test fun failedSignInIsRetryableWithANewStateAndNoCompletionEvent() = runTest(main.dispatcher) {
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
