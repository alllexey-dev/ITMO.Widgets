package dev.alllexey.itmowidgets.app

import dev.alllexey.itmowidgets.BuildConfig
import dev.alllexey.itmowidgets.core.friend.FriendListState
import dev.alllexey.itmowidgets.core.friend.FriendRepository
import dev.alllexey.itmowidgets.core.model.RelationshipState
import dev.alllexey.itmowidgets.core.model.UserGroup
import dev.alllexey.itmowidgets.core.model.UserProfile
import dev.alllexey.itmowidgets.core.model.UserSharing
import dev.alllexey.itmowidgets.core.model.UserSummary
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.social.PeopleSearchPage
import dev.alllexey.itmowidgets.core.social.PeopleSearchRepository
import dev.alllexey.itmowidgets.core.social.PersonSearchResult
import dev.alllexey.itmowidgets.feature.friendselector.domain.FriendSelectionHistory
import kotlinx.coroutines.flow.MutableStateFlow

/** Shared only by the isolated debug host so recreation cannot replace a test's history or sources. */
class FriendSelectorFixture {
    init { check(BuildConfig.DEBUG) }

    val friends = listOf("Александра", "Борис", "Виктория", "Григорий", "Дарья", "Евгений", "Жанна", "Захар").mapIndexed { index, name ->
        UserSummary(100001 + index, "$name Константинович Оченьдлиннаяфамилия", null,
            listOf(UserGroup("TEST", 2, "ТЕСТ")), UserSharing(true, true))
    }
    val friendState = MutableStateFlow<FriendListState>(FriendListState.Content(friends))
    val currentUser = MutableStateFlow<UserSummary?>(null)
    var recentIsus = friends.take(5).map(UserSummary::isu)
    val recorded = mutableListOf<Int>()
    val results = mutableListOf<Int>()

    val repository = object : FriendRepository {
        override fun observeFriendList() = friendState
        override fun observeCurrentUser() = currentUser
        override suspend fun refreshFriendList() = Unit
        override val currentFriends: List<UserSummary>?
            get() = (friendState.value as? FriendListState.Content)?.friends
    }
    val history = object : FriendSelectionHistory {
        override suspend fun getRecentIsu() = recentIsus
        override suspend fun record(isu: Int) {
            recorded += isu
            recentIsus = (listOf(isu) + recentIsus).distinct().take(5)
        }
    }
    val search = object : PeopleSearchRepository {
        override suspend fun search(query: String, offset: Int): AppResult<PeopleSearchPage> =
            AppResult.Success(PeopleSearchPage(friends.map { friend ->
                PersonSearchResult(friend.isu, friend.name, null, UserProfile(friend, RelationshipState.NONE))
            }, friends.size, null))
    }
}
