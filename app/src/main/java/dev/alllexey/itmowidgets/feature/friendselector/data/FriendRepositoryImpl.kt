package dev.alllexey.itmowidgets.feature.friendselector.data

import dev.alllexey.itmowidgets.core.friend.FriendListState
import dev.alllexey.itmowidgets.core.friend.FriendRepository
import dev.alllexey.itmowidgets.core.model.UserProfile
import dev.alllexey.itmowidgets.core.model.UserSummary
import dev.alllexey.itmowidgets.core.social.SocialRepository
import dev.alllexey.itmowidgets.core.social.SocialState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/** The picker's narrow view of [SocialRepository]: friends as plain identities. */
class FriendRepositoryImpl @Inject constructor(
    private val social: SocialRepository
) : FriendRepository {

    override fun observeFriendList(): Flow<FriendListState> {
        return social.observeFriends().map { state ->
            when (state) {
                SocialState.Loading -> FriendListState.Loading
                SocialState.Disabled -> FriendListState.Disabled
                is SocialState.Content -> FriendListState.Content(state.value.map(UserProfile::user))
                is SocialState.Error -> FriendListState.Error(state.error)
            }
        }
    }

    override fun observeCurrentUser(): Flow<UserSummary?> = social.observeCurrentUser()

    override val currentFriends: List<UserSummary>?
        get() = social.currentFriends?.map(UserProfile::user)

    override suspend fun refreshFriendList() = social.refresh()
}
