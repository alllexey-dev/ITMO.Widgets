package dev.alllexey.itmowidgets.feature.sport.data

import dev.alllexey.itmoapi.itmoid.TokenSet
import dev.alllexey.itmoapi.itmoid.TokenStorage
import dev.alllexey.itmoapi.myitmo.MyItmoClient
import dev.alllexey.itmowidgets.client.BackendClient
import dev.alllexey.itmowidgets.core.coroutines.AppDispatchers
import dev.alllexey.itmowidgets.core.network.BackendClientFactory
import dev.alllexey.itmowidgets.core.network.MyItmoClientFactory
import dev.alllexey.itmowidgets.testkit.FakeClock
import dev.alllexey.itmowidgets.testkit.respondJson
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import java.io.File
import java.lang.reflect.Proxy
import java.util.Collections
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

// The JVM harness of the sport data host tests, as :app's tests had it (`core/testing`, `core/network/Core2Harness`)
// before the data moved here.

/** `Dispatchers.Main` on [dispatcher] for one test; [appDispatchers] puts every slot on the same scheduler. */
@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(
    val dispatcher: TestDispatcher = StandardTestDispatcher()
) : TestWatcher() {

    /** Injected into the class under test; `runTest` shares its scheduler through `Dispatchers.Main`. */
    val appDispatchers: AppDispatchers = AppDispatchers(io = dispatcher, default = dispatcher, main = dispatcher)

    override fun starting(description: Description) {
        Dispatchers.setMain(dispatcher)
    }

    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}

/**
 * `io` on real threads, the rest on [dispatcher], for race tests whose fake network blocks the calling thread until
 * the test releases it: on the single test thread that block would never be released.
 */
fun blockingIoAppDispatchers(dispatcher: TestDispatcher): AppDispatchers =
    AppDispatchers(io = Dispatchers.IO, default = dispatcher, main = dispatcher)

/** A client interface every call of which fails the test: the demo session must not reach it. */
inline fun <reified T : Any> unreachable(): T =
    Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, method, _ ->
        if (method.declaringClass == Any::class.java) return@newProxyInstance method.name.hashCode()
        throw AssertionError("The demo session called ${T::class.java.simpleName}.${method.name}")
    } as T

/**
 * MyItmoApi 2.x and Core 2.0 as the app builds them: over the session of [storage] and one MockEngine, where
 * `id.itmo.ru` answers token refreshes and every other host is my.itmo.ru or Backend ([backend]).
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

        /** A golden fixture of Backend's contract, vendored by L19 into `:shared:backend-client`; never copied. */
        fun contractFixture(path: String): String =
            File("../backend-client/src/commonTest/resources/contract/$path").readText()

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
