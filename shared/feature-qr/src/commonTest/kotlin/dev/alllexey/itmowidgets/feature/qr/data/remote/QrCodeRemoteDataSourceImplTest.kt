package dev.alllexey.itmowidgets.feature.qr.data.remote

import dev.alllexey.itmoapi.itmoid.TokenSet
import dev.alllexey.itmoapi.itmoid.TokenStorage
import dev.alllexey.itmowidgets.core.coroutines.AppDispatchers
import dev.alllexey.itmowidgets.core.network.MyItmoClientFactory
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.testing.FakeDemoMode
import dev.alllexey.itmowidgets.feature.qr.data.demo.DemoQr
import dev.alllexey.itmowidgets.feature.qr.data.local.QrCodeLocalDataSource
import dev.alllexey.itmowidgets.feature.qr.data.repository.QrCodeRepositoryImpl
import dev.alllexey.itmowidgets.feature.qr.domain.QrCodeSnapshot
import dev.alllexey.itmowidgets.testkit.FakeClock
import dev.alllexey.itmowidgets.testkit.respondJson
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest

/** The pass through `QrCodeRepositoryImpl` and the 2.x client, with a MockEngine for qr.itmo.su and ITMO.ID. */
class QrCodeRemoteDataSourceImplTest {

    private val clock = FakeClock(Instant.parse("2026-07-24T00:00:00Z"))
    private val storage = InMemoryTokenStorage(
        TokenSet(
            accessToken = STORED_ACCESS,
            accessExpiresAt = clock.now() + 10.minutes,
            refreshToken = "stored-refresh",
            refreshExpiresAt = clock.now() + 30.minutes,
            idToken = "stored.id.token"
        )
    )
    private val requests = mutableListOf<HttpRequestData>()
    private val local = FakeQrCodeLocalDataSource()

    @Test
    fun aPassIsSavedAfterOneRequestWithTheStoredToken() = runTest {
        val result = repository(passes = listOf(QrRemoteFixtures.PASS)).refreshQrHex(force = true)

        assertEquals(AppResult.Success(Unit), result)
        assertEquals(listOf(QrRemoteFixtures.PASS_HEX), local.savedValues)
        assertEquals(listOf(QR_HOST), hosts())
        assertEquals("/v1/user/pass", requests.single().url.encodedPath)
        assertEquals("Bearer $STORED_ACCESS", requests.single().headers[HttpHeaders.Authorization])
    }

    @Test
    fun aPassWithoutQrHexIsAskedAgainOnceAfterOneForcedRefresh() = runTest {
        val repository = repository(passes = listOf(QrRemoteFixtures.PASS_WITHOUT_HEX, QrRemoteFixtures.PASS))

        val result = repository.refreshQrHex(force = true)

        assertEquals(AppResult.Success(Unit), result)
        assertEquals(listOf(QrRemoteFixtures.PASS_HEX), local.savedValues)
        assertEquals(listOf(QR_HOST, ID_HOST, QR_HOST), hosts())
        assertEquals("Bearer ${QrRemoteFixtures.REFRESHED_ACCESS}", requests.last().headers[HttpHeaders.Authorization])
    }

    @Test
    fun aSecondAnswerWithoutAPassFailsAfterTheOneRefresh() = runTest {
        val repository = repository(passes = listOf(QrRemoteFixtures.PASS_NULL, QrRemoteFixtures.PASS_WITHOUT_HEX))

        val result = repository.refreshQrHex(force = true)

        assertIs<AppError.Unknown>(assertIs<AppResult.Failure>(result).error)
        assertEquals(listOf(QR_HOST, ID_HOST, QR_HOST), hosts())
        assertEquals(emptyList<String>(), local.savedValues)
    }

    @Test
    fun a401IsRefreshedOnceByTheClientAndAPassThenArrives() = runTest {
        val repository = repository(passes = listOf(UNAUTHORIZED, QrRemoteFixtures.PASS))

        val result = repository.refreshQrHex(force = true)

        assertEquals(AppResult.Success(Unit), result)
        assertEquals(listOf(QR_HOST, ID_HOST, QR_HOST), hosts())
    }

