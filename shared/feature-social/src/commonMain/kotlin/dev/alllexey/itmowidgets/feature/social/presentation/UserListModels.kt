package dev.alllexey.itmowidgets.feature.social.presentation

import dev.alllexey.itmowidgets.core.model.RelationshipState
import dev.alllexey.itmowidgets.core.model.UserSummary
import dev.alllexey.itmowidgets.core.model.primaryGroup
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.shared.core.CoreStrings
import dev.alllexey.itmowidgets.shared.feature.social.SocialStrings

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
    val opensProfile: Boolean = true,
    /** ITMO.ID's name parts for the avatar's initials, when Backend has them. */
    val familyName: String? = null,
    val givenName: String? = null,
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
    name.trim().takeIf { it.isNotEmpty() }?.let(UiText::Dynamic)
        ?: UiText.Res(CoreStrings.user_name_placeholder, listOf(isu))

fun UserSummary.displayName(): UiText = userDisplayName(name, isu)

/** `M3206, 2 курс` from the current group, or `ИСУ 288104` without one. */
fun UserSummary.subtitleText(): UiText {
    val group = primaryGroup() ?: return UiText.Res(SocialStrings.user_subtitle_isu, listOf(isu))
    return UiText.Res(SocialStrings.person_group_course, listOf(group.name, group.course))
}

fun RelationshipState.statusText(): UiText? = when (this) {
    RelationshipState.OUTGOING -> UiText.Res(SocialStrings.user_status_outgoing)
    RelationshipState.INCOMING -> UiText.Res(SocialStrings.user_status_incoming)
    RelationshipState.FRIENDS -> UiText.Res(SocialStrings.user_status_friends)
    RelationshipState.NONE, RelationshipState.BLOCKED -> null
}

/** The single action a stranger-list row offers for a relationship, if any. */
fun RelationshipState.primaryAction(): UserAction? = when (this) {
    RelationshipState.NONE -> UserAction.ADD
    RelationshipState.OUTGOING -> UserAction.CANCEL
    RelationshipState.INCOMING -> UserAction.ACCEPT
    RelationshipState.FRIENDS, RelationshipState.BLOCKED -> null
}
