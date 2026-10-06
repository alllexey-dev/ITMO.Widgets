package dev.alllexey.itmowidgets.feature.social.ui.list.preview

import dev.alllexey.itmowidgets.core.model.RelationshipState
import dev.alllexey.itmowidgets.core.model.UserGroup
import dev.alllexey.itmowidgets.core.model.UserSharing
import dev.alllexey.itmowidgets.core.model.UserSummary
import dev.alllexey.itmowidgets.feature.social.presentation.UserAction
import dev.alllexey.itmowidgets.feature.social.presentation.UserListItem
import dev.alllexey.itmowidgets.feature.social.presentation.UserRowUi
import dev.alllexey.itmowidgets.feature.social.presentation.statusText
import dev.alllexey.itmowidgets.feature.social.presentation.subtitleText

/** Synthetic people of the social list previews: LC-1c's reference owner and friends, long names included. */
internal object UserListPreviewData {
    const val OWNER_NAME = "Александра Константиновна Константинопольская"

    /** Another user's friends as `UserFriendsViewModel` maps them: statuses, no actions. */
    val Friends: List<UserListItem> = listOf(
        user(200001, "Преображенская Евгения Владиславовна", RelationshipState.FRIENDS),
        user(200002, "Соколов Артём Игоревич", RelationshipState.NONE),
        user(200003, "Григорьев Евгений Владиславович", RelationshipState.OUTGOING),
        user(200004, "Иванова Дарья Сергеевна", RelationshipState.INCOMING),
    )

    /** Every part of the shared list: headers, both action kinds, a row in flight, a closed row and load-more. */
    val Mixed: List<UserListItem> = listOf(
        UserListItem.Header(RelationshipState.INCOMING.statusText()!!),
        user(
            200004,
            "Преображенская Евгения Владиславовна",
            RelationshipState.INCOMING,
            primary = UserAction.ACCEPT,
            secondary = UserAction.REJECT,
        ),
        user(200003, "Григорьев Евгений Владиславович", RelationshipState.OUTGOING, UserAction.CANCEL, busy = true),
        UserListItem.Header(RelationshipState.FRIENDS.statusText()!!),
        user(200002, "Соколов Артём Игоревич", RelationshipState.NONE, UserAction.ADD),
        UserListItem.User(
            row(200005, "", RelationshipState.NONE).copy(primary = UserAction.INVITE, opensProfile = false),
        ),
        UserListItem.LoadMore,
    )

    private fun user(
        isu: Int,
        name: String,
        relationship: RelationshipState,
        primary: UserAction? = null,
        secondary: UserAction? = null,
        busy: Boolean = false,
    ) = UserListItem.User(row(isu, name, relationship).copy(primary = primary, secondary = secondary, busy = busy))

    private fun row(isu: Int, name: String, relationship: RelationshipState): UserRowUi {
        val group = UserGroup("M3234", 2, "ФИТиП")
        val summary = UserSummary(isu, name, null, listOf(group), UserSharing(true, true, true))
        return UserRowUi(isu, name, null, summary.subtitleText(), relationship.statusText())
    }
}
