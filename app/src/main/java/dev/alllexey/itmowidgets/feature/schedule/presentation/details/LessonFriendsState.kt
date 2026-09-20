package dev.alllexey.itmowidgets.feature.schedule.presentation.details

import dev.alllexey.itmowidgets.core.model.UserSummary
import dev.alllexey.itmowidgets.core.result.AppError

/** The "friends on this lesson" block of the details sheet. */
sealed interface LessonFriendsState {
    /** The opt-in is off: the block is not shown at all. */
    data object Disabled : LessonFriendsState
    data object Loading : LessonFriendsState
    data class Content(val friends: List<UserSummary>) : LessonFriendsState
    data class Error(val error: AppError) : LessonFriendsState
}
