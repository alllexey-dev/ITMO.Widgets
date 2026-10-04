package dev.alllexey.itmowidgets.core.network

import api.myitmo.utils.ApiException
import api.myitmo.utils.TokenRefreshException
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.result.appResultOf
import retrofit2.HttpException
import java.io.IOException

/** [appResultOf] with the default mapping of network, ITMO.ID and Backend failures. */
internal inline fun <T> appResultOf(block: () -> T): AppResult<T> = appResultOf(Throwable::toAppError, block)

/**
 * A failure before any answer (no DNS, refused or lost connection, timeout) is [AppError.Network] wherever it is
 * wrapped: a token refresh that could not reach ITMO.ID is not a rejected session.
 */
internal fun Throwable.toAppError(): AppError = when {
    isCausedByNetworkFailure() -> AppError.Network
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

/** Whether an [IOException] is anywhere in the cause chain; cycles in the chain are tolerated. */
internal fun Throwable.isCausedByNetworkFailure(): Boolean {
    var current: Throwable? = this
    val seen = mutableSetOf<Throwable>()
    while (current != null && seen.add(current)) {
        if (current is IOException) return true
        current = current.cause
    }
    return false
}

private fun Int.toAppError(cause: Throwable): AppError = when (this) {
    401 -> AppError.Unauthorized
    403 -> AppError.Forbidden
    404 -> AppError.NotFound
    else -> AppError.Unknown(cause)
}
