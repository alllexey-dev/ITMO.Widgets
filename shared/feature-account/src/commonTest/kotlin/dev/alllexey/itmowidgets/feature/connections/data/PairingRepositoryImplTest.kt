package dev.alllexey.itmowidgets.feature.connections.data

import dev.alllexey.itmowidgets.client.connections.ApprovePairingRequest
import dev.alllexey.itmowidgets.client.connections.ApprovedPairing
import dev.alllexey.itmowidgets.client.connections.ClientAppInfo
import dev.alllexey.itmowidgets.client.connections.ClientDeployment
import dev.alllexey.itmowidgets.client.connections.ConnectionClient
import dev.alllexey.itmowidgets.client.connections.ConnectionInfo
import dev.alllexey.itmowidgets.client.connections.ConnectionScope
import dev.alllexey.itmowidgets.client.connections.ConnectionsApi
import dev.alllexey.itmowidgets.client.connections.GrantRequest
import dev.alllexey.itmowidgets.client.connections.PairingKind
import dev.alllexey.itmowidgets.client.connections.PairingPreview
import dev.alllexey.itmowidgets.client.connections.ScopeInfo
import dev.alllexey.itmowidgets.client.connections.UpdateScopesRequest
import dev.alllexey.itmowidgets.client.connections.UserDevices
import dev.alllexey.itmowidgets.client.error.BackendException
import dev.alllexey.itmowidgets.core.coroutines.AppDispatchers
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.testing.FakeBackendGate
import dev.alllexey.itmowidgets.core.testing.FakeDemoMode
import dev.alllexey.itmowidgets.feature.connections.domain.ClientType
import dev.alllexey.itmowidgets.feature.connections.domain.ItmoGrants
import dev.alllexey.itmowidgets.feature.connections.domain.ItmoSessionRef
import dev.alllexey.itmowidgets.feature.connections.domain.PairingApproval
import dev.alllexey.itmowidgets.feature.connections.domain.PairingCrypto
import dev.alllexey.itmowidgets.feature.connections.domain.PairingError
import dev.alllexey.itmowidgets.feature.connections.domain.PairingRequest
import dev.alllexey.itmowidgets.feature.connections.domain.PairingResult
import dev.alllexey.itmowidgets.feature.connections.domain.PairingVectors
import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlin.uuid.Uuid
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest

class PairingRepositoryImplTest {

