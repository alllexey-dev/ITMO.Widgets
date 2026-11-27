package dev.alllexey.itmowidgets.feature.me.moderation.data

import dev.alllexey.itmowidgets.client.error.BackendException
import dev.alllexey.itmowidgets.core.network.asAppError
import dev.alllexey.itmowidgets.core.network.isCausedByNetworkFailure
import dev.alllexey.itmowidgets.core.result.AppError

/**
 * A failure before any answer is [AppError.Network] wherever it is wrapped; otherwise the first Backend failure in the
 * cause chain maps through its table; anything else is [AppError.Unknown]. The same rule as the other Backend features.
 */
internal fun Exception.toAppError(): AppError {
    if (isCausedByNetworkFailure()) return AppError.Network
    val seen = mutableSetOf<Throwable>()
    var current: Throwable? = this
    while (current != null && seen.add(current)) {
        if (current is BackendException) return current.asAppError()
        current = current.cause
    }
    return AppError.Unknown(this)
}
