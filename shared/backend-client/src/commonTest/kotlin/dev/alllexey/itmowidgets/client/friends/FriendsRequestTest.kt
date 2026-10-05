package dev.alllexey.itmowidgets.client.friends

import dev.alllexey.itmowidgets.client.support.assertRequest
import dev.alllexey.itmowidgets.client.support.runSuspend
import io.ktor.http.HttpMethod
import kotlin.test.Test
import kotlin.test.assertEquals

/** The method, path, query and body of every [FriendsApi] call ([FriendsRouteCases]); no call sends a body. */
class FriendsRequestTest {

    @Test
    fun everyPublicFunctionHasOneCase() {
        assertEquals(8, FriendsRouteCases.all.size)
        assertEquals(FriendsRouteCases.all.size, FriendsRouteCases.all.map { it.name }.toSet().size)
    }

    @Test
    fun sendFriendRequest() = runSuspend {
        FriendsRouteCases.sendFriendRequest.assertRequest(HttpMethod.Post, "/api/friends/123456/request")
    }

    @Test
    fun acceptFriendRequest() = runSuspend {
        FriendsRouteCases.acceptFriendRequest.assertRequest(HttpMethod.Post, "/api/friends/123456/accept")
    }

    @Test
    fun rejectFriendRequest() = runSuspend {
        FriendsRouteCases.rejectFriendRequest.assertRequest(HttpMethod.Post, "/api/friends/123456/reject")
    }

    @Test
    fun cancelFriendRequest() = runSuspend {
        FriendsRouteCases.cancelFriendRequest.assertRequest(HttpMethod.Post, "/api/friends/123456/cancel")
    }

    @Test
    fun removeFriend() = runSuspend {
        FriendsRouteCases.removeFriend.assertRequest(HttpMethod.Delete, "/api/friends/123456")
    }

    @Test
    fun friends() = runSuspend {
        FriendsRouteCases.friends.assertRequest(HttpMethod.Get, "/api/friends")
    }

    @Test
    fun incomingFriendRequests() = runSuspend {
        FriendsRouteCases.incomingFriendRequests.assertRequest(HttpMethod.Get, "/api/friends/requests/incoming")
    }

    @Test
    fun outgoingFriendRequests() = runSuspend {
        FriendsRouteCases.outgoingFriendRequests.assertRequest(HttpMethod.Get, "/api/friends/requests/outgoing")
    }
}
