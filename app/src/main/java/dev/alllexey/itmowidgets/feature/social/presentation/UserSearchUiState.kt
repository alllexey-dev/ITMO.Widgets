package dev.alllexey.itmowidgets.feature.social.presentation

import dev.alllexey.itmowidgets.core.result.AppError

sealed interface UserSearchUiState {
    /** Nothing typed yet. */
    data object Idle : UserSearchUiState
    data object Loading : UserSearchUiState

    /** The query found nobody. */
    data object Empty : UserSearchUiState
    data class Error(val error: AppError) : UserSearchUiState
    data class Content(
        val items: List<UserListItem>,
        val loadingMore: Boolean
    ) : UserSearchUiState
}

sealed interface UserSearchEvent {
    data class ActionFailed(val error: AppError) : UserSearchEvent
    data class Invite(val name: String) : UserSearchEvent
}
