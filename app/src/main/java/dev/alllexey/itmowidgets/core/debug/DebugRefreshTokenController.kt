package dev.alllexey.itmowidgets.core.debug

import api.myitmo.MyItmo
import dev.alllexey.itmowidgets.BuildConfig
import dev.alllexey.itmowidgets.core.network.toAppError
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.session.SessionDataCleaner
import dev.alllexey.itmowidgets.core.session.SessionTokenStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface DebugRefreshTokenController {

    fun hasRefreshToken(): Boolean

    suspend fun replaceRefreshToken(refreshToken: String): AppResult<Unit>
}

class DefaultDebugRefreshTokenController(
    private val tokenStore: SessionTokenStore,
    private val myItmo: MyItmo,
    private val dataCleaners: Set<SessionDataCleaner>
) : DebugRefreshTokenController {

    override fun hasRefreshToken(): Boolean {
        return BuildConfig.DEBUG && tokenStore.hasRefreshToken()
    }

    override suspend fun replaceRefreshToken(
        refreshToken: String
    ): AppResult<Unit> = withContext(Dispatchers.IO) {
        if (!BuildConfig.DEBUG) {
            return@withContext AppResult.Failure(AppError.Forbidden)
        }

        val normalizedToken = refreshToken.trim()
        if (normalizedToken.isEmpty()) {
            return@withContext AppResult.Failure(AppError.Unauthorized)
        }

        try {
            tokenStore.replaceWithRefreshToken(normalizedToken)
            clearSessionData()
            myItmo.forceRefreshTokens()
            AppResult.Success(Unit)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            tokenStore.clearTokens()
            clearSessionDataIgnoringFailures()
            AppResult.Failure(error.toAppError())
        }
    }

    private suspend fun clearSessionData() {
        dataCleaners.forEach { cleaner -> cleaner.clearSessionData() }
    }

    private suspend fun clearSessionDataIgnoringFailures() {
        dataCleaners.forEach { cleaner ->
            runCatching { cleaner.clearSessionData() }
        }
    }
}
