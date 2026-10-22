package dev.alllexey.itmowidgets.client.connections

import dev.alllexey.itmowidgets.client.contract.ResponseClaim
import dev.alllexey.itmowidgets.client.contract.assertAreaClaims
import dev.alllexey.itmowidgets.client.support.runSuspend
import kotlin.test.Test

/**
 * Backend's vendored connections fixtures of the phone's routes (`claims/connections.txt`); the client's own routes
 * are not mirrored. Every answer decodes through [ConnectionsApi] and re-encodes to the fixture's `data`.
 */
class ConnectionsVendoredFixturesTest {

    private val cases = ConnectionsRouteCases

    private val responses = listOf(
        ResponseClaim("http/connections/openPairing.json", PairingPreview.serializer()) {
            connections.openPairing("K7Q2M9AB")
        },
        ResponseClaim("http/connections/approvePairing.json", ApprovedPairing.serializer()) {
            connections.approvePairing(cases.challengeId, cases.approval)
        },
        ResponseClaim("http/connections/userDevices.json", UserDevices.serializer()) { connections.devices() },
        ResponseClaim("http/connections/updateConnectionScopes.json", ConnectionInfo.serializer()) {
            connections.updateScopes(cases.connectionId, cases.narrowed)
        },
        ResponseClaim("http/connections/revokeConnection.json", null) {
            connections.revokeConnection(cases.connectionId)
        },
        ResponseClaim("http/connections/connectionItmoEnded.json", null) {
            connections.connectionItmoEnded(cases.connectionId)
        },
        ResponseClaim("http/connections/signOutInstall.json", null) { connections.signOutInstall(cases.instanceId) },
    )

    @Test
    fun everyClaimDecodesAndRoundTrips() = runSuspend { assertAreaClaims("connections", responses, emptyList()) }
}
