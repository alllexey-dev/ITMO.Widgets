package dev.alllexey.itmowidgets.client.moderation

import dev.alllexey.itmowidgets.client.http.BackendHttp
import dev.alllexey.itmowidgets.client.http.BackendRoute
import io.ktor.http.HttpMethod
import kotlinx.serialization.builtins.ListSerializer

internal class KtorModerationNoticesApi(private val http: BackendHttp) : ModerationNoticesApi {

    override suspend fun notices(): List<ModerationNotice> = http.call(
        BackendRoute(HttpMethod.Get, listOf("api", "users", "me", "moderation-notices")),
        ListSerializer(ModerationNotice.serializer()),
    )
}
