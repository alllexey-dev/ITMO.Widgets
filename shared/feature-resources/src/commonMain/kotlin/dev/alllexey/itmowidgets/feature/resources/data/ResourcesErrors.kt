package dev.alllexey.itmowidgets.feature.resources.data

import dev.alllexey.itmowidgets.client.error.BackendException
import dev.alllexey.itmowidgets.core.network.asAppError
import dev.alllexey.itmowidgets.core.network.isCausedByNetworkFailure
import dev.alllexey.itmowidgets.core.result.AppError

/**
 * The released error semantics of the subject links, in common code: a failure before any answer is [AppError.Network]
 * wherever it is wrapped; otherwise the first Core 2.0 failure in the cause chain maps through its table (401
 * Unauthorized, 403 `restricted` Restricted, other 403 Forbidden, 404 NotFound, 409 and the rest Unknown); anything
 * else is [AppError.Unknown]. Internal to this module until `:shared:core` has one common mapping for every client.
 */
internal fun Throwable.toAppError(): AppError {
    if (isCausedByNetworkFailure()) return AppError.Network
    val seen = mutableSetOf<Throwable>()
    var current: Throwable? = this
    while (current != null && seen.add(current)) {
        if (current is BackendException) return current.asAppError()
        current = current.cause
    }
    return AppError.Unknown(this)
}
