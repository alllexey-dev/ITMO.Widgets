package dev.alllexey.itmowidgets.feature.me.presentation

import dev.alllexey.itmowidgets.core.connections.DevicesSummary
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.shared.core.CorePlurals
import dev.alllexey.itmowidgets.shared.feature.account.AccountStrings

/** What the friends row can say before or after Backend answered. */
sealed interface MeFriendsSummary {
    /** No connection to ITMO.Widgets: the hub shows the gate in place of the people rows. */
    data object Disabled : MeFriendsSummary
    data object Loading : MeFriendsSummary
    data object Error : MeFriendsSummary
    data class Content(val friends: Int, val incomingRequests: Int) : MeFriendsSummary
}

/**
 * The hub's identity card: the name (the placeholder until either source knows it), the avatar letters from
 * ITMO.ID's name parts, the study group line from Backend and the ISU, which [shareable] needs.
 */
data class MeHeader(
    val name: UiText = UiText.Res(AccountStrings.me_unknown_user),
    val initials: String? = null,
    val pictureUrl: String? = null,
    val group: UiText? = null,
    val isu: Int? = null,
) {
    val shareable: Boolean get() = isu != null && isu > 0
}

/**
 * The Профиль tab (docs/features/me.md): the header, the people rows or their gate, the app rows, the links and the
 * sign-out. Every text is built here, so the Compose and SwiftUI views only lay it out.
 */
data class MeUiState(
    val header: MeHeader = MeHeader(),
    val friends: MeFriendsSummary = MeFriendsSummary.Loading,
    val signOutInProgress: Boolean = false,
    /** The «Устройства» row: its count and a client's sign-in that needs the user. */
    val devices: DevicesSummary = DevicesSummary(count = null, warning = null),
) {
    /** `3 друга, 1 заявка`; null while there is nothing to say. */
    val friendsSubtitle: UiText?
        get() = when (friends) {
            MeFriendsSummary.Loading -> UiText.Res(AccountStrings.me_friends_loading)
            MeFriendsSummary.Error -> UiText.Res(AccountStrings.me_friends_error)
            MeFriendsSummary.Disabled -> null
            is MeFriendsSummary.Content -> when {
                friends.friends == 0 && friends.incomingRequests == 0 -> UiText.Res(AccountStrings.me_friends_none)
                friends.incomingRequests == 0 -> friendsCount(friends.friends)
                friends.friends == 0 -> requestsCount(friends.incomingRequests)
                else -> UiText.Joined(listOf(friendsCount(friends.friends), requestsCount(friends.incomingRequests)), ", ")
            }
        }

    /** The badge on the friends row: incoming requests. */
    val incomingRequests: Int get() = (friends as? MeFriendsSummary.Content)?.incomingRequests ?: 0
}

private fun friendsCount(count: Int) = UiText.Plural(CorePlurals.friends_count, count, listOf(count))

private fun requestsCount(count: Int) = UiText.Plural(CorePlurals.friend_requests_count, count, listOf(count))
