package dev.alllexey.itmowidgets.feature.social.data

import dev.alllexey.itmowidgets.client.AccessTokenSource
import dev.alllexey.itmowidgets.client.BackendClient
import dev.alllexey.itmowidgets.client.common.UserData
import dev.alllexey.itmowidgets.client.common.UserProfile
import dev.alllexey.itmowidgets.client.friends.FriendsApi
import dev.alllexey.itmowidgets.client.users.IdTokenRequest
import dev.alllexey.itmowidgets.client.users.UserLookupRequest
import dev.alllexey.itmowidgets.client.users.UserLookupResponse
import dev.alllexey.itmowidgets.client.users.UserPrivacySettings
import dev.alllexey.itmowidgets.client.users.UsersApi
import dev.alllexey.itmowidgets.client.users.WebLoginPreview
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import kotlin.uuid.Uuid
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The Core 2.0 client over one MockEngine with the stored access token, as the app builds it from the MyItmoApi
 * 2.x session. [requests] records every request; MockEngine may answer the repository's parallel calls on several
 * threads, so recording takes a lock.
 */
internal class BackendHarness(
    private val backend: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
) {
    private val lock = Mutex()
    private val recorded = mutableListOf<HttpRequestData>()
    val requests: List<HttpRequestData> get() = recorded.toList()

    private val engine = MockEngine { request ->
        lock.withLock { recorded += request }
        backend(request)
    }

    val client = BackendClient(BACKEND_URL, AccessTokenSource { STORED_BACKEND_ACCESS }, engine)

    companion object {
        const val BACKEND_URL = "https://backend.test"
        const val STORED_BACKEND_ACCESS = "stored-access"
    }
}

/** Core 2.0's users area that fails the test on any call: the demo session must not reach Backend. */
internal object UnreachableUsers : UsersApi {
    override suspend fun userProfile(isu: Int): UserProfile = unreachable("userProfile")
    override suspend fun userFriends(isu: Int): List<UserProfile> = unreachable("userFriends")
    override suspend fun lookupUsers(request: UserLookupRequest): UserLookupResponse = unreachable("lookupUsers")
    override suspend fun myPrivacySettings(): UserPrivacySettings = unreachable("myPrivacySettings")
    override suspend fun updateMyPrivacySettings(settings: UserPrivacySettings): UserPrivacySettings =
        unreachable("updateMyPrivacySettings")
    override suspend fun updateIdTokenData(request: IdTokenRequest): Unit = unreachable("updateIdTokenData")
    override suspend fun myUserData(): UserData = unreachable("myUserData")
    override suspend fun webLoginPreview(code: String): WebLoginPreview = unreachable("webLoginPreview")
    override suspend fun approveWebLogin(challengeId: Uuid): Unit = unreachable("approveWebLogin")
}

/** Core 2.0's friends area that fails the test on any call. */
internal object UnreachableFriends : FriendsApi {
    override suspend fun sendFriendRequest(isu: Int): UserProfile = unreachable("sendFriendRequest")
    override suspend fun acceptFriendRequest(isu: Int): UserProfile = unreachable("acceptFriendRequest")
    override suspend fun rejectFriendRequest(isu: Int): UserProfile = unreachable("rejectFriendRequest")
    override suspend fun cancelFriendRequest(isu: Int): UserProfile = unreachable("cancelFriendRequest")
    override suspend fun removeFriend(isu: Int): UserProfile = unreachable("removeFriend")
    override suspend fun friends(): List<UserProfile> = unreachable("friends")
    override suspend fun incomingFriendRequests(): List<UserProfile> = unreachable("incomingFriendRequests")
    override suspend fun outgoingFriendRequests(): List<UserProfile> = unreachable("outgoingFriendRequests")
}

private fun unreachable(call: String): Nothing = throw AssertionError("The demo session called Backend: $call")
