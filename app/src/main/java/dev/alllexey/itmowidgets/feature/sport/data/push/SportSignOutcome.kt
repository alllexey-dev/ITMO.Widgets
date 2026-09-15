package dev.alllexey.itmowidgets.feature.sport.data.push

import api.myitmo.utils.ApiException
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult

enum class SportSignOutcome { SIGNED_IN, NO_CAPACITY, REJECTED, RETRY_LATER }

/** Legacy MyITMO capacity signature, including the server's student/lesson context suffix. */
internal const val NO_CAPACITY_MESSAGE = "нельзя записать студента: нет свободных мест на занятии"

internal fun AppResult<Unit>.sportSignOutcome(): SportSignOutcome = when (this) {
    is AppResult.Success -> SportSignOutcome.SIGNED_IN
    is AppResult.Failure -> {
        val apiError = (error as? AppError.Unknown)?.cause as? ApiException
        val message = apiError?.errorMessage.orEmpty()
        when {
            apiError == null || apiError.cause != null -> SportSignOutcome.RETRY_LATER
            message.contains(NO_CAPACITY_MESSAGE) && message.replace(NO_CAPACITY_MESSAGE, "").length > 20 ->
                SportSignOutcome.NO_CAPACITY
            else -> SportSignOutcome.REJECTED
        }
    }
}
