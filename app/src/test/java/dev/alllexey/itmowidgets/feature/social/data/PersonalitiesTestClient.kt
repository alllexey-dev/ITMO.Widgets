package dev.alllexey.itmowidgets.feature.social.data

import dev.alllexey.itmoapi.itmoid.TokenSet
import dev.alllexey.itmoapi.itmoid.TokenStorage
import dev.alllexey.itmoapi.myitmo.MyItmoClient
import dev.alllexey.itmowidgets.core.network.MyItmoClientFactory
import dev.alllexey.itmowidgets.testkit.FakeClock
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

internal const val MY_ITMO_HOST = "my.itmo.ru"

/**
 * A real 2.x client over a MockEngine: [answer] serves my.itmo.ru, every other host fails the test. The stored
 * token is valid for minutes, so ITMO.ID is never asked; [requests] records what reached the engine.
 */
internal fun personalitiesClient(
    requests: MutableList<HttpRequestData>,
    answer: MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
): MyItmoClient {
    val clock = FakeClock(Instant.parse("2026-07-24T00:00:00Z"))
    val tokens = TokenSet(
        accessToken = STORED_ACCESS,
        accessExpiresAt = clock.now() + 10.minutes,
        refreshToken = "stored-refresh",
        refreshExpiresAt = clock.now() + 30.minutes,
        idToken = "stored.id.token",
    )
    val engine = MockEngine { request ->
        requests += request
        if (request.url.host != MY_ITMO_HOST) throw AssertionError("Unexpected host ${request.url.host}")
        answer(request)
    }
    return MyItmoClientFactory.create(storage = InMemoryTokenStorage(tokens), engine = engine, clock = clock)
}

/** A 2.x client whose session and engine fail the test: the demo session must not reach My ITMO. */
internal fun unreachablePersonalitiesClient(): MyItmoClient = MyItmoClientFactory.create(
    storage = object : TokenStorage {
        override suspend fun read(): TokenSet? = throw AssertionError("The demo session read the ITMO session")

        override suspend fun write(tokens: TokenSet?) = throw AssertionError("The demo session wrote the ITMO session")
    },
    engine = MockEngine { request -> throw AssertionError("The demo session asked ${request.url.host}${request.url.encodedPath}") },
    clock = Clock.System,
)

internal const val STORED_ACCESS = "stored-access"

private class InMemoryTokenStorage(private var tokens: TokenSet?) : TokenStorage {
    override suspend fun read(): TokenSet? = tokens

    override suspend fun write(tokens: TokenSet?) {
        this.tokens = tokens
    }
}
