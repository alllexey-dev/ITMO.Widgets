package dev.alllexey.itmowidgets.core.network

import api.myitmo.utils.ApiException
import api.myitmo.utils.TokenRefreshException
import dev.alllexey.itmowidgets.core.result.AppError
import retrofit2.HttpException
import java.io.IOException

internal fun Throwable.toAppError(): AppError = when (this) {
    is IOException -> AppError.Network
    is TokenRefreshException -> AppError.Unauthorized
    is HttpException -> if (code() == 403 && backendErrorCode() == "restricted") AppError.Restricted else code().toAppError(cause = this)
    is ApiException -> {
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
