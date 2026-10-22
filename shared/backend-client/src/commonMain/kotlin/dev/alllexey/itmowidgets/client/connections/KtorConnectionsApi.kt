package dev.alllexey.itmowidgets.client.connections

import dev.alllexey.itmowidgets.client.http.BackendBody
import dev.alllexey.itmowidgets.client.http.BackendHttp
import dev.alllexey.itmowidgets.client.http.BackendRoute
import dev.alllexey.itmowidgets.client.http.jsonBody
import io.ktor.http.HttpMethod
import kotlin.uuid.Uuid

internal class KtorConnectionsApi(private val http: BackendHttp) : ConnectionsApi {

    override suspend fun openPairing(code: String): PairingPreview =
        http.call(route(HttpMethod.Post, "pair", code, "open"), PairingPreview.serializer())

    override suspend fun approvePairing(challengeId: Uuid, request: ApprovePairingRequest): ApprovedPairing =
        http.call(
            route(HttpMethod.Post, "pair", challengeId.toHexDashString(), "approve", body = jsonBody(request)),
            ApprovedPairing.serializer(),
        )

    override suspend fun devices(): UserDevices = http.call(route(HttpMethod.Get, "devices"), UserDevices.serializer())

    override suspend fun updateScopes(id: Uuid, request: UpdateScopesRequest): ConnectionInfo = http.call(
        route(HttpMethod.Put, "connections", id.toHexDashString(), "scopes", body = jsonBody(request)),
        ConnectionInfo.serializer(),
    )

    override suspend fun revokeConnection(id: Uuid) =
        http.callUnit(route(HttpMethod.Post, "connections", id.toHexDashString(), "revoke"))

    override suspend fun connectionItmoEnded(id: Uuid) =
        http.callUnit(route(HttpMethod.Post, "connections", id.toHexDashString(), "itmo-ended"))

    override suspend fun signOutInstall(instanceId: Uuid) =
        http.callUnit(route(HttpMethod.Post, "installs", instanceId.toHexDashString(), "sign-out"))

    private fun route(method: HttpMethod, vararg segments: String, body: BackendBody? = null) =
        BackendRoute(method, listOf("api", "users", "me") + segments, body = body)
}
