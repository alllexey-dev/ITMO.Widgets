package dev.alllexey.itmowidgets.feature.weblogin.data

import dev.alllexey.itmowidgets.client.AccessTokenSource
import dev.alllexey.itmowidgets.client.BackendClient
import dev.alllexey.itmowidgets.core.coroutines.AppDispatchers
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.testing.FakeBackendGate
import dev.alllexey.itmowidgets.core.testing.FakeDemoMode
import dev.alllexey.itmowidgets.feature.weblogin.domain.AgentScope
import dev.alllexey.itmowidgets.feature.weblogin.domain.Connection
import dev.alllexey.itmowidgets.feature.weblogin.domain.ConnectionKind
import dev.alllexey.itmowidgets.feature.weblogin.domain.PairingCrypto
import dev.alllexey.itmowidgets.feature.weblogin.domain.PairingError
import dev.alllexey.itmowidgets.feature.weblogin.domain.PairingKind
import dev.alllexey.itmowidgets.feature.weblogin.domain.PairingRequest
import dev.alllexey.itmowidgets.feature.weblogin.domain.PairingResult
import dev.alllexey.itmowidgets.feature.weblogin.domain.PairingVectors
import dev.alllexey.itmowidgets.testkit.respondJson
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlin.uuid.Uuid
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest

/** Pairing over the real Core 2.0 client and a MockEngine: the key check, the kinds, the mapping and the gates. */
class PairingRepositoryImplTest {

    private val link = PairingVectors.link()
    private val challengeId = PairingVectors.CHALLENGE_ID
    private val commitment = BASE64.encode(PairingCrypto.keyCommitment(link.publicKey, link.secret))

    @Test
    fun aLinkWhoseKeyMatchesTheCommitmentOpens() = runTest {
        val harness = BackendHarness { respondJson(preview(kind = "AGENT", commitment = "\"$commitment\"")) }

        val result = repository(harness).open(link.code, link)

        assertEquals(
            PairingResult.Success(
                PairingRequest(challengeId, PairingKind.AGENT, "nas", "0.1.0", null, emptySet(), CREATED_AT),
            ),
            result,
        )
        assertEquals(listOf("POST /api/users/me/pair/ABCD2345/open"), harness.paths())
    }

    @Test
    fun aCommitmentOfAnotherKeyIsALinkMismatch() = runTest {
        val other = BASE64.encode(PairingCrypto.keyCommitment(link.publicKey, ByteArray(16)))
        for (wire in listOf("\"$other\"", "null", "\"not base64!\"")) {
            val harness = BackendHarness { respondJson(preview(kind = "AGENT", commitment = wire)) }

            assertEquals(PairingResult.Failure(PairingError.LinkMismatch), repository(harness).open(link.code, link), wire)
        }
    }

    @Test
    fun aTypedCodeOfAKeyedKindNeedsTheLinkAndAWebCodeOpens() = runTest {
        val keyed = BackendHarness { respondJson(preview(kind = "AGENT_SESSION", commitment = "\"$commitment\"")) }
        assertEquals(PairingResult.Failure(PairingError.KeyRequired), repository(keyed).open("ABCD2345", null))

        val web = BackendHarness { respondJson(preview(kind = "WEB_SESSION", commitment = "null")) }
        assertTrue(repository(web).open("ABCD2345", null) is PairingResult.Success)
        assertEquals(PairingResult.Failure(PairingError.Unsupported), repository(web).open(link.code, link))
    }

    @Test
    fun kindsThisVersionCannotPairAreUnsupported() = runTest {
        for (kind in listOf("WEB_ITMO", "TV")) {
            val harness = BackendHarness { respondJson(preview(kind = kind, commitment = "\"$commitment\"")) }

            assertEquals(PairingResult.Failure(PairingError.Unsupported), repository(harness).open(link.code, link), kind)
        }
    }

    @Test
    fun anAgentSessionCarriesItsScopesAndUnknownOnesAreDropped() = runTest {
        val harness = BackendHarness {
            respondJson(preview("AGENT_SESSION", "\"$commitment\"", scopes = """["sport","calendar:write"]"""))
        }

        val request = (repository(harness).open(link.code, link) as PairingResult.Success).value

        assertEquals(setOf(AgentScope.SPORT), request.scopes)
    }

    @Test
    fun anApprovalSendsTheBundleAndScopesOnlyForANewAgent() = runTest {
        val harness = BackendHarness { respondJson(EMPTY) }
        val repository = repository(harness)
        val agent = PairingRequest(challengeId, PairingKind.AGENT, null, null, null, emptySet(), CREATED_AT)

        repository.approve(agent, "BwcH", setOf(AgentScope.SPORT, AgentScope.FRIENDS_READ))
        repository.approve(agent.copy(kind = PairingKind.AGENT_SESSION), "BwcH", setOf(AgentScope.SPORT))

        assertEquals(
            listOf("""{"sealedBundle":"BwcH","scopes":["sport","friends:read"]}""", """{"sealedBundle":"BwcH"}"""),
            harness.requests.map { (it.body as TextContent).text },
        )
        assertEquals(
            List(2) { "POST /api/users/me/pair/$challengeId/approve" },
            harness.paths(),
        )
    }

