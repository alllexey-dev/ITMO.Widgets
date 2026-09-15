package dev.alllexey.itmowidgets.feature.social.presentation

import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.model.RelationshipState
import dev.alllexey.itmowidgets.core.model.UserSummary
import dev.alllexey.itmowidgets.core.text.UiText

enum class UserAction { ADD, ACCEPT, REJECT, CANCEL, REMOVE, INVITE }

/** One person in a social list: identity plus the actions the viewer can take now. */
data class UserRowUi(
    val isu: Int,
    val name: String,
    val pictureUrl: String?,
    val subtitle: UiText,
    val status: UiText? = null,
    val primary: UserAction? = null,
    val secondary: UserAction? = null,
    /** An action for this person is in flight; buttons are disabled meanwhile. */
    val busy: Boolean = false,
    val opensProfile: Boolean = true
)

sealed interface UserListItem {
    data class Header(val title: UiText) : UserListItem
    data class User(val row: UserRowUi) : UserListItem
    data object LoadMore : UserListItem
}

fun UserSummary.subtitleText(): UiText {
    if (groups.isEmpty()) return UiText.Resource(R.string.user_subtitle_isu, listOf(isu))
    val groupsText = if (groups.size <= 2) {
        groups.joinToString(" • ") { it.name }
    } else {
        "${groups.first().name} +${groups.size - 1}"
    }
    return UiText.Resource(R.string.friend_picker_user_subtitle, listOf(isu, groupsText))
}

fun RelationshipState.statusText(): UiText? = when (this) {
    RelationshipState.OUTGOING -> UiText.Resource(R.string.user_status_outgoing)
    RelationshipState.INCOMING -> UiText.Resource(R.string.user_status_incoming)
    RelationshipState.FRIENDS -> UiText.Resource(R.string.user_status_friends)
    RelationshipState.NONE, RelationshipState.BLOCKED -> null
}

/** The single action a stranger-list row offers for a relationship, if any. */
fun RelationshipState.primaryAction(): UserAction? = when (this) {
    RelationshipState.NONE -> UserAction.ADD
    RelationshipState.OUTGOING -> UserAction.CANCEL
    RelationshipState.INCOMING -> UserAction.ACCEPT
    RelationshipState.FRIENDS, RelationshipState.BLOCKED -> null
}
