package dev.alllexey.itmowidgets.feature.recordbook.presentation

import androidx.lifecycle.SavedStateHandle
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.feature.recordbook.MainDispatcherRule
import dev.alllexey.itmowidgets.feature.recordbook.data.lms.LmsAuthorization
import dev.alllexey.itmowidgets.feature.recordbook.domain.lms.LmsGateway
import dev.alllexey.itmowidgets.feature.recordbook.domain.lms.LmsLoadResult
import dev.alllexey.itmowidgets.feature.recordbook.domain.lms.LmsScore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LmsLoginViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private class FakeLmsGateway(
        var startError: AppError? = null,
        var loadResult: LmsLoadResult = LmsLoadResult.Success(emptyList()),
    ) : LmsGateway {
        var lastLoadArgs: List<Any?>? = null
        override val loginUrl: String = LmsAuthorization.LOGIN_URL
        override fun authorizationState(url: String): String? = LmsAuthorization.stateFromAuthorization(url)
        override fun callbackCode(url: String, state: String): String? = LmsAuthorization.callbackCode(url, state)
        override fun isAllowedBrowserUrl(url: String): Boolean = LmsAuthorization.isAllowedBrowserUrl(url)
        override suspend fun canStart(): AppError? = startError
        override suspend fun load(code: String, state: String, subject: String, year: String, semester: Int): LmsLoadResult {
            lastLoadArgs = listOf(code, state, subject, year, semester)
            return loadResult
        }
    }

    private fun handle(
        subject: String = "Программирование",
        year: String = "2026/2027",
        semester: Int = 3,
    ) = SavedStateHandle(
        mapOf(
            LmsLoginViewModel.EXTRA_SUBJECT to subject,
            LmsLoginViewModel.EXTRA_YEAR to year,
            LmsLoginViewModel.EXTRA_SEMESTER to semester,
        )
    )

    @Test fun `starts in Web when allowed and increments attempt on retry`() = runTest {
        val vm = LmsLoginViewModel(FakeLmsGateway(), handle())
        advanceUntilIdle()
        assertEquals(LmsLoginUiState.Web(attempt = 1), vm.uiState.value)

        vm.onWebError()
        assertEquals(LmsLoginUiState.Error(LmsLoginError.NETWORK_OR_UNKNOWN, clearCookiesOnRetry = false), vm.uiState.value)

        vm.start()
        advanceUntilIdle()
        assertEquals(LmsLoginUiState.Web(attempt = 2), vm.uiState.value)
    }

    @Test fun `demo and unauthenticated session fail before opening web`() = runTest {
        val demoVm = LmsLoginViewModel(FakeLmsGateway(startError = AppError.DemoUnavailable), handle())
        advanceUntilIdle()
        assertEquals(LmsLoginUiState.Error(LmsLoginError.DEMO, clearCookiesOnRetry = false), demoVm.uiState.value)

        val authVm = LmsLoginViewModel(FakeLmsGateway(startError = AppError.Unauthorized), handle())
        advanceUntilIdle()
        assertEquals(LmsLoginUiState.Error(LmsLoginError.WRONG_ACCOUNT, clearCookiesOnRetry = true), authVm.uiState.value)
    }

    private fun authUrl(state: String) =
        "https://sso.tuxedo.se.ifmo.ru/realms/Newdex/protocol/openid-connect/auth" +
            "?client_id=newdex-backend&redirect_uri=https%3A%2F%2Flms.tuxedo.se.ifmo.ru%2Fsso%2Ftoken" +
            "&response_type=code&state=$state"

    @Test fun `rejects disallowed urls and invalid callbacks, emits Completed on match`() = runTest {
        val scores = listOf(LmsScore("РК №1", 9.0, 10.0, "VERIFIED"))
        val gateway = FakeLmsGateway(loadResult = LmsLoadResult.Success(scores))
        val vm = LmsLoginViewModel(gateway, handle())
        advanceUntilIdle()

        assertTrue(vm.onNavigation("https://evil.example.org/"))
        assertEquals(LmsLoginUiState.Error(LmsLoginError.NETWORK_OR_UNKNOWN), vm.uiState.value)

        vm.start()
        advanceUntilIdle()
        // Callback before OAuth state capture fails.
        assertTrue(vm.onNavigation("https://lms.tuxedo.se.ifmo.ru/sso/token?code=abc&state=st1"))
        assertEquals(LmsLoginUiState.Error(LmsLoginError.NETWORK_OR_UNKNOWN), vm.uiState.value)

        vm.start()
        advanceUntilIdle()
        assertFalse(vm.onNavigation(authUrl("st2")))
        assertTrue(vm.onNavigation("https://lms.tuxedo.se.ifmo.ru/sso/token?code=code2&state=st2"))
        advanceUntilIdle()

        assertEquals(LmsLoginEvent.Completed(scores), vm.events.first())
        assertEquals(listOf("code2", "st2", "Программирование", "2026/2027", 3), gateway.lastLoadArgs)
    }

    @Test fun `maps empty courses, no match, course failure and wrong account errors`() = runTest {
        val cases = listOf(
            LmsLoadResult.EmptyCourses to LmsLoginUiState.Error(LmsLoginError.EMPTY_COURSES),
            LmsLoadResult.NoMatch to LmsLoginUiState.Error(LmsLoginError.NO_MATCH),
            LmsLoadResult.CourseFailed(AppError.Network) to LmsLoginUiState.Error(LmsLoginError.COURSE_LOAD_FAILED),
            LmsLoadResult.Failed(AppError.Forbidden) to LmsLoginUiState.Error(LmsLoginError.WRONG_ACCOUNT, clearCookiesOnRetry = true),
            LmsLoadResult.Failed(AppError.Unauthorized) to LmsLoginUiState.Error(LmsLoginError.WRONG_ACCOUNT, clearCookiesOnRetry = true),
        )
        for ((loadResult, expected) in cases) {
            val gateway = FakeLmsGateway(loadResult = loadResult)
            val vm = LmsLoginViewModel(gateway, handle())
            advanceUntilIdle()
            assertFalse(vm.onNavigation(authUrl("s1")))
            assertTrue(vm.onNavigation("https://lms.tuxedo.se.ifmo.ru/sso/token?code=c1&state=s1"))
            advanceUntilIdle()
            assertEquals(expected, vm.uiState.value)
        }
    }
}
