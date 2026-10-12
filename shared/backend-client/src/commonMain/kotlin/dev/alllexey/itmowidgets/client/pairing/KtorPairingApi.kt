package dev.alllexey.itmowidgets.client.pairing

import dev.alllexey.itmowidgets.client.http.BackendBody
import dev.alllexey.itmowidgets.client.http.BackendHttp
import dev.alllexey.itmowidgets.client.http.BackendRoute
import dev.alllexey.itmowidgets.client.http.jsonBody
import io.ktor.http.HttpMethod
import kotlinx.serialization.builtins.ListSerializer
import kotlin.uuid.Uuid

internal class KtorPairingApi(private val http: BackendHttp) : PairingApi {

    override suspend fun openPairing(code: String): PairingPreview =
        http.call(route("pair", code, "open"), PairingPreview.serializer())

    override suspend fun approvePairing(challengeId: Uuid, request: ApprovePairingRequest) =
        http.callUnit(route("pair", challengeId.toHexDashString(), "approve", body = jsonBody(request)))

    override suspend fun rejectPairing(challengeId: Uuid) =
        http.callUnit(route("pair", challengeId.toHexDashString(), "reject"))

    override suspend fun connections(): List<ConnectionInfo> = http.call(
        BackendRoute(HttpMethod.Get, ME + "connections"),
        ListSerializer(ConnectionInfo.serializer()),
    )

    override suspend fun revokeConnection(id: Uuid) =
        http.callUnit(route("connections", id.toHexDashString(), "revoke"))

    private fun route(vararg segments: String, body: BackendBody? = null) =
        BackendRoute(HttpMethod.Post, ME + segments, body = body)

    private companion object {
        val ME = listOf("api", "users", "me")
    }
}
