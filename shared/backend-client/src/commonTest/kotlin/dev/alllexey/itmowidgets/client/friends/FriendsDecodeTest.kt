package dev.alllexey.itmowidgets.client.friends

import dev.alllexey.itmowidgets.client.BackendClient
import dev.alllexey.itmowidgets.client.common.RelationshipState
import dev.alllexey.itmowidgets.client.common.UserProfile
import dev.alllexey.itmowidgets.client.support.runSuspend
import dev.alllexey.itmowidgets.client.users.SyntheticUsers
import dev.alllexey.itmowidgets.client.users.SyntheticUsers.answering
import dev.alllexey.itmowidgets.client.users.SyntheticUsers.profile
import dev.alllexey.itmowidgets.client.users.SyntheticUsers.profileJson
import kotlin.test.Test
import kotlin.test.assertEquals

/** What every [FriendsApi] call returns for a synthetic Backend answer. */
class FriendsDecodeTest {

    private suspend fun action(relationship: String, call: suspend BackendClient.() -> UserProfile) {
        val result = answering(profileJson(relationship)).client.call()

        assertEquals(profile(RelationshipState.valueOf(relationship)), result)
    }

    private suspend fun list(call: suspend BackendClient.() -> List<UserProfile>) {
        val result = answering("[${profileJson("FRIENDS")},${profileJson("INCOMING")}]").client.call()

        assertEquals(listOf(profile(RelationshipState.FRIENDS), profile(RelationshipState.INCOMING)), result)
    }

    @Test
    fun sendFriendRequest() = runSuspend { action("OUTGOING") { friends.sendFriendRequest(SyntheticUsers.ISU) } }

    @Test
    fun acceptFriendRequest() = runSuspend { action("FRIENDS") { friends.acceptFriendRequest(SyntheticUsers.ISU) } }

    @Test
    fun rejectFriendRequest() = runSuspend { action("NONE") { friends.rejectFriendRequest(SyntheticUsers.ISU) } }

    @Test
    fun cancelFriendRequest() = runSuspend { action("NONE") { friends.cancelFriendRequest(SyntheticUsers.ISU) } }

    @Test
    fun removeFriend() = runSuspend { action("NONE") { friends.removeFriend(SyntheticUsers.ISU) } }

    @Test
    fun friends() = runSuspend { list { friends.friends() } }

    @Test
    fun incomingFriendRequests() = runSuspend { list { friends.incomingFriendRequests() } }

    @Test
    fun outgoingFriendRequests() = runSuspend { list { friends.outgoingFriendRequests() } }

    @Test
    fun emptyListsStayEmpty() = runSuspend {
        assertEquals(emptyList(), answering("[]").client.friends.friends())
    }
}
