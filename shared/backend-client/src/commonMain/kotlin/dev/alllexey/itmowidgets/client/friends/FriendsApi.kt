package dev.alllexey.itmowidgets.client.friends

import dev.alllexey.itmowidgets.client.common.UserProfile
import dev.alllexey.itmowidgets.client.error.BackendException
import kotlin.coroutines.cancellation.CancellationException

/**
 * Friendships (routes under `/api/friends`): the viewer's friends, pending requests and their transitions.
 * Semantics are in Backend's `friendships.md` contract.
 *
 * Every action takes the other user's ISU, sends no body and returns that user's updated [UserProfile] with fresh
 * capabilities. Backend's transition table: a crossed request accepts at once, an impossible transition is 409, a
 * self or non-positive ISU is 400 and an unregistered user 404. Every listed profile is relative to the viewer.
 */
interface FriendsApi {

    /** `POST /api/friends/{isu}/request`; accepts at once when [isu] already sent the viewer a request. */
    @Throws(BackendException::class, CancellationException::class)
    suspend fun sendFriendRequest(isu: Int): UserProfile

    /** `POST /api/friends/{isu}/accept`. */
    @Throws(BackendException::class, CancellationException::class)
    suspend fun acceptFriendRequest(isu: Int): UserProfile

    /** `POST /api/friends/{isu}/reject`; never a block. */
    @Throws(BackendException::class, CancellationException::class)
    suspend fun rejectFriendRequest(isu: Int): UserProfile

    /** `POST /api/friends/{isu}/cancel`: withdraws the viewer's own request. */
    @Throws(BackendException::class, CancellationException::class)
    suspend fun cancelFriendRequest(isu: Int): UserProfile

    /** `DELETE /api/friends/{isu}`. */
    @Throws(BackendException::class, CancellationException::class)
    suspend fun removeFriend(isu: Int): UserProfile

    /** `GET /api/friends`: accepted friends. */
    @Throws(BackendException::class, CancellationException::class)
    suspend fun friends(): List<UserProfile>

    /** `GET /api/friends/requests/incoming`. */
    @Throws(BackendException::class, CancellationException::class)
    suspend fun incomingFriendRequests(): List<UserProfile>

    /** `GET /api/friends/requests/outgoing`. */
    @Throws(BackendException::class, CancellationException::class)
    suspend fun outgoingFriendRequests(): List<UserProfile>
}
