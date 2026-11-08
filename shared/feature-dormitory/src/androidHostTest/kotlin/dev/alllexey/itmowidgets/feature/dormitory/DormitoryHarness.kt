package dev.alllexey.itmowidgets.feature.dormitory

import dev.alllexey.itmoapi.itmoid.TokenSet
import dev.alllexey.itmoapi.itmoid.TokenStorage
import dev.alllexey.itmoapi.myitmo.MyItmoClient
import dev.alllexey.itmoapi.myitmo.MyItmoConfiguration
import dev.alllexey.itmowidgets.core.coroutines.AppDispatchers
import dev.alllexey.itmowidgets.core.session.CurrentUser
import dev.alllexey.itmowidgets.core.session.CurrentUserProvider
import dev.alllexey.itmowidgets.core.testing.FakeDemoMode
import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import dev.alllexey.itmowidgets.feature.dormitory.data.DormitoryFileStore
import dev.alllexey.itmowidgets.feature.dormitory.data.DormitoryRepositoryImpl
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
 * The dormitory over the real MyItmoApi client on a `MockEngine`: my.itmo.ru answers by path from [answers], the
 * stored token outlives every test. A fixed [clock], and a fake file system that outlives a [repository] as the
 * device's files outlive the process. [offline] makes every request fail before an answer.
 */
class DormitoryHarness(private val dispatcher: CoroutineDispatcher, demo: Boolean = false) {
    val clock = FakeClock(Instant.parse("2026-11-08T09:00:00Z"))
    val files: FileSystem = FakeFileSystem()
    val requests = mutableListOf<HttpRequestData>()
    val answers = mutableMapOf<String, String>()
    var offline = false
    var isu: Int? = OWNER
    val demoMode = FakeDemoMode(active = demo)

    // On the test's dispatcher, so `advanceUntilIdle` waits for the answers too.
    private val engine = MockEngine(
        MockEngineConfig().apply {
            dispatcher = this@DormitoryHarness.dispatcher
            addHandler { request ->
                requests += request
                if (offline) throw IOException("offline")
                when (request.url.host) {
                    "my.itmo.ru" -> respondJson(
                        answers[keyOf(request)] ?: throw AssertionError("Unexpected MyITMO request ${keyOf(request)}"),
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

    /** The academic time over [clock], in Moscow as in the app. */
    val time = object : AcademicTimeProvider {
        override val timeZone: TimeZone = TimeZone.of("Europe/Moscow")

        override fun today(): LocalDate = clock.now().toLocalDateTime(timeZone).date

        override fun now(): Instant = clock.now()
    }

    private val user = object : CurrentUserProvider {
        override suspend fun getCurrentUser(): CurrentUser? = CurrentUser(isu, null, null)
    }

    /** A new repository over the same files: a restart of the app. */
    fun repository() = DormitoryRepositoryImpl(
        client,
        demoMode,
        DormitoryFileStore("/no-backup/dormitory".toPath(), files),
        user,
        clock,
        time,
        AppDispatchers(io = dispatcher, default = dispatcher, main = dispatcher),
    )

    init {
        answers[PERIODS] = fixture("periods-empty.json")
    }

    fun answer(status: String, documents: String? = null) {
        answers[STATUS] = fixture(status)
        documents?.let { answers[DOCUMENTS] = fixture(it) }
    }

    /** The academic years in [periods] and, for each year that starts on a key of [contracts], its Договоры найма. */
    fun contracts(periods: String, contracts: Map<String, String>) {
        answers[PERIODS] = fixture(periods)
        contracts.forEach { (from, name) -> answers["$CONTRACTS?from=$from"] = fixture(name) }
    }

    private fun keyOf(request: HttpRequestData): String {
        val path = request.url.encodedPath
        return if (path == CONTRACTS) "$path?from=${request.url.parameters["from"]}" else path
    }

    fun paths(): List<String> = requests.filter { it.url.host == "my.itmo.ru" }.map { it.url.encodedPath }

    private fun fixture(name: String): String =
        checkNotNull(javaClass.getResourceAsStream("/myitmo/dormitory/$name")) { "No fixture $name" }
            .use { it.readBytes().decodeToString() }

    private class InMemoryTokenStorage(private var tokens: TokenSet?) : TokenStorage {

        override suspend fun read(): TokenSet? = tokens

        override suspend fun write(tokens: TokenSet?) {
            this.tokens = tokens
        }
    }

    companion object {
        const val OWNER = 100001
        const val STATUS = "/api/dormitory/status/full"
        const val DOCUMENTS = "/api/dormitory/documents"
        const val PERIODS = "/api/dormitory/payments/contracts/periods"
        const val CONTRACTS = "/api/dormitory/payments/contracts"
    }
}
