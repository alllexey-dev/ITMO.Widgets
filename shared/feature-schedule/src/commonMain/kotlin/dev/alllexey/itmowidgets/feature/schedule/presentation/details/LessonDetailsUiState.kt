package dev.alllexey.itmowidgets.feature.schedule.presentation.details

import dev.alllexey.itmowidgets.core.model.UserSummary
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.reviews.TeacherLevel
import dev.alllexey.itmowidgets.core.schedule.ScheduleChange

/** What the details sheet shows besides the lesson it was opened with. */
data class LessonDetailsUiState(
    val friends: LessonFriendsState = LessonFriendsState.Loading,
    /** The tone of the teacher's reviews: `null` without a teacher ISU, the opt-in or a tone. */
    val teacherLevel: TeacherLevel? = null,
    /** The newest change of the last 30 days that touches this occurrence, from the local store only. */
    val change: ScheduleChange? = null
)

/** The "friends on this lesson" block of the details sheet. */
sealed interface LessonFriendsState {
    /** The opt-in is off: the block is not shown at all. */
    data object Disabled : LessonFriendsState
    data object Loading : LessonFriendsState
    data class Content(val friends: List<UserSummary>) : LessonFriendsState
    data class Error(val error: AppError) : LessonFriendsState
}
