package dev.alllexey.itmowidgets.feature.weblogin.data

import dev.alllexey.itmoapi.core.MyItmoException
import dev.alllexey.itmowidgets.client.error.BackendException
import dev.alllexey.itmowidgets.client.users.UsersApi
import dev.alllexey.itmowidgets.core.coroutines.AppDispatchers
import dev.alllexey.itmowidgets.core.demo.DemoMode
import dev.alllexey.itmowidgets.core.network.asAppError
import dev.alllexey.itmowidgets.core.network.isCausedByNetworkFailure
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.result.appResultOf
import dev.alllexey.itmowidgets.core.services.BackendGate
import dev.alllexey.itmowidgets.core.weblogin.WebLoginPreview
import dev.alllexey.itmowidgets.core.weblogin.WebLoginRepository
import kotlinx.coroutines.withContext
import kotlin.uuid.Uuid
import dev.alllexey.itmowidgets.client.users.WebLoginPreview as WirePreview

/**
 * Browser sign-in through Core 2.0. An unknown, used or expired code (404) is [AppError.NotFound]; a 403 is
 * [AppError.Forbidden], or [AppError.Restricted] for a moderation restriction, as the released app mapped them.
 * The demo session is refused before the opt-in is read, so it never reaches Backend.
 */
class WebLoginRepositoryImpl(
    private val backend: BackendGate,
    private val users: UsersApi,
    private val demo: DemoMode,
    private val dispatchers: AppDispatchers,
) : WebLoginRepository {

    override suspend fun preview(code: String): AppResult<WebLoginPreview> =
        call { users.webLoginPreview(code).toModel() }

    override suspend fun approve(challengeId: Uuid): AppResult<Unit> = call { users.approveWebLogin(challengeId) }

    private suspend fun <T> call(request: suspend () -> T): AppResult<T> {
        if (demo.isActive()) return AppResult.Failure(AppError.DemoUnavailable)
        if (!backend.mayCallBackend()) return AppResult.Failure(AppError.CustomServicesDisabled)
        return appResultOf(::toAppError) { withContext(dispatchers.io) { request() } }
    }

    private fun WirePreview.toModel() =
        WebLoginPreview(challengeId, userAgent?.trim()?.ifEmpty { null }, createdAt, expiresAt)

    /**
     * The released mapping in common code: a failure before any answer is [AppError.Network] wherever it is
     * wrapped; otherwise the first Core 2.0 or MyItmoApi 2.x failure in the cause chain maps through its own table
     * (the token refresh runs through MyItmoApi); anything else is [AppError.Unknown].
     */
    private fun toAppError(error: Exception): AppError {
        if (error.isCausedByNetworkFailure()) return AppError.Network
        val seen = mutableSetOf<Throwable>()
        var current: Throwable? = error
        while (current != null && seen.add(current)) {
            when (current) {
                is MyItmoException -> return current.asAppError()
                is BackendException -> return current.asAppError()
            }
            current = current.cause
        }
        return AppError.Unknown(error)
    }
}
