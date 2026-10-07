package dev.alllexey.itmowidgets.feature.schedule.ui.details

import androidx.compose.runtime.Immutable
import dev.alllexey.itmowidgets.core.navigation.LessonDetailsArgs
import dev.alllexey.itmowidgets.feature.schedule.presentation.details.LessonDetailsUiState

/**
 * What [LessonDetailsContent] draws: the [lesson] the sheet was opened with, what [details] the view model added
 * (friends, the teacher's tone, the latest change), and whether the host can show the place on a map
 * ([mapAvailable]: a known building or at least the raw building text).
 */
@Immutable
data class LessonDetailsSheetState(
    val lesson: LessonDetailsArgs,
    val details: LessonDetailsUiState = LessonDetailsUiState(),
    val mapAvailable: Boolean = false,
)

/**
 * What the sheet asks of its host. The host performs every effect: the map, the meeting link, the profile of an ISU
 * (after closing the sheet), closing itself. [onRetryFriends] asks for the friends again.
 */
@Immutable
class LessonDetailsActions(
    val onMap: () -> Unit = {},
    val onLink: (url: String) -> Unit = {},
    val onProfile: (isu: Int) -> Unit = {},
    val onRetryFriends: () -> Unit = {},
    val onClose: () -> Unit = {},
)

/** Tags of the sheet's parts for host tests and instrumented flows. */
object LessonDetailsTestTags {
    const val SCROLL = "lesson_details_scroll"
    const val CHANGES = "lesson_details_changes"
    const val LINK = "lesson_details_link"
    const val NOTE = "lesson_details_note"
    const val FRIENDS = "lesson_details_friends"
    const val FRIENDS_PROGRESS = "lesson_details_friends_progress"
    const val FRIEND = "lesson_details_friend"
}
