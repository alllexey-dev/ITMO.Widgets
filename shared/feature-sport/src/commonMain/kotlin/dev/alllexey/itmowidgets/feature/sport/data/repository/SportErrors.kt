package dev.alllexey.itmowidgets.feature.sport.data.repository

import dev.alllexey.itmoapi.core.MyItmoException
import dev.alllexey.itmowidgets.client.error.BackendException
import dev.alllexey.itmowidgets.core.network.asAppError
import dev.alllexey.itmowidgets.core.network.isCausedByNetworkFailure
import dev.alllexey.itmowidgets.core.result.AppError

/**
 * The app's released mapping for a sport call over MyItmoApi 2.x or Core 2.0: a failure before any answer is
 * [AppError.Network], then the first [MyItmoException] or [BackendException] in the cause chain maps in common code,
 * anything else is a retriable unknown. MyITMO's refusal stays an [AppError.Unknown] around its
 * `MyItmoException.Api`, which the push decision reads (`SportSignOutcome`). Sport-local until L07 adds a common
 * mapping.
 */
internal fun Exception.toSportAppError(): AppError {
    if (isCausedByNetworkFailure()) return AppError.Network
    val seen = mutableSetOf<Throwable>()
    var current: Throwable? = this
    while (current != null && seen.add(current)) {
        when (current) {
            is MyItmoException -> return current.asAppError()
            is BackendException -> return current.asAppError()
        }
        current = current.cause
    }
    return AppError.Unknown(this)
}
