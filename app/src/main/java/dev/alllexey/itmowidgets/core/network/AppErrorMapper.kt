package dev.alllexey.itmowidgets.core.network

import api.myitmo.utils.ApiException
import api.myitmo.utils.TokenRefreshException
import dev.alllexey.itmoapi.core.MyItmoException
import dev.alllexey.itmowidgets.client.error.BackendException
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.result.appResultOf
import retrofit2.HttpException

/** [appResultOf] with the default mapping of network, ITMO.ID and Backend failures. */
internal inline fun <T> appResultOf(block: () -> T): AppResult<T> = appResultOf(Throwable::toAppError, block)

/**
 * A failure before any answer (no DNS, refused or lost connection, timeout) is [AppError.Network] wherever it is
 * wrapped: a token refresh that could not reach ITMO.ID is not a rejected session. MyItmoApi 2.x and Core 2.0
 * failures map in common code; the 1.x and Retrofit branches go with those clients in KM-10i.
 */
internal fun Throwable.toAppError(): AppError = when {
    isCausedByNetworkFailure() -> AppError.Network
    this is MyItmoException -> asAppError()
    this is BackendException -> asAppError()
    this is TokenRefreshException -> AppError.Unauthorized
    this is HttpException -> if (code() == 403 && backendErrorCode() == "restricted") AppError.Restricted else code().toAppError(cause = this)
    this is ApiException -> {
        errorCode?.toAppError(cause = this)
            ?: cause?.toAppError()
            ?: AppError.Unknown(this)
    }
    else -> cause
        ?.takeIf { it !== this }
        ?.toAppError()
        ?: AppError.Unknown(this)
}

private fun Int.toAppError(cause: Throwable): AppError = when (this) {
    401 -> AppError.Unauthorized
    403 -> AppError.Forbidden
    404 -> AppError.NotFound
    else -> AppError.Unknown(cause)
}
