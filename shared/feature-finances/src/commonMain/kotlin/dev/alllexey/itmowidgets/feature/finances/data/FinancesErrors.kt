package dev.alllexey.itmowidgets.feature.finances.data

import dev.alllexey.itmoapi.core.MyItmoException
import dev.alllexey.itmowidgets.core.network.asAppError
import dev.alllexey.itmowidgets.core.network.isCausedByNetworkFailure
import dev.alllexey.itmowidgets.core.result.AppError

/**
 * A finances call's failure: before any answer [AppError.Network], then the first [MyItmoException] in the cause
 * chain (a body MyItmoApi cannot decode is its redacted `Decode`), anything else a retriable unknown. The message of
 * no exception ever carries the student's data.
 */
internal fun Exception.toFinancesAppError(): AppError {
    if (isCausedByNetworkFailure()) return AppError.Network
    val seen = mutableSetOf<Throwable>()
    var current: Throwable? = this
    while (current != null && seen.add(current)) {
        if (current is MyItmoException) return current.asAppError()
        current = current.cause
    }
    return AppError.Unknown(this)
}
