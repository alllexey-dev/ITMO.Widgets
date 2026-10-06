package dev.alllexey.itmowidgets.feature.social.presentation

import dev.alllexey.itmowidgets.core.model.RelationshipState
import dev.alllexey.itmowidgets.core.model.UserSummary
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.shared.core.Res as CoreRes
import dev.alllexey.itmowidgets.shared.core.user_name_placeholder
import dev.alllexey.itmowidgets.shared.feature.social.Res
import dev.alllexey.itmowidgets.shared.feature.social.friend_picker_user_subtitle
import dev.alllexey.itmowidgets.shared.feature.social.user_status_friends
import dev.alllexey.itmowidgets.shared.feature.social.user_status_incoming
import dev.alllexey.itmowidgets.shared.feature.social.user_status_outgoing
import dev.alllexey.itmowidgets.shared.feature.social.user_subtitle_isu

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
) {
    val displayName: UiText get() = userDisplayName(name, isu)
}

sealed interface UserListItem {
    data class Header(val title: UiText) : UserListItem
    data class User(val row: UserRowUi) : UserListItem
    data object LoadMore : UserListItem
}

/** Backend sends an empty name until the owner's identity is published; a screen never shows it raw. */
fun userDisplayName(name: String, isu: Int): UiText =
    name.trim().takeIf { it.isNotEmpty() }?.let(UiText::Dynamic) ?: UiText.Res(CoreRes.string.user_name_placeholder, listOf(isu))

fun UserSummary.displayName(): UiText = userDisplayName(name, isu)

fun UserSummary.subtitleText(): UiText {
    if (groups.isEmpty()) return UiText.Res(Res.string.user_subtitle_isu, listOf(isu))
    val groupsText = if (groups.size <= 2) {
        groups.joinToString(" • ") { it.name }
    } else {
        "${groups.first().name} +${groups.size - 1}"
    }
    return UiText.Res(Res.string.friend_picker_user_subtitle, listOf(isu, groupsText))
}

fun RelationshipState.statusText(): UiText? = when (this) {
    RelationshipState.OUTGOING -> UiText.Res(Res.string.user_status_outgoing)
    RelationshipState.INCOMING -> UiText.Res(Res.string.user_status_incoming)
    RelationshipState.FRIENDS -> UiText.Res(Res.string.user_status_friends)
    RelationshipState.NONE, RelationshipState.BLOCKED -> null
}

/** The single action a stranger-list row offers for a relationship, if any. */
fun RelationshipState.primaryAction(): UserAction? = when (this) {
    RelationshipState.NONE -> UserAction.ADD
    RelationshipState.OUTGOING -> UserAction.CANCEL
    RelationshipState.INCOMING -> UserAction.ACCEPT
    RelationshipState.FRIENDS, RelationshipState.BLOCKED -> null
}
