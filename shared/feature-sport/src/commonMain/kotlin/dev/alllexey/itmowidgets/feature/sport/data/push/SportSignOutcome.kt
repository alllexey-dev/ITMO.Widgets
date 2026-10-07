package dev.alllexey.itmowidgets.feature.sport.data.push

import dev.alllexey.itmoapi.core.MyItmoException
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult

enum class SportSignOutcome { SIGNED_IN, NO_CAPACITY, REJECTED, RETRY_LATER }

/** Legacy MyITMO capacity signature, including the server's student/lesson context suffix. */
internal const val NO_CAPACITY_MESSAGE = "нельзя записать студента: нет свободных мест на занятии"

/**
 * Only MyITMO's own refusal on a successful HTTP exchange (an error envelope with HTTP 2xx, as POST
 * `sign/schedule/lessons` answers code 137 with its reasons) decides about the lesson. A failure before any answer,
 * another status, an envelope on a non-2xx status or an unreadable answer is retried later, as with 1.x.
 */
internal fun AppResult<Unit>.sportSignOutcome(): SportSignOutcome = when (this) {
    is AppResult.Success -> SportSignOutcome.SIGNED_IN
    is AppResult.Failure -> {
        val refusal = ((error as? AppError.Unknown)?.cause as? MyItmoException.Api)?.takeIf { it.status in 200..299 }
        val message = refusal?.serverMessage.orEmpty()
        when {
            refusal == null -> SportSignOutcome.RETRY_LATER
            message.contains(NO_CAPACITY_MESSAGE) && message.replace(NO_CAPACITY_MESSAGE, "").length > 20 ->
                SportSignOutcome.NO_CAPACITY
            else -> SportSignOutcome.REJECTED
        }
    }
}
