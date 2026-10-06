package dev.alllexey.itmowidgets.core.network

import dev.alllexey.itmowidgets.client.error.BackendException
import dev.alllexey.itmowidgets.core.result.AppError

/** Backend's `error.code` for a moderation restriction; any other 403 is [AppError.Forbidden]. */
private const val RESTRICTED = "restricted"

/**
 * The released error semantics for a Core 2.0 failure: 401 is [AppError.Unauthorized], a 403 `restricted` is
 * [AppError.Restricted], any other 403 is [AppError.Forbidden], 404 is [AppError.NotFound], and no answer (the
 * connection, the body read or the token source failed, a token refresh included) is [AppError.Network]. Another
 * status or an answer that breaks the contract is [AppError.Unknown] keeping the exception.
 */
fun BackendException.asAppError(): AppError = when (this) {
    is BackendException.Unauthorized -> AppError.Unauthorized
    is BackendException.Forbidden -> if (code == RESTRICTED) AppError.Restricted else AppError.Forbidden
    is BackendException.NotFound -> AppError.NotFound
    is BackendException.Transport -> AppError.Network
    is BackendException.Http, is BackendException.Contract -> AppError.Unknown(this)
}
