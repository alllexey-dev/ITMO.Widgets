package dev.alllexey.itmowidgets.client.connections

import dev.alllexey.itmowidgets.client.support.RouteCase
import kotlin.time.Instant
import kotlin.uuid.Uuid

/** One [RouteCase] per public function of [ConnectionsApi], with synthetic arguments. */
object ConnectionsRouteCases {
    val challengeId: Uuid = Uuid.parse("00000000-0000-4000-8000-000000000501")
    val connectionId: Uuid = Uuid.parse("00000000-0000-4000-8000-000000000601")
    val instanceId: Uuid = Uuid.parse("00000000-0000-4000-8000-000000000702")
    val approval = ApprovePairingRequest(
        sealedBundle = "BwcH",
        scopes = listOf(ConnectionScope.SPORT, ConnectionScope.FRIENDS_READ),
        itmo = ItmoSession("sid-1", Instant.parse("2026-12-30T09:00:00Z")),
    )
    val narrowed = UpdateScopesRequest(listOf(ConnectionScope.SPORT))

    val openPairing = RouteCase("openPairing") { connections.openPairing("K7M2QX9P") }
    val approvePairing = RouteCase("approvePairing") { connections.approvePairing(challengeId, approval) }
    val devices = RouteCase("devices") { connections.devices() }
    val updateScopes = RouteCase("updateScopes") { connections.updateScopes(connectionId, narrowed) }
    val revokeConnection = RouteCase("revokeConnection") { connections.revokeConnection(connectionId) }
    val connectionItmoEnded = RouteCase("connectionItmoEnded") { connections.connectionItmoEnded(connectionId) }
    val signOutInstall = RouteCase("signOutInstall") { connections.signOutInstall(instanceId) }

    val all: List<RouteCase> = listOf(
        openPairing,
        approvePairing,
        devices,
        updateScopes,
        revokeConnection,
        connectionItmoEnded,
        signOutInstall,
    )
}
