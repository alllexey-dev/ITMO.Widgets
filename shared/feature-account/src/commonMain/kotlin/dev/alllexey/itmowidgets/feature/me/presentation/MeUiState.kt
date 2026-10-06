package dev.alllexey.itmowidgets.feature.me.presentation

import dev.alllexey.itmowidgets.core.model.UserSummary
import dev.alllexey.itmowidgets.core.session.CurrentUser

/** What the social card can say about friends before or after Backend answered. */
sealed interface MeFriendsSummary {
    data object Disabled : MeFriendsSummary
    data object Loading : MeFriendsSummary
    data object Error : MeFriendsSummary
    data class Content(val friends: Int, val incomingRequests: Int) : MeFriendsSummary
}

data class MeUiState(
    val user: CurrentUser? = null,
    /** Backend's view of the same account; carries the study group. */
    val backendUser: UserSummary? = null,
    val friends: MeFriendsSummary = MeFriendsSummary.Loading,
    val signOutInProgress: Boolean = false,
    /** Sign-in to the web version goes through Backend, so it needs the ITMO.Widgets connection. */
    val webLoginAvailable: Boolean = false
)
