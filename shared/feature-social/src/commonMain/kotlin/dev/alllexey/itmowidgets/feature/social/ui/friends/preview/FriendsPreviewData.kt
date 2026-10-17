package dev.alllexey.itmowidgets.feature.social.ui.friends.preview

import dev.alllexey.itmowidgets.core.model.UserGroup
import dev.alllexey.itmowidgets.core.model.UserSharing
import dev.alllexey.itmowidgets.core.model.UserSummary
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.feature.social.presentation.FriendsEmpty
import dev.alllexey.itmowidgets.feature.social.presentation.FriendsTab
import dev.alllexey.itmowidgets.feature.social.presentation.FriendsUiState
import dev.alllexey.itmowidgets.feature.social.presentation.UserAction
import dev.alllexey.itmowidgets.feature.social.presentation.UserListItem
import dev.alllexey.itmowidgets.feature.social.presentation.UserRowUi
import dev.alllexey.itmowidgets.feature.social.presentation.subtitleText
import dev.alllexey.itmowidgets.shared.core.CoreStrings
import dev.alllexey.itmowidgets.shared.feature.social.SocialStrings

/** Synthetic people of the friends previews: friends and requests, as `FriendsViewModel` maps them. */
internal object FriendsPreviewData {

    /** The friends tab with one incoming request behind the badge. */
    fun friends(incomingCount: Int = 1, busyIsu: Int? = null, refreshing: Boolean = false) = FriendsUiState.Content(
        tab = FriendsTab.FRIENDS,
        items = FRIENDS.map { UserListItem.User(it.row(secondary = UserAction.REMOVE, busyIsu = busyIsu)) },
        incomingCount = incomingCount,
        refreshing = refreshing,
    )

    /** The requests tab: incoming over outgoing, each under its section header. */
    fun requests(busyIsu: Int? = null) = FriendsUiState.Content(
        tab = FriendsTab.REQUESTS,
        items = buildList {
            add(UserListItem.Header(UiText.Res(SocialStrings.friends_section_incoming)))
            INCOMING.forEach {
                add(UserListItem.User(it.row(UserAction.ACCEPT, UserAction.REJECT, busyIsu, it.sent)))
            }
            add(UserListItem.Header(UiText.Res(SocialStrings.friends_section_outgoing)))
            OUTGOING.forEach { add(UserListItem.User(it.row(secondary = UserAction.CANCEL, busyIsu = busyIsu, status = it.sent))) }
        },
        incomingCount = INCOMING.size,
        refreshing = false,
    )

    fun empty(tab: FriendsTab) = FriendsUiState.Content(
        tab = tab,
        items = emptyList(),
        incomingCount = 0,
        refreshing = false,
        empty = if (tab == FriendsTab.FRIENDS) FriendsEmpty.NO_FRIENDS else FriendsEmpty.NO_REQUESTS,
    )

    const val BUSY_ISU = 200004

    /** [sent] is when a request was sent, as the requests tab says it. */
    private class Person(val isu: Int, val name: String, val group: String, val sent: UiText? = null) {
        fun row(primary: UserAction? = null, secondary: UserAction? = null, busyIsu: Int? = null, status: UiText? = null): UserRowUi {
            val summary = UserSummary(isu, name, null, listOf(UserGroup(group, 2, "ФИТиП")), UserSharing(true, true, true))
            return UserRowUi(
                isu = isu,
                name = name,
                pictureUrl = null,
                subtitle = summary.subtitleText(),
                status = status,
                primary = primary,
                secondary = secondary,
                busy = isu == busyIsu,
            )
        }
    }

    private val FRIENDS = listOf(
        Person(200001, "Александра Константиновна Константинопольская-Преображенская", "M3205"),
        Person(200002, "Соколов Артём Игоревич", "M3234"),
        Person(200003, "Иванова Дарья Сергеевна", "M3205"),
    )
    private val INCOMING = listOf(
        Person(200004, "Григорьев Евгений Владиславович", "R3135", UiText.Dynamic("12:05")),
        Person(200005, "Преображенская Евгения Владиславовна", "M3207", UiText.Res(CoreStrings.event_moment_yesterday)),
    )
    private val OUTGOING = listOf(Person(200006, "Захаров Борис Константинович", "P3110", UiText.Dynamic("28 сент.")))
}
