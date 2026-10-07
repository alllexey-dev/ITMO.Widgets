package dev.alllexey.itmowidgets.core.network

import dev.alllexey.itmoapi.core.MyItmoException
import dev.alllexey.itmowidgets.core.result.AppError
import kotlinx.io.IOException

/**
 * Whether an I/O failure is anywhere in the cause chain; cycles in the chain are tolerated. A failure before any
 * answer (no DNS, refused or lost connection, timeout) is [AppError.Network] wherever it is wrapped: a token
 * refresh that could not reach ITMO.ID is not a rejected session.
 *
 * Both I/O exception roots count: Ktor (every engine, Darwin included) throws [IOException] of kotlinx-io, okio
 * throws its own. On the JVM both are `java.io.IOException`; on Kotlin/Native they are unrelated classes, and
 * checking only one of them would make iOS answer a failure Android calls [AppError.Network] with a generic error.
 */
fun Throwable.isCausedByNetworkFailure(): Boolean {
    var current: Throwable? = this
    val seen = mutableSetOf<Throwable>()
    while (current != null && seen.add(current)) {
        if (current is IOException || current is okio.IOException || current is MyItmoException.Network) return true
        current = current.cause
    }
    return false
}

/**
 * The released error semantics for a MyItmoApi 2.x failure. Only [MyItmoException.Auth] (ITMO.ID rejected the
 * refresh token, or MyITMO still answers 401 after one refresh) is [AppError.Unauthorized], which only a new
 * sign-in fixes. An unexplained status or a garbled body, among them an ITMO.ID 5xx during a refresh, is a
 * retriable [AppError.Unknown] that keeps the session.
 */
fun MyItmoException.asAppError(): AppError = when (this) {
    is MyItmoException.Network -> AppError.Network
    is MyItmoException.Auth -> AppError.Unauthorized
    is MyItmoException.Http -> when (status) {
        403 -> AppError.Forbidden
        404 -> AppError.NotFound
        else -> AppError.Unknown(this)
    }
    is MyItmoException.Api -> when (errorCode) {
        401 -> AppError.Unauthorized
        403 -> AppError.Forbidden
        404 -> AppError.NotFound
        else -> AppError.Unknown(this)
    }
    is MyItmoException.Decode -> AppError.Unknown(this)
}
