package dev.alllexey.itmowidgets.feature.resources.presentation

import dev.alllexey.itmowidgets.core.result.AppError

/** One-shot outcomes of the links sheets; the views turn a failure into text. */
sealed interface LinkEvent {
    data class Failed(val error: AppError) : LinkEvent
    data object Saved : LinkEvent
    /** An action other than a vote succeeded; a sheet opened for one action may close. */
    data object Done : LinkEvent
}
