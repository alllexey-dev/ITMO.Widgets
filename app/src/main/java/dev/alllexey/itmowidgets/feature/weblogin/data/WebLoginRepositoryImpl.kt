package dev.alllexey.itmowidgets.feature.weblogin.data

import dev.alllexey.itmowidgets.client.users.UsersApi
import dev.alllexey.itmowidgets.core.coroutines.AppDispatchers
import dev.alllexey.itmowidgets.core.demo.DemoMode
import dev.alllexey.itmowidgets.core.network.appResultOf
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.services.BackendGate
import dev.alllexey.itmowidgets.core.weblogin.WebLoginPreview
import dev.alllexey.itmowidgets.core.weblogin.WebLoginRepository
import javax.inject.Inject
import kotlinx.coroutines.withContext
import kotlin.uuid.Uuid
import dev.alllexey.itmowidgets.client.users.WebLoginPreview as WirePreview

/**
 * Browser sign-in through Core 2.0. An unknown, used or expired code (404) is [AppError.NotFound]; a 403 is
 * [AppError.Forbidden], or [AppError.Restricted] for a moderation restriction, as the released app mapped them.
 */
class WebLoginRepositoryImpl @Inject constructor(
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
        return appResultOf { withContext(dispatchers.io) { request() } }
    }

    private fun WirePreview.toModel() =
        WebLoginPreview(challengeId, userAgent?.trim()?.ifEmpty { null }, createdAt, expiresAt)
}
