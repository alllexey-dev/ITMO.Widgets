package dev.alllexey.itmowidgets.feature.friendselector.presentation

import dev.alllexey.itmowidgets.core.model.UserSummary
import dev.alllexey.itmowidgets.core.result.AppError

/** Whose schedules the list offers: mutual friends, or every ITMO.Widgets user found by name. */
enum class FriendSelectorScope { FRIENDS, ALL }

/** Registered people found by name; only these can have a viewable schedule. */
sealed interface PeopleResults {
    data object Idle : PeopleResults
    data object Loading : PeopleResults
    data class Content(val people: List<UserSummary>) : PeopleResults
    data class Error(val error: AppError) : PeopleResults
}

/**
 * The picker sheet.
 *
 * [recentFriends] are the chips after the own one, in the order fixed when the list first loaded; while the list is
 * loading or failed they keep the last order shown. [selectedIsu] marks the chosen chip and row (`null` is the own
 * schedule); before the list loads it is the ISU the sheet was opened with. [applyTarget] is what Apply sends: the
 * chosen person, or `null` for the own schedule. [currentUser] gives the own chip its avatar in every state.
 */
data class FriendSelectorUiState(
    val body: FriendSelectorBody = FriendSelectorBody.Loading,
    val scope: FriendSelectorScope = FriendSelectorScope.FRIENDS,
    val recentFriends: List<UserSummary> = emptyList(),
    val selectedIsu: Int? = null,
    val applyTarget: UserSummary? = null,
    val currentUser: UserSummary? = null
) {
    /** Apply and the scope toggle need a loaded friend list, even an empty one. */
    val canApply: Boolean
        get() = when (body) {
            FriendSelectorBody.Loading, FriendSelectorBody.Disabled, is FriendSelectorBody.Error -> false
            else -> true
        }

    val showsScope: Boolean get() = canApply
}

/** What fills the sheet under the search field. */
sealed interface FriendSelectorBody {

    /** The friend list is not known yet. */
    data object Loading : FriendSelectorBody

    /** Friends matching the filter, or people found by name. */
    data class Users(val users: List<UserSummary>) : FriendSelectorBody

    /** Friends exist, but none matches the filter. */
    data object NoMatches : FriendSelectorBody

    /** The wide scope waits for a name. */
    data object PeopleIdle : FriendSelectorBody

    data object PeopleLoading : FriendSelectorBody

    data object PeopleEmpty : FriendSelectorBody

    data class PeopleError(val error: AppError) : FriendSelectorBody

    /** The friend list loaded empty. */
    data object NoFriends : FriendSelectorBody

    /** Custom services are switched off. */
    data object Disabled : FriendSelectorBody

    data class Error(val error: AppError) : FriendSelectorBody
}

sealed interface FriendSelectorEvent {

    /** Apply was confirmed: show [target]'s schedule, or the own one when it is `null`. */
    data class Apply(val target: UserSummary?) : FriendSelectorEvent
}
