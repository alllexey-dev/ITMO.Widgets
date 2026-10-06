package dev.alllexey.itmowidgets.feature.friendselector.data

import dev.alllexey.itmowidgets.core.model.RelationshipState
import dev.alllexey.itmowidgets.core.result.LoadState
import dev.alllexey.itmowidgets.core.testing.FakeSocialRepository
import dev.alllexey.itmowidgets.core.testing.profile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest

class FriendRepositoryImplTest {

    private val social = FakeSocialRepository()
    private val friends = FriendRepositoryImpl(social)

    @Test
    fun theFriendListIsTheSocialListAsPlainIdentities() = runTest {
        assertEquals(LoadState.Loading, friends.observeFriendList().first())
        assertNull(friends.currentFriends)

        social.friends.value = LoadState.Content(listOf(profile(1, RelationshipState.FRIENDS), profile(2, RelationshipState.FRIENDS)))

        val list = (friends.observeFriendList().first() as LoadState.Content).value
        assertEquals(listOf(1, 2), list.map { it.isu })
        assertEquals(listOf(1, 2), friends.currentFriends?.map { it.isu })
    }

    @Test
    fun refreshingTheListRefreshesSocial() = runTest {
        friends.refreshFriendList()

        assertEquals(1, social.refreshes)
    }
}