    private val dispatcher = StandardTestDispatcher()
    private val dispatchers = AppDispatchers(io = dispatcher, default = dispatcher, main = dispatcher)
    private val link = PairingVectors.link()
    private val commitment = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT)
        .encode(PairingCrypto.keyCommitment(link.publicKey, link.secret))
    private val api = FakeApi(preview(commitment))

    private fun repository(optedIn: Boolean = true, demo: Boolean = false) =
        PairingRepositoryImpl(FakeBackendGate(optedIn), api, FakeDemoMode(demo), dispatchers)

    @Test
    fun aMatchingLinkOpensWithBackendsScopeTexts() = runTest(dispatcher) {
        val opened = assertIs<PairingResult.Success<PairingRequest>>(repository().open(link)).value

        assertEquals(listOf(link.code), api.openedCodes)
        assertEquals("nas", opened.name)
        assertEquals(ClientType.AGENT, opened.client)
        assertEquals(listOf("SPORT", null), opened.scopes.map { it.id })
        assertEquals("Запись на физкультуру", opened.scopes.first().title)
    }

    @Test
    fun anotherQrsKeyIsRefused() = runTest(dispatcher) {
        api.preview = preview("AQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQE")

        assertEquals(PairingResult.Failure(PairingError.LinkMismatch), repository().open(link))
    }

    @Test
    fun anUnknownKindOrClientIsUnsupported() = runTest(dispatcher) {
        for (preview in listOf(preview(commitment).copy(kind = PairingKind.UNKNOWN), preview(commitment).copy(client = ConnectionClient.UNKNOWN))) {
            api.preview = preview
            assertEquals(PairingResult.Failure(PairingError.Unsupported), repository().open(link))
        }
    }

    @Test
    fun fiveConnectionsAreTooMany() = runTest(dispatcher) {
        api.failure = BackendException.Http(409, "business_rule_violation")

        assertEquals(PairingResult.Failure(PairingError.TooManyDevices), repository().open(link))
    }

    @Test
    fun theDemoAndTheMissingOptInNeverReachBackend() = runTest(dispatcher) {
        assertEquals(PairingResult.Failure(PairingError.Failed(AppError.DemoUnavailable)), repository(demo = true).open(link))
        assertEquals(
            PairingResult.Failure(PairingError.Failed(AppError.CustomServicesDisabled)),
            repository(optedIn = false).open(link),
        )
        assertTrue(api.openedCodes.isEmpty())
    }

    @Test
    fun theApprovalSendsTheWireScopesAndTheSession() = runTest(dispatcher) {
        val request = (repository().open(link) as PairingResult.Success).value
        val session = ItmoSessionRef("sid-1", Instant.parse("2026-12-30T09:00:00Z"))

        val approved = repository().approve(request, PairingApproval("BwcH", listOf("SPORT"), session))

        assertEquals(PairingResult.Success(CONNECTION), approved)
        val sent = api.approvals.single()
        assertEquals(listOf(ConnectionScope.SPORT), sent.scopes)
        assertEquals("sid-1", sent.itmo?.sid)
    }

    @Test
    fun aProtocol2PreviewCarriesTheGrantsAndTheRegistryEntry() = runTest(dispatcher) {
        api.preview = protocol2(GrantRequest("widgets:reviews:read", false, " Отзывы ", "Читает", null, null), REFRESH, SSO)

        val opened = assertIs<PairingResult.Success<PairingRequest>>(repository().open(link)).value

        assertEquals(2, opened.protocol)
        assertEquals("itmo-mcp", opened.clientId)
        assertEquals("ITMO MCP", opened.appName)
        assertEquals(listOf("widgets:reviews:read", ItmoGrants.REFRESH, ItmoGrants.SSO), opened.grants.map { it.id })
        assertEquals("Отзывы", opened.grants.first().title)
        assertEquals("purpose", opened.grants[1].purpose)
    }

    @Test
    fun aProtocol1PreviewKeepsTheOldRequestEvenWithGrantsListed() = runTest(dispatcher) {
        api.preview = protocol2(REFRESH).copy(protocol = 1, client = ConnectionClient.AGENT)

        val opened = assertIs<PairingResult.Success<PairingRequest>>(repository().open(link)).value

        assertEquals(1, opened.protocol)
        assertTrue(opened.grants.isEmpty())
    }

    @Test
    fun anUnknownItmoGrantIsUnsupportedOnlyWhenRequired() = runTest(dispatcher) {
        api.preview = protocol2(REFRESH, GrantRequest("itmo:refresh:dev-stand", false, null, null, null, null))
        assertIs<PairingResult.Success<PairingRequest>>(repository().open(link))

        api.preview = protocol2(REFRESH, GrantRequest("itmo:refresh:dev-stand", true, null, null, null, null))
        assertEquals(PairingResult.Failure(PairingError.Unsupported), repository().open(link))
    }

    @Test
    fun aProtocol2ClientOfANewKindOpensAndANewerProtocolDoesNot() = runTest(dispatcher) {
        api.preview = protocol2(REFRESH).copy(client = ConnectionClient.UNKNOWN)
        assertEquals(ClientType.OTHER, assertIs<PairingResult.Success<PairingRequest>>(repository().open(link)).value.client)

        api.preview = protocol2(REFRESH).copy(protocol = 3)
        assertEquals(PairingResult.Failure(PairingError.Unsupported), repository().open(link))
    }

    @Test
    fun aProtocol2ApprovalSendsTheGrantedIdsWithoutScopes() = runTest(dispatcher) {
        api.preview = protocol2(REFRESH)
        val request = (repository().open(link) as PairingResult.Success).value
        val session = ItmoSessionRef("sid-1", Instant.parse("2026-12-30T09:00:00Z"))

        repository().approve(request, PairingApproval("BwcH", null, session, listOf("widgets:reviews:read", ItmoGrants.REFRESH)))

        val sent = api.approvals.single()
        assertEquals(listOf("widgets:reviews:read", ItmoGrants.REFRESH), sent.granted)
        assertNull(sent.scopes)
    }

    private fun protocol2(vararg grants: GrantRequest) = preview(commitment).copy(
        client = ConnectionClient.MCP,
        requestedScopes = emptyList(),
        protocol = 2,
        app = ClientAppInfo("itmo-mcp", " ITMO MCP ", null, null, ClientDeployment.SELF_HOSTED, false),
        grants = grants.toList(),
    )

    private fun preview(keyCommitment: String) = PairingPreview(
        challengeId = PairingVectors.CHALLENGE_ID,
        kind = PairingKind.CONNECT,
        client = ConnectionClient.AGENT,
        name = " nas ",
        version = "1.2",
        platform = "linux",
        requestedScopes = listOf(
            ScopeInfo(ConnectionScope.SPORT, "Запись на физкультуру", "Записывается"),
            ScopeInfo(ConnectionScope.UNKNOWN, "Новое", "Новый доступ"),
        ),
        keyCommitment = keyCommitment,
        connectionId = null,
        expiresAt = Instant.parse("2026-10-05T09:10:00Z"),
    )

    private class FakeApi(var preview: PairingPreview) : ConnectionsApi {
        val openedCodes = mutableListOf<String>()
        val approvals = mutableListOf<ApprovePairingRequest>()
        var failure: BackendException? = null

        override suspend fun openPairing(code: String): PairingPreview {
            failure?.let { throw it }
            openedCodes += code
            return preview
        }

        override suspend fun approvePairing(challengeId: Uuid, request: ApprovePairingRequest): ApprovedPairing {
            approvals += request
            return ApprovedPairing(CONNECTION)
        }

        override suspend fun devices(): UserDevices = error("not used")
        override suspend fun updateScopes(id: Uuid, request: UpdateScopesRequest): ConnectionInfo = error("not used")
        override suspend fun revokeConnection(id: Uuid) = error("not used")
        override suspend fun connectionItmoEnded(id: Uuid) = error("not used")
        override suspend fun connectionItmoClosed(id: Uuid) = error("not used")
        override suspend fun signOutInstall(instanceId: Uuid) = error("not used")
    }

    private companion object {
        val REFRESH = GrantRequest(ItmoGrants.REFRESH, false, null, null, " purpose ", null)
        val SSO = GrantRequest(ItmoGrants.SSO, false, null, null, null, null)
        val CONNECTION: Uuid = Uuid.parse("00000000-0000-4000-8000-000000000601")
    }
}
