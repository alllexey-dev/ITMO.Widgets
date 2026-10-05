package dev.alllexey.itmowidgets.feature.social.presentation

import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.text.UiText

enum class FriendsTab { FRIENDS, REQUESTS }

/** What an empty tab says: no friends yet, with a way to find people, or no requests either way. */
enum class FriendsEmpty { NO_FRIENDS, NO_REQUESTS }

sealed interface FriendsUiState {
    data object Loading : FriendsUiState

    /** Custom services are off: nothing was requested, and the screen offers the settings. */
    data object Disabled : FriendsUiState
    data class Error(val error: AppError) : FriendsUiState
    data class Content(
        val tab: FriendsTab,
        val items: List<UserListItem>,
        val incomingCount: Int,
        val refreshing: Boolean,
        /** Set when the selected tab has nothing to list. */
        val empty: FriendsEmpty? = null
    ) : FriendsUiState
}

sealed interface FriendsEvent {
    data class ActionFailed(val error: AppError) : FriendsEvent
    data class ConfirmRemove(val isu: Int, val name: UiText) : FriendsEvent
}
