package dev.alllexey.itmowidgets.feature.social.presentation

import dev.alllexey.itmowidgets.core.model.UserSummary
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.shared.core.CorePlurals

/**
 * Друзья (docs/features/social.md): people search on top, then the viewer's requests and friends. While [query] is
 * blank [search] is null and the lists show; a typed query shows [search] in their place.
 */
data class FriendsUiState(
    val query: String = "",
    val lists: FriendLists = FriendLists.Loading,
    val search: PeopleSearchResults? = null,
    /** The viewer as Backend sees them: `Поделиться профилем` needs the name and the ISU. */
    val viewer: UserSummary? = null,
)

sealed interface FriendLists {
    data object Loading : FriendLists

    /** Custom services are off: nothing was requested, and the screen shows the gate. */
    data object Disabled : FriendLists
    data class Error(val error: AppError) : FriendLists

    /**
     * Incoming requests answer inline (`Принять`, `Отклонить`), sent ones offer `Отменить`, friends open their card,
     * where removing them lives.
     */
    data class Content(
        val incoming: List<UserRowUi>,
        val outgoing: List<UserRowUi>,
        val friends: List<UserRowUi>,
        val refreshing: Boolean = false,
    ) : FriendLists {
        val isEmpty: Boolean get() = incoming.isEmpty() && outgoing.isEmpty() && friends.isEmpty()
        val hasRequests: Boolean get() = incoming.isNotEmpty() || outgoing.isNotEmpty()

        /** The header's `3 друга, 1 заявка`; null for an empty screen. */
        val subtitle: UiText?
            get() = listOfNotNull(
                friends.size.takeIf { it > 0 }?.let { UiText.Plural(CorePlurals.friends_count, it, listOf(it)) },
                incoming.size.takeIf { it > 0 }?.let { UiText.Plural(CorePlurals.friend_requests_count, it, listOf(it)) },
            ).takeIf { it.isNotEmpty() }?.let { UiText.Joined(it, ", ") }
    }
}

/** People search through My ITMO; Backend marks who uses ITMO.Widgets. */
sealed interface PeopleSearchResults {
    data object Loading : PeopleSearchResults

    /** The query found nobody. */
    data object Empty : PeopleSearchResults
    data class Error(val error: AppError) : PeopleSearchResults

    /** [registered] users of ITMO.Widgets first, then [others]; [offersMore] is `Показать ещё`. */
    data class Content(
        val registered: List<UserRowUi>,
        val others: List<UserRowUi>,
        val offersMore: Boolean,
        val loadingMore: Boolean,
    ) : PeopleSearchResults
}

sealed interface FriendsEvent {
    data class ActionFailed(val error: AppError) : FriendsEvent

    /** `Пригласить` for [name], who has no ITMO.Widgets: the host shares the invitation. */
    data class Invite(val name: String) : FriendsEvent
}
