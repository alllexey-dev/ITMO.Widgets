package dev.alllexey.itmowidgets.client.users

import dev.alllexey.itmowidgets.client.common.UserData
import dev.alllexey.itmowidgets.client.common.UserProfile
import dev.alllexey.itmowidgets.client.http.BackendBody
import dev.alllexey.itmowidgets.client.http.BackendHttp
import dev.alllexey.itmowidgets.client.http.BackendRoute
import dev.alllexey.itmowidgets.client.http.jsonBody
import io.ktor.http.HttpMethod
import kotlinx.serialization.builtins.ListSerializer
import kotlin.uuid.Uuid

internal class KtorUsersApi(private val http: BackendHttp) : UsersApi {

    override suspend fun userProfile(isu: Int): UserProfile =
        http.call(route(HttpMethod.Get, isu.toString()), UserProfile.serializer())

    override suspend fun userFriends(isu: Int): List<UserProfile> =
        http.call(route(HttpMethod.Get, isu.toString(), "friends"), ListSerializer(UserProfile.serializer()))

    override suspend fun lookupUsers(request: UserLookupRequest): UserLookupResponse =
        http.call(route(HttpMethod.Post, "lookup", body = jsonBody(request)), UserLookupResponse.serializer())

    override suspend fun myPrivacySettings(): UserPrivacySettings =
        http.call(route(HttpMethod.Get, "me", "privacy"), UserPrivacySettings.serializer())

    override suspend fun updateMyPrivacySettings(settings: UserPrivacySettings): UserPrivacySettings =
        http.call(route(HttpMethod.Put, "me", "privacy", body = jsonBody(settings)), UserPrivacySettings.serializer())

    override suspend fun updateIdTokenData(request: IdTokenRequest) =
        http.callUnit(route(HttpMethod.Put, "me", "id-token", body = jsonBody(request)))

    override suspend fun myUserData(): UserData =
        http.call(route(HttpMethod.Get, "me", "data"), UserData.serializer())

    override suspend fun webLoginPreview(code: String): WebLoginPreview =
        http.call(route(HttpMethod.Get, "me", "web-login", code), WebLoginPreview.serializer())

    override suspend fun approveWebLogin(challengeId: Uuid) =
        http.callUnit(route(HttpMethod.Post, "me", "web-login", challengeId.toHexDashString(), "approve"))

    private fun route(method: HttpMethod, vararg segments: String, body: BackendBody? = null) =
        BackendRoute(method, listOf("api", "users") + segments, body = body)
}
