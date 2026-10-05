package dev.alllexey.itmowidgets.feature.reviews.presentation

import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.reviews.TeacherReviewLimits

/** Why a field of the review form was not accepted, by [TeacherReviewLimits]; the view picks the text. */
enum class ReviewFieldError {
    TEXT_TOO_SHORT,
    TEXT_TOO_LONG,
    SUBJECT_TOO_LONG,
}

data class ReviewEditorUiState(
    val subject: String = "",
    val text: String = "",
    val anonymous: Boolean = true,
    /** Subjects of the viewer's own lessons with the teacher, newest first. */
    val suggestions: List<String> = emptyList(),
    val subjectError: ReviewFieldError? = null,
    val textError: ReviewFieldError? = null,
    val saving: Boolean = false,
    val editing: Boolean = false,
) {
    val canSave: Boolean get() = text.isNotBlank() && !saving

    /** The minimum length is a quiet hint while typing; it turns into an error only on send. */
    val showsMinimumHint: Boolean get() = textError == null && TeacherReviewLimits.length(text.trim()) < TeacherReviewLimits.MIN_TEXT
}

sealed interface ReviewEditorEvent {
    data object Saved : ReviewEditorEvent
    data class Failed(val error: AppError) : ReviewEditorEvent
}
