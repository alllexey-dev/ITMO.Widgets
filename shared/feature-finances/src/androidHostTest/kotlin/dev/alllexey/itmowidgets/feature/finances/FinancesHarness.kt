package dev.alllexey.itmowidgets.feature.finances

import dev.alllexey.itmoapi.itmoid.TokenSet
import dev.alllexey.itmoapi.itmoid.TokenStorage
import dev.alllexey.itmoapi.myitmo.MyItmoClient
import dev.alllexey.itmoapi.myitmo.MyItmoConfiguration
import dev.alllexey.itmowidgets.core.coroutines.AppDispatchers
import dev.alllexey.itmowidgets.core.session.CurrentUser
import dev.alllexey.itmowidgets.core.session.CurrentUserProvider
import dev.alllexey.itmowidgets.core.testing.FakeDemoMode
import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import dev.alllexey.itmowidgets.feature.finances.data.TuitionFileStore
import dev.alllexey.itmowidgets.feature.finances.data.TuitionRepositoryImpl
import dev.alllexey.itmowidgets.testkit.FakeClock
import dev.alllexey.itmowidgets.testkit.respondJson
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockEngineConfig
import io.ktor.client.request.HttpRequestData
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.io.IOException
import okio.FileSystem
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem

/**
 * Tuition over the real MyItmoApi client on a `MockEngine`: my.itmo.ru answers by path from [answers], the stored
 * token outlives every test. A fixed [clock] (8 November 2026, 12:00 in Moscow), and a fake file system that outlives
 * a [repository] as the device's files outlive the process. [offline] makes every request fail before an answer.
 */
class FinancesHarness(private val dispatcher: CoroutineDispatcher, demo: Boolean = false) {
    val clock = FakeClock(Instant.parse("2026-11-08T09:00:00Z"))

    /** The academic time over [clock], in Moscow. */
    val time = object : AcademicTimeProvider {
        override val timeZone: TimeZone = TimeZone.of("Europe/Moscow")

        override fun today(): LocalDate = now().toLocalDateTime(timeZone).date

        override fun now(): Instant = clock.now()
    }
    val files: FileSystem = FakeFileSystem()
    val requests = mutableListOf<HttpRequestData>()
    val answers = mutableMapOf<String, String>()
    var offline = false
    var isu: Int? = OWNER
    val demoMode = FakeDemoMode(active = demo)

    /** Runs once before the next my.itmo answer is sent, then clears itself: sign-out while a request is on its way. */
    var beforeAnswer: (suspend () -> Unit)? = null

    // On the test's dispatcher, so `advanceUntilIdle` waits for the answers too.
    private val engine = MockEngine(
        MockEngineConfig().apply {
            dispatcher = this@FinancesHarness.dispatcher
            addHandler { request ->
                requests += request
                if (offline) throw IOException("offline")
                beforeAnswer?.let { beforeAnswer = null; it() }
                when (request.url.host) {
                    "my.itmo.ru" -> respondJson(
                        answers[request.url.encodedPath]
                            ?: throw AssertionError("Unexpected MyITMO request ${request.url.encodedPath}"),
                    )
                    else -> throw AssertionError("Unexpected host ${request.url.host}")
                }
            }
        },
    )

    private val client = MyItmoClient(
        MyItmoConfiguration.DEFAULT,
        storage = InMemoryTokenStorage(TokenSet("stored-access", clock.now() + 365.days, "stored-refresh", clock.now() + 365.days, "header.payload.signature")),
        engine = engine,
        clock = clock,
    )

    private val user = object : CurrentUserProvider {
        override suspend fun getCurrentUser(): CurrentUser? = CurrentUser(isu, null, null)
    }

    /** A new repository over the same files: a restart of the app. */
    fun repository() = TuitionRepositoryImpl(
        client,
        demoMode,
        TuitionFileStore("/no-backup/finances".toPath(), files),
        user,
        clock,
        time,
        AppDispatchers(io = dispatcher, default = dispatcher, main = dispatcher),
    )

    /** A student on a budget place: my.itmo says tuition is not available. */
    fun budget() {
        answers[AVAILABILITY] = fixture("availability-false.json")
    }

    /** A student on paid study whose Договоры об обучении with their Платежи are in [payments]. */
    fun paid(payments: String) {
        answers[AVAILABILITY] = fixture("availability-true.json")
        answers[PAYMENTS] = fixture(payments)
    }

    fun paths(): List<String> = requests.filter { it.url.host == "my.itmo.ru" }.map { it.url.encodedPath }

    private fun fixture(name: String): String =
        checkNotNull(javaClass.getResourceAsStream("/myitmo/finances/$name")) { "No fixture $name" }
            .use { it.readBytes().decodeToString() }

    private class InMemoryTokenStorage(private var tokens: TokenSet?) : TokenStorage {

        override suspend fun read(): TokenSet? = tokens

        override suspend fun write(tokens: TokenSet?) {
            this.tokens = tokens
        }
    }

    companion object {
        const val OWNER = 100001
        const val AVAILABILITY = "/api/finances/edupayments/availability"
        const val PAYMENTS = "/api/finances/edupayments/payments"
    }
}
