package dev.alllexey.itmowidgets.core.debug

import api.myitmo.MyItmo
import dev.alllexey.itmowidgets.core.testing.FakeSessionDataCleaner
import dev.alllexey.itmowidgets.core.testing.FakeSessionTokenStore
import dev.alllexey.itmowidgets.core.testing.MainDispatcherRule
import dev.alllexey.itmowidgets.core.testing.noDemo
import api.myitmo.model.other.TokenResponse
import api.myitmo.utils.TokenRefreshException
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

class DefaultDebugRefreshTokenControllerTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val dispatchers = mainDispatcherRule.appDispatchers

    @Test
    fun `trims token validates it and clears session data`() = runTest {
        val tokenStore = FakeSessionTokenStore(signedIn = false)
        val cleaner = FakeSessionDataCleaner()
        val myItmo = TokenRefreshingMyItmo()
        val controller = DefaultDebugRefreshTokenController(
            tokenStore = tokenStore,
            myItmo = myItmo,
            dataCleaners = setOf(cleaner),
            demo = noDemo(),
            dispatchers = dispatchers
        )

        val result = controller.replaceRefreshToken("  test-refresh-token  ")

        assertEquals(AppResult.Success(Unit), result)
        assertEquals("test-refresh-token", tokenStore.refreshToken)
        assertEquals(1, myItmo.refreshRequests)
        assertEquals(1, cleaner.requests)
    }

    @Test
    fun `clears rejected token and returns typed error`() = runTest {
        val tokenStore = FakeSessionTokenStore(signedIn = false)
        val cleaner = FakeSessionDataCleaner()
        val controller = DefaultDebugRefreshTokenController(
            tokenStore = tokenStore,
            myItmo = TokenRefreshingMyItmo(rejectToken = true),
            dataCleaners = setOf(cleaner),
            demo = noDemo(),
            dispatchers = dispatchers
        )

        val result = controller.replaceRefreshToken("rejected-token")

        assertEquals(AppResult.Failure(AppError.Unauthorized), result)
        assertNull(tokenStore.refreshToken)
        assertEquals(2, cleaner.requests)
    }

    private class TokenRefreshingMyItmo(
        private val rejectToken: Boolean = false
    ) : MyItmo() {
        var refreshRequests = 0

        override fun forceRefreshTokens(): TokenResponse {
            refreshRequests += 1
            if (rejectToken) {
                throw TokenRefreshException("Rejected test token")
            }
            return TokenResponse()
        }
    }
}
