package dev.alllexey.itmowidgets.core.network

import dev.alllexey.itmoapi.core.MyItmoException
import dev.alllexey.itmowidgets.client.error.BackendException
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.result.appResultOf

/** [appResultOf] with the default mapping of network, ITMO.ID and Backend failures. */
internal inline fun <T> appResultOf(block: () -> T): AppResult<T> = appResultOf(Throwable::toAppError, block)

/**
 * A failure before any answer (no DNS, refused or lost connection, timeout) is [AppError.Network] wherever it is
 * wrapped: a token refresh that could not reach ITMO.ID is not a rejected session. MyItmoApi 2.x and Core 2.0
 * failures map in common code; anything else maps by its cause.
 */
internal fun Throwable.toAppError(): AppError = when {
    isCausedByNetworkFailure() -> AppError.Network
    this is MyItmoException -> asAppError()
    this is BackendException -> asAppError()
    else -> cause
        ?.takeIf { it !== this }
        ?.toAppError()
        ?: AppError.Unknown(this)
}
