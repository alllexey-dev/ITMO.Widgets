package dev.alllexey.itmowidgets.feature.connections.data

import dev.alllexey.itmoapi.core.MyItmoException
import dev.alllexey.itmowidgets.client.error.BackendException
import dev.alllexey.itmowidgets.core.network.asAppError
import dev.alllexey.itmowidgets.core.network.isCausedByNetworkFailure
import dev.alllexey.itmowidgets.core.result.AppError

/**
 * The released mapping in common code: a failure before any answer is [AppError.Network] wherever it is wrapped;
 * otherwise the first Core 2.0 or MyItmoApi 2.x failure in the cause chain maps through its own table (the token
 * refresh runs through MyItmoApi); anything else is [AppError.Unknown].
 */
internal fun backendAppError(error: Exception): AppError {
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
