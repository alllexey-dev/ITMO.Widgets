package dev.alllexey.itmowidgets.core.network

import dev.alllexey.itmoapi.itmoid.TokenSet
import dev.alllexey.itmoapi.itmoid.TokenStorage
import dev.alllexey.itmoapi.myitmo.MyItmoClient
import dev.alllexey.itmowidgets.client.BackendClient
import dev.alllexey.itmowidgets.testkit.FakeClock
import dev.alllexey.itmowidgets.testkit.respondJson
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import java.io.File
import java.util.Collections
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * The Core 2.0 client as `di/NetworkModule.kt` builds it: over the MyItmoApi 2.x session of [storage] and one
 * MockEngine, where `id.itmo.ru` answers token refreshes and every other host is Backend.
 */
class Core2Harness(
    stored: TokenSet?,
    private val identity: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData = {
        respondJson(TOKEN_RESPONSE)
    },
    private val backend: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
) {
    val storage = InMemoryTokenStorage(stored)
    /** Synchronized: MockEngine answers concurrent requests (a repository's `async` calls) on several threads. */
    val requests: MutableList<HttpRequestData> = Collections.synchronizedList(mutableListOf())
    val backendRequests: List<HttpRequestData>
        get() = synchronized(requests) { requests.filter { it.url.host != ITMO_ID_HOST } }

    private val engine = MockEngine { request ->
        requests += request
        if (request.url.host == ITMO_ID_HOST) identity(request) else backend(request)
    }

    val myItmo: MyItmoClient = MyItmoClientFactory.create(storage = storage, engine = engine, clock = CLOCK)
    val client: BackendClient = BackendClientFactory.create(BACKEND_URL, myItmo.tokens, engine)

    companion object {
        const val BACKEND_URL = "https://backend.test"
        const val ITMO_ID_HOST = "id.itmo.ru"
        val NOW: Instant = Instant.parse("2026-07-24T00:00:00Z")
        private val CLOCK = FakeClock(NOW)

        /** ITMO.ID's answer to a refresh, with a new access token and a new id token. */
        const val TOKEN_RESPONSE = """{"access_token":"refreshed-access","expires_in":300,""" +
            """"refresh_token":"refreshed-refresh","refresh_expires_in":600,"id_token":"refreshed.id.token",""" +
            """"session_state":"synthetic"}"""

        fun session(accessExpiresIn: Duration = 10.minutes, refreshExpiresIn: Duration = 30.minutes) = TokenSet(
            accessToken = "stored-access",
            accessExpiresAt = NOW + accessExpiresIn,
            refreshToken = "stored-refresh",
            refreshExpiresAt = NOW + refreshExpiresIn,
            idToken = "stored.id.token"
        )

        /** A golden fixture of Backend's contract, vendored by L19 into `:shared:backend-client`. */
        fun contractFixture(path: String): String =
            File("../shared/backend-client/src/commonTest/resources/contract/$path").readText()

        /** Backend's error envelope (`GlobalExceptionHandler`) with a synthetic message. */
        fun errorEnvelope(code: String): String =
            """{"success":false,"data":null,"error":{"message":"synthetic message","code":"$code"}}"""
    }
}

/** The session snapshot of one test; MyItmoApi's `TokenManager` is its only writer. */
class InMemoryTokenStorage(var tokens: TokenSet?) : TokenStorage {
    override suspend fun read(): TokenSet? = tokens

    override suspend fun write(tokens: TokenSet?) {
        this.tokens = tokens
    }
}
