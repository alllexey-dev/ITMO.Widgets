package dev.alllexey.itmowidgets.feature.weblogin.data

import dev.alllexey.itmoapi.core.MyItmoException
import dev.alllexey.itmoapi.itmoid.TokenStorage
import dev.alllexey.itmoapi.myitmo.MyItmoClient
import dev.alllexey.itmowidgets.core.coroutines.AppDispatchers
import dev.alllexey.itmowidgets.core.demo.DemoMode
import dev.alllexey.itmowidgets.core.network.asAppError
import dev.alllexey.itmowidgets.core.network.isCausedByNetworkFailure
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.result.appResultOf
import dev.alllexey.itmowidgets.feature.weblogin.domain.HandoffSession
import dev.alllexey.itmowidgets.feature.weblogin.domain.HandoffToken
import kotlinx.coroutines.withContext

/**
 * [HandoffSession] on MyItmoApi: `forceRefresh` of the client's `TokenManager`, the session's only refresher, under its
 * lock and the platform's refresh guard (iOS: the `myitmo-refresh` file lock), stores the new set as an on-demand
 * refresh would. The token read before it is handed out once and never written anywhere. A rejected refresh is
 * [AppError.Unauthorized] and ends the session through `RefreshRejections`, as any rejected refresh does. The demo
 * session has no ITMO session and is refused.
 */
class TokenManagerHandoffSession(
    private val myItmo: MyItmoClient,
    private val storage: TokenStorage,
    private val demo: DemoMode,
    private val dispatchers: AppDispatchers,
) : HandoffSession {

    // shortcut: reads storage around forceRefresh; switch to TokenManager.rotateForHandoff once MyItmoApi ships it.
    override suspend fun rotate(): AppResult<HandoffToken> {
        if (demo.isActive()) return AppResult.Failure(AppError.DemoUnavailable)
        return withContext(dispatchers.io) {
            appResultOf(::toAppError) {
                val previous = storage.read() ?: return@withContext AppResult.Failure(AppError.Unauthorized)
                myItmo.tokens.forceRefresh()
                // Whoever rotated, the phone now holds a newer token and never uses `previous` again.
                check(storage.read()?.refreshToken.let { it != null && it != previous.refreshToken }) {
                    "The refresh token was not rotated"
                }
                HandoffToken(previous.refreshToken, previous.refreshExpiresAt)
            }
        }
    }

    private fun toAppError(error: Exception): AppError = when {
        error.isCausedByNetworkFailure() -> AppError.Network
        error is MyItmoException -> error.asAppError()
        else -> AppError.Unknown(error)
    }
}
