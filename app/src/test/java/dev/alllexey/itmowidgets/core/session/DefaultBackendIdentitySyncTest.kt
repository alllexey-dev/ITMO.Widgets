package dev.alllexey.itmowidgets.core.session

import android.content.Context
import android.content.ContextWrapper
import dev.alllexey.itmowidgets.core.network.Core2Harness
import dev.alllexey.itmowidgets.core.network.Core2Harness.Companion.contractFixture
import dev.alllexey.itmowidgets.core.network.Core2Harness.Companion.errorEnvelope
import dev.alllexey.itmowidgets.core.network.Core2Harness.Companion.session
import dev.alllexey.itmowidgets.core.testing.FakeBackendGate
import dev.alllexey.itmowidgets.core.testing.FakeDemoMode
import dev.alllexey.itmowidgets.core.testing.MainDispatcherRule
import dev.alllexey.itmowidgets.core.testing.RecordingDiagnostics
import dev.alllexey.itmowidgets.testkit.bodyText
import dev.alllexey.itmowidgets.testkit.respondJson
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** The identity upload over Core 2.0, with the id token of the MyItmoApi 2.x session. */
class DefaultBackendIdentitySyncTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val diagnostics = RecordingDiagnostics()

    @Test
    fun `publishes the stored id token through the users API`() = runTest {
        val harness = Core2Harness(session()) { respondJson(contractFixture("http/users/updateIdTokenData.json")) }

        assertTrue(sync(harness))

        val request = harness.requests.single()
        assertEquals(HttpMethod.Put, request.method)
        assertEquals("/api/users/me/id-token", request.url.encodedPath)
        assertEquals("Bearer stored-access", request.headers[HttpHeaders.Authorization])
        assertEquals(Json.parseToJsonElement("""{"idToken":"stored.id.token"}"""), Json.parseToJsonElement(request.bodyText()))
        assertTrue(diagnostics.messages.isEmpty())
    }

    @Test
    fun `an expiring session is refreshed first and the new id token is published`() = runTest {
        val harness = Core2Harness(session(accessExpiresIn = (-1).minutes)) {
            respondJson(contractFixture("http/users/updateIdTokenData.json"))
        }

        assertTrue(sync(harness))

        assertEquals(Core2Harness.ITMO_ID_HOST, harness.requests.first().url.host)
        assertEquals(
            Json.parseToJsonElement("""{"idToken":"refreshed.id.token"}"""),
            Json.parseToJsonElement(harness.backendRequests.single().bodyText())
        )
    }

    @Test
    fun `without the opt-in nothing is read or sent`() = runTest {
        val harness = Core2Harness(session()) { throw AssertionError("Backend called without the opt-in") }

        assertTrue(sync(harness, gate = FakeBackendGate(optedIn = false)))

        assertTrue(harness.requests.isEmpty())
    }

    @Test
    fun `the demo session sends nothing, even with the stored opt-in`() = runTest {
        val demo = FakeDemoMode(active = true)
        val harness = Core2Harness(session()) { throw AssertionError("Backend called in the demo") }

        assertTrue(sync(harness, gate = FakeBackendGate(optedIn = true, demo = demo), demo = demo))

        assertTrue(harness.requests.isEmpty())
    }

    @Test
    fun `without a session nothing is sent and nothing is retried`() = runTest {
        val harness = Core2Harness(stored = null) { throw AssertionError("Backend called without a session") }

        assertTrue(sync(harness))

        assertTrue(harness.requests.isEmpty())
        assertEquals(listOf("WARNING:BackendIdentitySync:No id token in storage; identity not published"), diagnostics.messages)
    }

    @Test
    fun `a 401 from Backend is a failed upload for the retry`() = runTest {
        val harness = Core2Harness(session()) { respondJson(errorEnvelope("unauthorized"), HttpStatusCode.Unauthorized) }

        assertFalse(sync(harness))

        assertEquals(1, harness.requests.size)
        assertEquals(listOf("WARNING:BackendIdentitySync:Failed to publish identity to backend"), diagnostics.messages)
        assertEquals("stored-refresh", harness.storage.tokens?.refreshToken)
    }

    /** `scheduleRetry = false` as the worker calls it: the retry itself is WorkManager's. */
    private suspend fun sync(
        harness: Core2Harness,
        gate: FakeBackendGate = FakeBackendGate(optedIn = true),
        demo: FakeDemoMode = FakeDemoMode(),
    ): Boolean = DefaultBackendIdentitySync(
        context = unusedContext(),
        gate = gate,
        tokens = harness.myItmo.tokens,
        storage = harness.storage,
        users = harness.client.users,
        diagnostics = diagnostics,
        demo = demo,
        dispatchers = mainDispatcherRule.appDispatchers
    ).sync(scheduleRetry = false)

    /** A context nobody may touch: allocated without Android's stub constructor, any call on it fails. */
    private fun unusedContext(): Context {
        val unsafe = Class.forName("sun.misc.Unsafe").getDeclaredField("theUnsafe").apply { isAccessible = true }.get(null)
        val allocate = unsafe.javaClass.getMethod("allocateInstance", Class::class.java)
        return allocate.invoke(unsafe, ContextWrapper::class.java) as Context
    }
}
