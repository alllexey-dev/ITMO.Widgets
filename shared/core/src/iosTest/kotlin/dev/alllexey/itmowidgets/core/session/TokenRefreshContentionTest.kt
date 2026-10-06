package dev.alllexey.itmowidgets.core.session

import dev.alllexey.itmoapi.itmoid.TokenSet
import dev.alllexey.itmoapi.myitmo.MyItmoClient
import dev.alllexey.itmowidgets.core.network.MyItmoClientFactory
import dev.alllexey.itmowidgets.core.storage.FileCrossProcessLock
import dev.alllexey.itmowidgets.core.storage.SecureStore
import dev.alllexey.itmowidgets.core.storage.TemporaryDirectory
import dev.alllexey.itmowidgets.core.testing.RecordingAppLog
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.incrementAndFetch
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext

/**
 * The app and the notification service each run their own MyItmoApi client over the shared Keychain item and take
 * the same file lock: an expired session is refreshed once, and the second process uses the rotated tokens.
 */
@OptIn(ExperimentalAtomicApi::class)
class TokenRefreshContentionTest {

    private val temporary = TemporaryDirectory()
    private val locks = temporary.root / "group" / "locks"
    private val keychain = SharedKeychain()
    private val refreshes = AtomicInt(0)
    private val identity = MockEngine {
        val count = refreshes.incrementAndFetch()
        delay(REFRESH_TIME)
        respond(
            """{"access_token":"rotated-$count","expires_in":300,"refresh_token":"refresh-$count",""" +
                """"refresh_expires_in":3600,"id_token":"id"}""",
            headers = headersOf(HttpHeaders.ContentType, "application/json")
        )
    }

    @AfterTest
    fun tearDown() {
        identity.close()
        temporary.delete()
    }

    @Test
    fun twoProcessesRefreshAnExpiredSessionOnce() = runTest {
        val app = process()
        val notificationService = process()
        app.storage.write(TokenSet("expired", NOW - 1.minutes, "refresh-0", NOW + 1.hours, "id"))

        val tokens = withContext(Dispatchers.Default) {
            listOf(app, notificationService)
                .map { process -> async { process.client.tokens.validAccessToken() } }
                .awaitAll()
        }

        assertEquals(1, refreshes.load())
        assertEquals(listOf("rotated-1", "rotated-1"), tokens)
        assertEquals("refresh-1", notificationService.storage.read()?.refreshToken)
    }

    /** One process: its own storage object, lock object and client over the shared Keychain and lock files. */
    private fun process(): ProcessSession {
        val storage = KeychainTokenStorage(keychain, FixedClock, RecordingAppLog())
        val client = MyItmoClientFactory.create(
            storage = storage,
            engine = identity,
            clock = FixedClock,
            refreshGuard = FileCrossProcessLock(locks).myItmoRefreshGuard()
        )
        return ProcessSession(storage, client)
    }

    private class ProcessSession(val storage: KeychainTokenStorage, val client: MyItmoClient)

    /** The Keychain as both processes see it: one item set, safe from any thread. */
    private class SharedKeychain : SecureStore {
        private val guard = SynchronizedObject()
        private val values = HashMap<String, String>()

        override fun read(name: String): String? = synchronized(guard) { values[name] }

        override fun write(name: String, value: String) = synchronized(guard) { values[name] = value }

        override fun delete(name: String) = synchronized(guard) { values.remove(name); Unit }
    }

    private object FixedClock : Clock {
        override fun now(): Instant = NOW
    }

    private companion object {
        val NOW: Instant = Instant.fromEpochMilliseconds(1_790_000_000_000)
        val REFRESH_TIME = 50.milliseconds
    }
}