    @Test
    fun a401AfterTheRefreshNeedsANewSignInWithOneRefreshInTotal() = runTest {
        val repository = repository(passes = listOf(UNAUTHORIZED, UNAUTHORIZED))

        val result = repository.refreshQrHex(force = true)

        assertEquals(AppResult.Failure(AppError.Unauthorized), result)
        assertEquals(listOf(QR_HOST, ID_HOST, QR_HOST), hosts())
        assertEquals(emptyList<String>(), local.savedValues)
    }

    @Test
    fun a5xxFailsAtOnceWithoutARefreshAndKeepsTheCachedPass() = runTest {
        local.save("cached")
        local.savedValues.clear()
        val repository = repository(passes = listOf(BAD_GATEWAY))

        val result = repository.refreshQrHex(force = true)

        assertIs<AppError.Unknown>(assertIs<AppResult.Failure>(result).error)
        assertEquals(listOf(QR_HOST), hosts())
        assertEquals("cached", local.get())
    }

    @Test
    fun theDemoPassCostsNoRequest() = runTest {
        val repository = repository(passes = emptyList(), demo = true)

        val result = repository.refreshQrHex(force = true)

        assertEquals(AppResult.Success(Unit), result)
        assertEquals(listOf(DemoQr.HEX), local.savedValues)
        assertEquals(emptyList<HttpRequestData>(), requests)
    }

    /** qr.itmo.su answers [passes] in order; ITMO.ID answers every refresh with a new token pair. */
    private fun TestScope.repository(passes: List<String>, demo: Boolean = false): QrCodeRepositoryImpl {
        val queue = ArrayDeque(passes)
        val engine = MockEngine { request ->
            requests += request
            when (request.url.host) {
                ID_HOST -> respondJson(QrRemoteFixtures.TOKEN_SUCCESS)
                QR_HOST -> answer(queue.removeFirstOrNull() ?: throw AssertionError("Unexpected QR request"))
                else -> throw AssertionError("Unexpected host ${request.url.host}")
            }
        }
        val client = MyItmoClientFactory.create(storage = storage, engine = engine, clock = clock)
        val dispatchers = StandardTestDispatcher(testScheduler).let { AppDispatchers(io = it, default = it, main = it) }
        val remote = QrCodeRemoteDataSourceImpl(client, FakeDemoMode(active = demo), dispatchers)
        return QrCodeRepositoryImpl(local, remote, dispatchers)
    }

    private fun MockRequestHandleScope.answer(pass: String): HttpResponseData = when (pass) {
        UNAUTHORIZED -> respondJson("{}", HttpStatusCode.Unauthorized)
        BAD_GATEWAY -> respond(QrRemoteFixtures.ERROR_502, HttpStatusCode.BadGateway)
        else -> respondJson(pass)
    }

    private fun hosts(): List<String> = requests.map { it.url.host }

    private class InMemoryTokenStorage(private var tokens: TokenSet?) : TokenStorage {
        override suspend fun read(): TokenSet? = tokens

        override suspend fun write(tokens: TokenSet?) {
            this.tokens = tokens
        }
    }

    private class FakeQrCodeLocalDataSource : QrCodeLocalDataSource {
        private var cached: String? = null
        val savedValues = mutableListOf<String>()

        override fun snapshot() = cached?.let { QrCodeSnapshot(it, 3_600_000L) }

        override fun observe(): Flow<String> = flowOf(cached.orEmpty())

        override fun get(allowExpired: Boolean): String? = cached

        override fun save(hex: String) {
            cached = hex
            savedValues += hex
        }

        override fun clear() {
            cached = null
        }
    }

    private companion object {
        const val QR_HOST = "qr.itmo.su"
        const val ID_HOST = "id.itmo.ru"
        const val STORED_ACCESS = "stored-access"
        const val UNAUTHORIZED = "401"
        const val BAD_GATEWAY = "502"
    }
}
