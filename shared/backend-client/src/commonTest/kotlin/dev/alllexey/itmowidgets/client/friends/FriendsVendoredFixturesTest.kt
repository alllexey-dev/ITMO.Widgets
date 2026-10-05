package dev.alllexey.itmowidgets.client.friends

import dev.alllexey.itmowidgets.client.common.UserProfile
import dev.alllexey.itmowidgets.client.contract.ResponseClaim
import dev.alllexey.itmowidgets.client.contract.assertAreaClaims
import dev.alllexey.itmowidgets.client.support.runSuspend
import kotlinx.serialization.builtins.ListSerializer
import kotlin.test.Test

/**
 * Backend's vendored friends fixtures (`claims/friends.txt`): every answer decodes through [FriendsApi] and
 * re-encodes to the fixture's `data`. Arguments do not matter, the fixture is the answer.
 */
class FriendsVendoredFixturesTest {

    private val profile = UserProfile.serializer()
    private val profiles = ListSerializer(UserProfile.serializer())

    private val responses = listOf(
        ResponseClaim("http/friends/sendFriendRequest.json", profile) { friends.sendFriendRequest(ISU) },
        ResponseClaim("http/friends/acceptFriendRequest.json", profile) { friends.acceptFriendRequest(ISU) },
        ResponseClaim("http/friends/rejectFriendRequest.json", profile) { friends.rejectFriendRequest(ISU) },
        ResponseClaim("http/friends/cancelFriendRequest.json", profile) { friends.cancelFriendRequest(ISU) },
        ResponseClaim("http/friends/removeFriend.json", profile) { friends.removeFriend(ISU) },
        ResponseClaim("http/friends/friends.json", profiles) { friends.friends() },
        ResponseClaim("http/friends/incomingFriendRequests.json", profiles) { friends.incomingFriendRequests() },
        ResponseClaim("http/friends/outgoingFriendRequests.json", profiles) { friends.outgoingFriendRequests() },
    )

    @Test
    fun everyClaimDecodes() = runSuspend { assertAreaClaims("friends", responses, requests = emptyList()) }

    private companion object {
        const val ISU = 100002
    }
}
