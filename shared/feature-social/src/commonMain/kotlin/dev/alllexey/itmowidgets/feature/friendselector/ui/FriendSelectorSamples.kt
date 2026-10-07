package dev.alllexey.itmowidgets.feature.friendselector.ui

import dev.alllexey.itmowidgets.core.model.UserGroup
import dev.alllexey.itmowidgets.core.model.UserSharing
import dev.alllexey.itmowidgets.core.model.UserSummary
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.feature.friendselector.presentation.FriendSelectorBody
import dev.alllexey.itmowidgets.feature.friendselector.presentation.FriendSelectorScope
import dev.alllexey.itmowidgets.feature.friendselector.presentation.FriendSelectorUiState

/**
 * Synthetic people for the picker's previews and host tests: the names of the debug `FriendSelectorFixture` and of
 * LC-1c's XML references, long so rows wrap and chips ellipsize.
 */
internal object FriendSelectorSamples {

    /** Declared first: the people below are built with it. */
    private val GROUP = UserGroup("M3205", 2, "ФИТиП")

    /** The recent chips after the own one, as the history of the references gave them. */
    const val RECENT = 5

    val friends: List<UserSummary> =
        listOf("Александра", "Борис", "Виктория", "Григорий", "Дарья", "Евгений", "Жанна", "Захар")
            .mapIndexed { index, name -> person(100001 + index, "$name Константинович Оченьдлиннаяфамилия") }

    /** Every other friend keeps their schedule closed. */
    val locked: List<UserSummary> = friends.mapIndexed { index, friend ->
        if (index % 2 == 1) friend.copy(sharing = UserSharing(sport = true, schedule = false)) else friend
    }

    /** People found by «Соколов» under «Все». */
    val people: List<UserSummary> = listOf(
        person(200011, "Соколов Артём Игоревич"),
        person(200012, "Соколова Александра Константиновна Константинопольская"),
        person(200013, "Соколов Евгений Владиславович"),
    )

    /** The signed-in user, so the own chip shows an avatar. */
    val me: UserSummary = person(300001, "Иванов Иван Иванович")

    const val PEOPLE_QUERY = "Соколов"
    const val NO_MATCH_QUERY = "Ъъъ"

    fun person(isu: Int, name: String, groups: List<UserGroup> = listOf(GROUP)): UserSummary =
        UserSummary(isu, name, null, groups, UserSharing(sport = true, schedule = true))

    /** A loaded friends scope over [friends], the chips from their first [RECENT] with an open schedule. */
    fun state(
        body: FriendSelectorBody,
        friends: List<UserSummary> = this.friends,
        scope: FriendSelectorScope = FriendSelectorScope.FRIENDS,
        selected: UserSummary? = null,
        currentUser: UserSummary? = null,
    ): FriendSelectorUiState = FriendSelectorUiState(
        body = body,
        scope = scope,
        recentFriends = friends.take(RECENT).filter { it.sharing.schedule },
        selectedIsu = selected?.isu,
        applyTarget = selected,
        currentUser = currentUser,
    )

    val networkError: AppError = AppError.Network

    /** Groups: none, two, and more than two. */
    val groupCases: List<UserSummary> = listOf(
        person(100101, "Без Группы", groups = emptyList()),
        person(100102, "Две Группы", groups = listOf(group("M3205"), group("M3206"))),
        person(100103, "Много Групп", groups = listOf(group("M3205"), group("M3206"), group("M3207"))),
    )

    private fun group(name: String) = UserGroup(name, 2, "ФИТиП")
}
