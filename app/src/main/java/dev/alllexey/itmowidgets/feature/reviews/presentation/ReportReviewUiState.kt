package dev.alllexey.itmowidgets.feature.reviews.presentation

import dev.alllexey.itmowidgets.core.result.AppError

/** [sending] holds from the tap until the report is answered, so a second tap sends nothing. */
data class ReportReviewUiState(val sending: Boolean = false)

sealed interface ReportReviewEvent {
    data object Done : ReportReviewEvent
    data class Failed(val error: AppError) : ReportReviewEvent
}
