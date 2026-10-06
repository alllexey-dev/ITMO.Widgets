package dev.alllexey.itmowidgets.feature.schedule.ui.list

import androidx.compose.runtime.Immutable
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.sport.PendingSportBooking
import dev.alllexey.itmowidgets.feature.schedule.domain.model.Lesson
import dev.alllexey.itmowidgets.feature.schedule.presentation.ScheduleDayUi
import dev.alllexey.itmowidgets.feature.schedule.presentation.ScheduleUiState
import dev.alllexey.itmowidgets.feature.schedule.presentation.SelectedUser
import dev.alllexey.itmowidgets.feature.schedule.presentation.buildScheduleListUi
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone

/** What [ScheduleScreen] shows; the days are already resolved at one academic instant. */
@Immutable
data class ScheduleScreenState(
    val body: ScheduleScreenBody,
    /** Another user's schedule: the card above the list says whose it is. */
    val selectedUser: SelectedUser?,
    /** The friends button: the own schedule tab offers it while no friend is selected. */
    val canPickFriend: Boolean,
    /** The pull indicator over the list. */
    val refreshing: Boolean,
)

sealed interface ScheduleScreenBody {
    /** A first load without a cache: placeholder cards. */
    data object Loading : ScheduleScreenBody

    data class Days(val days: List<ScheduleDayUi>) : ScheduleScreenBody

    data object Empty : ScheduleScreenBody

    data class Failed(val error: AppError) : ScheduleScreenBody
}

/** What the screen asks its host for; navigation and loading stay outside. */
@Immutable
class ScheduleScreenActions(
    val onRefresh: () -> Unit = {},
    val onRetry: () -> Unit = {},
    /** The list came within three days of its end. */
    val onLoadMore: () -> Unit = {},
    val onLessonClick: (Lesson, LocalDate) -> Unit = { _, _ -> },
    val onPendingClick: (PendingSportBooking) -> Unit = {},
    val onPickFriend: () -> Unit = {},
    val onClearFriend: () -> Unit = {},
)

/**
 * [state] at the academic instant [now]: the content's days as [buildScheduleListUi] resolves them, the pull
 * indicator while more days load, the friends button on the [ownTab] without a selected friend.
 */
fun scheduleScreenState(
    state: ScheduleUiState,
    now: LocalDateTime,
    timeZone: TimeZone,
    ownTab: Boolean,
): ScheduleScreenState = ScheduleScreenState(
    body = when (state) {
        is ScheduleUiState.Loading -> ScheduleScreenBody.Loading
        is ScheduleUiState.Content -> ScheduleScreenBody.Days(buildScheduleListUi(state.displayDays, now, timeZone))
        is ScheduleUiState.Empty -> ScheduleScreenBody.Empty
        is ScheduleUiState.Error -> ScheduleScreenBody.Failed(state.error)
    },
    selectedUser = state.selectedUser,
    canPickFriend = ownTab && state.selectedUser == null,
    refreshing = state is ScheduleUiState.Content && state.loadingMore,
)
