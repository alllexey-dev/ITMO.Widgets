package dev.alllexey.itmowidgets.feature.social.presentation

import dev.alllexey.itmowidgets.core.result.AppError

sealed interface UserFriendsUiState {
    data object Loading : UserFriendsUiState

    /** Custom services are off: nothing was requested, and the screen offers the settings. */
    data object Disabled : UserFriendsUiState

    /** The owner does not share their friends with the viewer (403): no list and nothing to retry. */
    data object Hidden : UserFriendsUiState
    data class Error(val error: AppError) : UserFriendsUiState

    /** An empty [items] is the owner's empty list, which the screen offers to refresh. */
    data class Content(val items: List<UserListItem>, val refreshing: Boolean = false) : UserFriendsUiState
}

sealed interface UserFriendsEvent {
    /** A refresh failed while the last list stays on screen. */
    data class RefreshFailed(val error: AppError) : UserFriendsEvent
}
