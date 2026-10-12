package dev.alllexey.itmowidgets.client.pairing

import dev.alllexey.itmowidgets.client.support.RouteCase
import kotlin.uuid.Uuid

/** One [RouteCase] per public function of [PairingApi], with synthetic arguments. */
object PairingRouteCases {
    val challengeId: Uuid = Uuid.parse("00000000-0000-4000-8000-000000000501")
    val connectionId: Uuid = Uuid.parse("00000000-0000-4000-8000-000000000601")
    val approval = ApprovePairingRequest(sealedBundle = "BwcH", scopes = listOf(AgentScope.SPORT, AgentScope.FRIENDS_READ))

    val openPairing = RouteCase("openPairing") { pairing.openPairing("K7M2QX9P") }
    val approvePairing = RouteCase("approvePairing") { pairing.approvePairing(challengeId, approval) }
    val rejectPairing = RouteCase("rejectPairing") { pairing.rejectPairing(challengeId) }
    val connections = RouteCase("connections") { pairing.connections() }
    val revokeConnection = RouteCase("revokeConnection") { pairing.revokeConnection(connectionId) }

    val all: List<RouteCase> = listOf(openPairing, approvePairing, rejectPairing, connections, revokeConnection)
}
