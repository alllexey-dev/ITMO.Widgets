package dev.alllexey.itmowidgets.feature.schedule.presentation

import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.domain.model.schedule.DaySchedule

data class SelectedUser(
    val isu: Int,
    val name: String,
    val avatar: String?
)

sealed interface ScheduleUiState {

    val selectedUser: SelectedUser?

    data class Loading(
        override val selectedUser: SelectedUser?
    ) : ScheduleUiState

    data class Content(
        val schedule: List<DaySchedule>,
        val loadingMore: Boolean,
        override val selectedUser: SelectedUser?
    ) : ScheduleUiState

    data class Empty(
        override val selectedUser: SelectedUser?
    ) : ScheduleUiState

    data class Error(
        val error: AppError,
        override val selectedUser: SelectedUser?
    ) : ScheduleUiState
}

sealed interface ScheduleEvent {

    data class ShowError(val error: AppError) : ScheduleEvent
}
