package dev.alllexey.itmowidgets.client.connections

import dev.alllexey.itmowidgets.client.support.assertRequest
import dev.alllexey.itmowidgets.client.support.recordRequest
import dev.alllexey.itmowidgets.client.support.runSuspend
import io.ktor.http.HttpMethod
import kotlin.test.Test
import kotlin.test.assertEquals

/** The method, path and body of every [ConnectionsApi] call ([ConnectionsRouteCases]). */
class ConnectionsRequestTest {

    private val cases = ConnectionsRouteCases

    @Test
    fun everyPublicFunctionHasOneCase() {
        assertEquals(7, cases.all.size)
        assertEquals(cases.all.size, cases.all.map { it.name }.toSet().size)
    }

    @Test
    fun openPairingSendsTheCodeAsOneSegmentWithoutABody() = runSuspend {
        cases.openPairing.assertRequest(HttpMethod.Post, "/api/users/me/pair/K7M2QX9P/open")
    }

    @Test
    fun approvePairingSendsTheBundleTheWireScopesAndTheSession() = runSuspend {
        cases.approvePairing.assertRequest(
            HttpMethod.Post,
            "/api/users/me/pair/${cases.challengeId}/approve",
            body = """{"sealedBundle":"BwcH","scopes":["sport","friends:read"],
                "itmo":{"sid":"sid-1","expiresAt":"2026-12-30T09:00:00Z"}}""",
        )
    }

    @Test
    fun aWebApprovalSendsNothingButNulls() = runSuspend {
        val request = recordRequest("approveWeb") {
            client.connections.approvePairing(cases.challengeId, ApprovePairingRequest(null, null, null))
        }

        assertEquals("{}", request.body)
    }

    @Test
    fun devices() = runSuspend {
        cases.devices.assertRequest(HttpMethod.Get, "/api/users/me/devices")
    }

    @Test
    fun updateScopes() = runSuspend {
        cases.updateScopes.assertRequest(
            HttpMethod.Put,
            "/api/users/me/connections/${cases.connectionId}/scopes",
            body = """{"scopes":["sport"]}""",
        )
    }

    @Test
    fun revokeConnection() = runSuspend {
        cases.revokeConnection.assertRequest(HttpMethod.Post, "/api/users/me/connections/${cases.connectionId}/revoke")
    }

    @Test
    fun connectionItmoEnded() = runSuspend {
        cases.connectionItmoEnded.assertRequest(
            HttpMethod.Post,
            "/api/users/me/connections/${cases.connectionId}/itmo-ended",
        )
    }

    @Test
    fun signOutInstall() = runSuspend {
        cases.signOutInstall.assertRequest(HttpMethod.Post, "/api/users/me/installs/${cases.instanceId}/sign-out")
    }
}
