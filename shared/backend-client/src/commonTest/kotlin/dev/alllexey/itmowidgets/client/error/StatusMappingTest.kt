package dev.alllexey.itmowidgets.client.error

import dev.alllexey.itmowidgets.client.support.MockBackend
import dev.alllexey.itmowidgets.client.support.Probe
import dev.alllexey.itmowidgets.client.support.errorEnvelope
import dev.alllexey.itmowidgets.client.support.html
import dev.alllexey.itmowidgets.client.support.json
import dev.alllexey.itmowidgets.client.support.probeRoute
import dev.alllexey.itmowidgets.client.support.runSuspend
import io.ktor.http.HttpStatusCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull

class StatusMappingTest {

    private suspend fun failureFor(status: HttpStatusCode, body: String, html: Boolean = false): BackendException {
        val backend = MockBackend { if (html) html(status, body) else json(status, body) }
        return assertFailsWith<BackendException> { backend.http.call(probeRoute(), Probe.serializer()) }
    }

    @Test
    fun emptyUnauthorizedIsUnauthorized() = runSuspend {
        assertIs<BackendException.Unauthorized>(failureFor(HttpStatusCode.Unauthorized, ""))
    }

    @Test
    fun unauthorizedEnvelopeIsUnauthorized() = runSuspend {
        assertIs<BackendException.Unauthorized>(failureFor(HttpStatusCode.Unauthorized, errorEnvelope("unauthorized")))
    }

    @Test
    fun forbiddenCodesStayDistinguishable() = runSuspend {
        for (code in listOf("restricted", "permission_denied", "access_denied", "csrf")) {
            val error = assertIs<BackendException.Forbidden>(failureFor(HttpStatusCode.Forbidden, errorEnvelope(code)))
            assertEquals(code, error.code)
        }
    }

    @Test
    fun nonJsonForbiddenHasNoCode() = runSuspend {
        val error = assertIs<BackendException.Forbidden>(
            failureFor(HttpStatusCode.Forbidden, "<html><body>403 Forbidden</body></html>", html = true),
        )
        assertNull(error.code)
    }

    @Test
    fun foreignJsonErrorBodyHasNoCode() = runSuspend {
        val springDefault =
            """{"timestamp":"2026-10-03T09:00:00Z","status":403,"error":"Forbidden","path":"/api/probe"}"""
        val error = assertIs<BackendException.Forbidden>(failureFor(HttpStatusCode.Forbidden, springDefault))
        assertNull(error.code)
    }

    @Test
    fun notFoundKeepsItsCode() = runSuspend {
        val error = assertIs<BackendException.NotFound>(failureFor(HttpStatusCode.NotFound, errorEnvelope("not_found")))
        assertEquals("not_found", error.code)
    }

    @Test
    fun otherStatusesAreHttpWithStatusAndCode() = runSuspend {
        val conflict = assertIs<BackendException.Http>(
            failureFor(HttpStatusCode.Conflict, errorEnvelope("business_rule_violation")),
        )
        assertEquals(409, conflict.status)
        assertEquals("business_rule_violation", conflict.code)

        val badRequest = assertIs<BackendException.Http>(
            failureFor(HttpStatusCode.BadRequest, errorEnvelope("invalid_request")),
        )
        assertEquals(400, badRequest.status)
        assertEquals("invalid_request", badRequest.code)
    }

    @Test
    fun htmlBadGatewayIsHttpWithoutCode() = runSuspend {
        val error = assertIs<BackendException.Http>(
            failureFor(HttpStatusCode.BadGateway, "<html><title>502 Bad Gateway</title></html>", html = true),
        )
        assertEquals(502, error.status)
        assertNull(error.code)
    }

    @Test
    fun serverMessageIsNeverKept() = runSuspend {
        val error = failureFor(HttpStatusCode.Forbidden, errorEnvelope("restricted"))
        assertFalse(error.message.orEmpty().contains("synthetic message"))
    }
}
