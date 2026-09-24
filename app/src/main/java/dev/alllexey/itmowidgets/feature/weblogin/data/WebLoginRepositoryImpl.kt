package dev.alllexey.itmowidgets.feature.weblogin.data

import dev.alllexey.itmowidgets.core.ItmoWidgetsApi
import dev.alllexey.itmowidgets.core.model.ApiResponse
import dev.alllexey.itmowidgets.core.network.toAppError
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.services.CustomServicesRepository
import dev.alllexey.itmowidgets.core.weblogin.WebLoginPreview
import dev.alllexey.itmowidgets.core.weblogin.WebLoginRepository
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import dev.alllexey.itmowidgets.core.model.WebLoginPreview as WirePreview

class WebLoginRepositoryImpl @Inject constructor(
    private val customServices: CustomServicesRepository,
    private val widgetsApi: ItmoWidgetsApi,
) : WebLoginRepository {

    override suspend fun preview(code: String): AppResult<WebLoginPreview> = call { widgetsApi.webLoginPreview(code) }
        .let { result ->
            when (result) {
                is AppResult.Success -> result.value?.let { AppResult.Success(it.toModel()) }
                    ?: AppResult.Failure(AppError.Unknown())
                is AppResult.Failure -> result
            }
        }

    override suspend fun approve(challengeId: UUID): AppResult<Unit> = when (val result = call { widgetsApi.approveWebLogin(challengeId) }) {
        is AppResult.Success -> AppResult.Success(Unit)
        is AppResult.Failure -> result
    }

    /** Null data is a valid answer only for calls without a body, like the approval. */
    private suspend fun <T> call(request: suspend () -> ApiResponse<T>): AppResult<T?> {
        if (!customServices.isEnabled()) return AppResult.Failure(AppError.CustomServicesDisabled)
        return try {
            val response = withContext(Dispatchers.IO) { request() }
            if (response.success) AppResult.Success(response.data) else AppResult.Failure(backendError(response.error?.code))
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            AppResult.Failure(error.toAppError())
        }
    }

    private fun backendError(code: String?): AppError = when (code) {
        "not_found" -> AppError.NotFound
        "permission_denied" -> AppError.Forbidden
        "restricted" -> AppError.Restricted
        else -> AppError.Unknown()
    }

    private fun WirePreview.toModel() = WebLoginPreview(challengeId, userAgent?.trim()?.ifEmpty { null }, createdAt, expiresAt)
}
