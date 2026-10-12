package dev.alllexey.itmowidgets.client.pairing

import dev.alllexey.itmowidgets.client.support.assertRequest
import dev.alllexey.itmowidgets.client.support.recordRequest
import dev.alllexey.itmowidgets.client.support.runSuspend
import io.ktor.http.HttpMethod
import kotlin.test.Test
import kotlin.test.assertEquals

/** The method, path and body of every [PairingApi] call ([PairingRouteCases]). */
class PairingRequestTest {

    @Test
    fun everyPublicFunctionHasOneCase() {
        assertEquals(5, PairingRouteCases.all.size)
        assertEquals(PairingRouteCases.all.size, PairingRouteCases.all.map { it.name }.toSet().size)
    }

    @Test
    fun openPairingSendsTheCodeAsOneSegmentWithoutABody() = runSuspend {
        PairingRouteCases.openPairing.assertRequest(HttpMethod.Post, "/api/users/me/pair/K7M2QX9P/open")
    }

    @Test
    fun approvePairingSendsTheBundleAndTheWireScopes() = runSuspend {
        PairingRouteCases.approvePairing.assertRequest(
            HttpMethod.Post,
            "/api/users/me/pair/${PairingRouteCases.challengeId}/approve",
            body = """{"sealedBundle":"BwcH","scopes":["sport","friends:read"]}""",
        )
    }

    @Test
    fun aNullScopeListIsOmitted() = runSuspend {
        val request = recordRequest("approveSession") {
            client.pairing.approvePairing(PairingRouteCases.challengeId, ApprovePairingRequest("BwcH", null))
        }

        assertEquals("""{"sealedBundle":"BwcH"}""", request.body)
    }

    @Test
    fun rejectPairing() = runSuspend {
        PairingRouteCases.rejectPairing.assertRequest(
            HttpMethod.Post,
            "/api/users/me/pair/${PairingRouteCases.challengeId}/reject",
        )
    }

    @Test
    fun connections() = runSuspend {
        PairingRouteCases.connections.assertRequest(HttpMethod.Get, "/api/users/me/connections")
    }

    @Test
    fun revokeConnection() = runSuspend {
        PairingRouteCases.revokeConnection.assertRequest(
            HttpMethod.Post,
            "/api/users/me/connections/${PairingRouteCases.connectionId}/revoke",
        )
    }
}
