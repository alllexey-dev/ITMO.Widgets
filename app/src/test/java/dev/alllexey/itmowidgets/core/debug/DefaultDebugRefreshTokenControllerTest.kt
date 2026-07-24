package dev.alllexey.itmowidgets.core.debug

import api.myitmo.MyItmo
import api.myitmo.model.other.TokenResponse
import api.myitmo.utils.TokenRefreshException
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.session.SessionDataCleaner
import dev.alllexey.itmowidgets.core.session.SessionTokenStore
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DefaultDebugRefreshTokenControllerTest {

    @Test
    fun `trims token validates it and clears session data`() = runTest {
        val tokenStore = FakeTokenStore()
        val cleaner = CountingCleaner()
        val myItmo = FakeMyItmo()
        val controller = DefaultDebugRefreshTokenController(
            tokenStore = tokenStore,
            myItmo = myItmo,
            dataCleaners = setOf(cleaner)
        )

        val result = controller.replaceRefreshToken("  test-refresh-token  ")

        assertEquals(AppResult.Success(Unit), result)
        assertEquals("test-refresh-token", tokenStore.refreshToken)
        assertEquals(1, myItmo.refreshRequests)
        assertEquals(1, cleaner.clearRequests)
    }

    @Test
    fun `clears rejected token and returns typed error`() = runTest {
        val tokenStore = FakeTokenStore()
        val cleaner = CountingCleaner()
        val controller = DefaultDebugRefreshTokenController(
            tokenStore = tokenStore,
            myItmo = FakeMyItmo(rejectToken = true),
            dataCleaners = setOf(cleaner)
        )

        val result = controller.replaceRefreshToken("rejected-token")

        assertEquals(AppResult.Failure(AppError.Unauthorized), result)
        assertNull(tokenStore.refreshToken)
        assertEquals(2, cleaner.clearRequests)
    }

    private class FakeTokenStore : SessionTokenStore {
        var refreshToken: String? = null

        override fun hasRefreshToken(): Boolean = refreshToken != null

        override fun replaceWithRefreshToken(refreshToken: String) {
            this.refreshToken = refreshToken
        }

        override fun clearTokens() {
            refreshToken = null
        }
    }

    private class CountingCleaner : SessionDataCleaner {
        var clearRequests = 0

        override fun clearSessionData() {
            clearRequests += 1
        }
    }

    private class FakeMyItmo(
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
