package dev.alllexey.itmowidgets.feature.schedule.data

import dev.alllexey.itmoapi.core.MyItmoException
import dev.alllexey.itmowidgets.client.error.BackendException
import dev.alllexey.itmowidgets.core.network.asAppError
import dev.alllexey.itmowidgets.core.network.isCausedByNetworkFailure
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.result.appResultOf

/**
 * The released error semantics of the schedule data, in common code: a failure before any answer is
 * [AppError.Network] wherever it is wrapped; otherwise the first Core 2.0 or MyItmoApi 2.x failure in the cause chain
 * maps through its own table; anything else is [AppError.Unknown]. Internal to this module until `:shared:core` has
 * one common mapping for every client.
 */
internal fun Throwable.toAppError(): AppError {
    if (isCausedByNetworkFailure()) return AppError.Network
    val seen = mutableSetOf<Throwable>()
    var current: Throwable? = this
    while (current != null && seen.add(current)) {
        when (current) {
            is BackendException -> return current.asAppError()
            is MyItmoException -> return current.asAppError()
        }
        current = current.cause
    }
    return AppError.Unknown(this)
}

/** [appResultOf] with [toAppError]. */
internal inline fun <T> scheduleResultOf(block: () -> T): AppResult<T> = appResultOf(Throwable::toAppError, block)
