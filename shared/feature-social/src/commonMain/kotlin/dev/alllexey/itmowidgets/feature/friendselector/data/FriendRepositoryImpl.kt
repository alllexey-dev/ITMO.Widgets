package dev.alllexey.itmowidgets.feature.friendselector.data

import dev.alllexey.itmowidgets.core.friend.FriendRepository
import dev.alllexey.itmowidgets.core.model.UserProfile
import dev.alllexey.itmowidgets.core.model.UserSummary
import dev.alllexey.itmowidgets.core.result.LoadState
import dev.alllexey.itmowidgets.core.result.map
import dev.alllexey.itmowidgets.core.social.SocialRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** The picker's narrow view of [SocialRepository]: friends as plain identities. */
class FriendRepositoryImpl(
    private val social: SocialRepository
) : FriendRepository {

    override fun observeFriendList(): Flow<LoadState<List<UserSummary>>> {
        return social.observeFriends().map { state -> state.map { friends -> friends.map(UserProfile::user) } }
    }

    override fun observeCurrentUser(): Flow<UserSummary?> = social.observeCurrentUser()

    override val currentFriends: List<UserSummary>?
        get() = social.currentFriends?.map(UserProfile::user)

    override suspend fun refreshFriendList() = social.refresh()
}
