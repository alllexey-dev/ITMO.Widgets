package dev.alllexey.itmowidgets.client.http

import dev.alllexey.itmowidgets.client.AccessTokenSource
import dev.alllexey.itmowidgets.client.error.BackendException
import dev.alllexey.itmowidgets.client.support.MockBackend
import dev.alllexey.itmowidgets.client.support.probeRoute
import dev.alllexey.itmowidgets.client.support.runSuspend
import io.ktor.http.HttpHeaders
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame

/** Ports Core 1.x `TokenInterceptorTest` and adds the blank-token and per-request rules. */
class AuthorizationHeaderTest {

    @Test
    fun addsBearerTokenWhenOneIsAvailable() = runSuspend {
        val backend = MockBackend(tokens = { "access-token" })

        backend.http.callUnit(probeRoute())

        assertEquals("Bearer access-token", backend.lastRequest.headers[HttpHeaders.Authorization])
    }

    @Test
    fun leavesRequestUnauthenticatedWhenNoTokenIsAvailable() = runSuspend {
        val backend = MockBackend(tokens = { null })

        backend.http.callUnit(probeRoute())

        assertNull(backend.lastRequest.headers[HttpHeaders.Authorization])
    }

    @Test
    fun treatsBlankTokenAsNoToken() = runSuspend {
        val backend = MockBackend(tokens = { " \t " })

        backend.http.callUnit(probeRoute())

        assertFalse(HttpHeaders.Authorization in backend.lastRequest.headers)
    }

    @Test
    fun trimsTheToken() = runSuspend {
        val backend = MockBackend(tokens = { "  access-token\n" })

        backend.http.callUnit(probeRoute())

        assertEquals("Bearer access-token", backend.lastRequest.headers[HttpHeaders.Authorization])
    }

    @Test
    fun asksTheSourceForEveryRequest() = runSuspend {
        var issued = 0
        val backend = MockBackend(tokens = { "token-${++issued}" })

        backend.http.callUnit(probeRoute())
        backend.http.callUnit(probeRoute())

        assertEquals(
            listOf("Bearer token-1", "Bearer token-2"),
            backend.requests.map { it.headers[HttpHeaders.Authorization] },
        )
    }

    @Test
    fun doesNotSilentlyDowngradeFailedTokenRefresh() = runSuspend {
        val refreshFailure = IllegalStateException("refresh failed")
        val backend = MockBackend(tokens = { throw refreshFailure })

        val error = assertFailsWith<BackendException.Transport> { backend.http.callUnit(probeRoute()) }

        assertSame(refreshFailure, error.cause)
        assertEquals(0, backend.requests.size)
    }

    @Test
    fun passesTokenSourceCancellationThrough() = runSuspend {
        val backend = MockBackend(tokens = AccessTokenSource { throw CancellationException("left the screen") })

        assertFailsWith<CancellationException> { backend.http.callUnit(probeRoute()) }

        assertEquals(0, backend.requests.size)
    }
}