    @Test
    fun aConflictIsTooManyDevicesAndAccessOffIsForbidden() = runTest {
        val conflict = BackendHarness { respondJson(errorEnvelope("business_rule_violation"), HttpStatusCode.Conflict) }
        assertEquals(PairingResult.Failure(PairingError.TooManyDevices), repository(conflict).open(link.code, link))

        val denied = BackendHarness { respondJson(errorEnvelope("permission_denied"), HttpStatusCode.Forbidden) }
        assertEquals(
            PairingResult.Failure(PairingError.Failed(AppError.Forbidden)),
            repository(denied).open(link.code, link),
        )
    }

    @Test
    fun connectionsAreMappedAndRevokedById() = runTest {
        val harness = BackendHarness { request ->
            if (request.url.encodedPath.endsWith("/revoke")) respondJson(EMPTY) else respondJson(CONNECTIONS_FIXTURE)
        }
        val repository = repository(harness)
        val id = Uuid.parse("00000000-0000-4000-8000-000000000601")

        assertEquals(
            AppResult.Success(
                listOf(
                    Connection(
                        id, ConnectionKind.AGENT, "nas", null, Instant.parse("2026-10-05T09:00:00Z"),
                        Instant.parse("2026-10-05T09:01:00Z"), itmoSession = true,
                    ),
                    Connection(
                        Uuid.parse("00000000-0000-4000-8000-000000000501"), ConnectionKind.WEB, null,
                        "Mozilla/5.0 (synthetic)", Instant.parse("2026-10-05T09:00:00Z"), null, itmoSession = false,
                    ),
                ),
            ),
            repository.connections(),
        )
        assertEquals(AppResult.Success(Unit), repository.revoke(id))
        assertEquals(
            listOf("GET /api/users/me/connections", "POST /api/users/me/connections/$id/revoke"),
            harness.paths(),
        )
    }

    @Test
    fun theDemoAndAMissingOptInSendNothing() = runTest {
        val demo = FakeDemoMode(active = true)
        val harness = BackendHarness { throw AssertionError("Backend called") }
        val demoRepository = repository(harness, FakeBackendGate(optedIn = true, demo = demo), demo)
        val optedOut = repository(harness, FakeBackendGate(optedIn = false))

        assertEquals(PairingResult.Failure(PairingError.Failed(AppError.DemoUnavailable)), demoRepository.open("A", link))
        assertEquals(AppResult.Failure(AppError.DemoUnavailable), demoRepository.connections())
        assertEquals(PairingResult.Failure(PairingError.Failed(AppError.CustomServicesDisabled)), optedOut.open("A", link))
        assertEquals(AppResult.Failure(AppError.CustomServicesDisabled), optedOut.revoke(challengeId))
        assertEquals(AppResult.Failure(AppError.CustomServicesDisabled), optedOut.reject(challengeId))
        assertTrue(harness.requests.isEmpty())
    }

    private fun TestScope.repository(
        harness: BackendHarness,
        gate: FakeBackendGate = FakeBackendGate(optedIn = true),
        demo: FakeDemoMode = FakeDemoMode(),
    ): PairingRepositoryImpl {
        val dispatcher = StandardTestDispatcher(testScheduler)
        return PairingRepositoryImpl(gate, harness.client.pairing, demo, AppDispatchers(dispatcher, dispatcher, dispatcher))
    }

    private fun preview(kind: String, commitment: String, scopes: String = "null") =
        """{"success":true,"data":{"challengeId":"$challengeId","kind":"$kind","name":" nas ","version":"0.1.0",""" +
            """"userAgent":null,"keyCommitment":$commitment,"scopes":$scopes,""" +
            """"createdAt":"2026-10-05T09:00:00Z","expiresAt":"2026-10-05T09:10:00Z"},"error":null}"""

    private class BackendHarness(backend: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData) {
        val requests = mutableListOf<HttpRequestData>()
        val client = BackendClient(
            "https://backend.test",
            AccessTokenSource { "stored-access" },
            MockEngine { request -> requests += request; backend(request) },
        )

        fun paths() = requests.map { "${it.method.value} ${it.url.encodedPath}" }
    }

    private companion object {
        val BASE64 = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT)
        val CREATED_AT: Instant = Instant.parse("2026-10-05T09:00:00Z")
        const val EMPTY = """{"success":true,"data":{},"error":null}"""

        /** Backend's contract fixture `http/pairing/connections.json` of `:shared:backend-client`. */
        const val CONNECTIONS_FIXTURE = """{"success":true,"data":[{"id":"00000000-0000-4000-8000-000000000601",""" +
            """"kind":"AGENT","name":"nas","userAgent":null,"createdAt":"2026-10-05T09:00:00Z",""" +
            """"lastUsedAt":"2026-10-05T09:01:00Z","live":true,"itmoSession":true,"current":false,""" +
            """"scopes":["sport","friends:read"]},{"id":"00000000-0000-4000-8000-000000000501",""" +
            """"kind":"WEB_SESSION","name":null,"userAgent":"Mozilla/5.0 (synthetic)",""" +
            """"createdAt":"2026-10-05T09:00:00Z","lastUsedAt":null,"live":false,"itmoSession":false,""" +
            """"current":true,"scopes":null}],"error":null}"""

        fun errorEnvelope(code: String): String =
            """{"success":false,"data":null,"error":{"message":"synthetic message","code":"$code"}}"""
    }
}
