package dev.alllexey.itmowidgets.client.pairing

import dev.alllexey.itmowidgets.client.contract.ResponseClaim
import dev.alllexey.itmowidgets.client.contract.assertAreaClaims
import dev.alllexey.itmowidgets.client.support.runSuspend
import kotlinx.serialization.builtins.ListSerializer
import kotlin.test.Test

/**
 * Backend's vendored pairing fixtures of the phone's routes (`claims/pairing.txt`); the device's routes are not
 * mirrored. Every answer decodes through [PairingApi] and re-encodes to the fixture's `data`.
 */
class PairingVendoredFixturesTest {

    private val responses = listOf(
        ResponseClaim("http/pairing/openPairing.json", PairingPreview.serializer()) {
            pairing.openPairing("K7Q2M9AB")
        },
        ResponseClaim("http/pairing/approvePairing.json", null) {
            pairing.approvePairing(PairingRouteCases.challengeId, PairingRouteCases.approval)
        },
        ResponseClaim("http/pairing/rejectPairing.json", null) { pairing.rejectPairing(PairingRouteCases.challengeId) },
        ResponseClaim("http/pairing/connections.json", ListSerializer(ConnectionInfo.serializer())) {
            pairing.connections()
        },
        ResponseClaim("http/pairing/revokeConnection.json", null) {
            pairing.revokeConnection(PairingRouteCases.connectionId)
        },
    )

    @Test
    fun everyClaimDecodesAndRoundTrips() = runSuspend { assertAreaClaims("pairing", responses, emptyList()) }
}
