package dev.alllexey.itmowidgets.client.friends

import dev.alllexey.itmowidgets.client.common.UserProfile
import dev.alllexey.itmowidgets.client.http.BackendHttp
import dev.alllexey.itmowidgets.client.http.BackendRoute
import io.ktor.http.HttpMethod
import kotlinx.serialization.builtins.ListSerializer

internal class KtorFriendsApi(private val http: BackendHttp) : FriendsApi {

    override suspend fun sendFriendRequest(isu: Int): UserProfile = action(HttpMethod.Post, isu, "request")

    override suspend fun acceptFriendRequest(isu: Int): UserProfile = action(HttpMethod.Post, isu, "accept")

    override suspend fun rejectFriendRequest(isu: Int): UserProfile = action(HttpMethod.Post, isu, "reject")

    override suspend fun cancelFriendRequest(isu: Int): UserProfile = action(HttpMethod.Post, isu, "cancel")

    override suspend fun removeFriend(isu: Int): UserProfile = action(HttpMethod.Delete, isu)

    override suspend fun friends(): List<UserProfile> = list()

    override suspend fun incomingFriendRequests(): List<UserProfile> = list("requests", "incoming")

    override suspend fun outgoingFriendRequests(): List<UserProfile> = list("requests", "outgoing")

    private suspend fun action(method: HttpMethod, isu: Int, vararg suffix: String): UserProfile =
        http.call(route(method, isu.toString(), *suffix), UserProfile.serializer())

    private suspend fun list(vararg segments: String): List<UserProfile> =
        http.call(route(HttpMethod.Get, *segments), ListSerializer(UserProfile.serializer()))

    private fun route(method: HttpMethod, vararg segments: String) =
        BackendRoute(method, listOf("api", "friends") + segments)
}
