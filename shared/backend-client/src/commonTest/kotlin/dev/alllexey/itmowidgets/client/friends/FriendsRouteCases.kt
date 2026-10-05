package dev.alllexey.itmowidgets.client.friends

import dev.alllexey.itmowidgets.client.support.RouteCase
import dev.alllexey.itmowidgets.client.users.SyntheticUsers

/** One [RouteCase] per public function of [FriendsApi], with synthetic arguments. */
object FriendsRouteCases {
    val sendFriendRequest = RouteCase("sendFriendRequest") { friends.sendFriendRequest(SyntheticUsers.ISU) }
    val acceptFriendRequest = RouteCase("acceptFriendRequest") { friends.acceptFriendRequest(SyntheticUsers.ISU) }
    val rejectFriendRequest = RouteCase("rejectFriendRequest") { friends.rejectFriendRequest(SyntheticUsers.ISU) }
    val cancelFriendRequest = RouteCase("cancelFriendRequest") { friends.cancelFriendRequest(SyntheticUsers.ISU) }
    val removeFriend = RouteCase("removeFriend") { friends.removeFriend(SyntheticUsers.ISU) }
    val friends = RouteCase("friends") { this.friends.friends() }
    val incomingFriendRequests = RouteCase("incomingFriendRequests") { friends.incomingFriendRequests() }
    val outgoingFriendRequests = RouteCase("outgoingFriendRequests") { friends.outgoingFriendRequests() }

    val all: List<RouteCase> = listOf(
        sendFriendRequest,
        acceptFriendRequest,
        rejectFriendRequest,
        cancelFriendRequest,
        removeFriend,
        friends,
        incomingFriendRequests,
        outgoingFriendRequests,
    )
}
