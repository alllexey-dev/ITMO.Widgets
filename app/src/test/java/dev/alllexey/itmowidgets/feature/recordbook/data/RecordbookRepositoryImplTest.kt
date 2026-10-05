package dev.alllexey.itmowidgets.feature.recordbook.data

import dev.alllexey.itmoapi.itmoid.TokenSet
import dev.alllexey.itmoapi.itmoid.TokenStorage
import dev.alllexey.itmowidgets.core.network.MyItmoClientFactory
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.testing.FakeDemoMode
import dev.alllexey.itmowidgets.core.testing.FixedAcademicTime
import dev.alllexey.itmowidgets.core.testing.MainDispatcherRule
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookControl
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookPeriod
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookProgram
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookSubject
import dev.alllexey.itmowidgets.testkit.FakeClock
import dev.alllexey.itmowidgets.testkit.respondJson
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * The recordbook through MyItmoApi 2.x, with a MockEngine for my.itmo.ru and ITMO.ID. The golden answers are the
 * vendored `myitmo/recordbook/` fixtures; the expected models are what the 1.x Gson path produced from them.
 */
class RecordbookRepositoryImplTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val dispatchers = mainDispatcherRule.appDispatchers
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

    @Test
    fun `golden programs map to the same periods as 1_x`() = runTest {
        val repository = repository(mapOf(SPECIALIZATIONS to fixture("specializations.json")))

        val programs = (repository.getPrograms() as AppResult.Success).value

        assertEquals(
            listOf(
                RecordbookProgram(
                    id = 50001,
                    name = "Синтетическая программа",
                    periods = listOf(
                        RecordbookPeriod(studyYear = "2025/2026", semester = 3, course = 2, actual = true),
                        RecordbookPeriod(studyYear = "2024/2025", semester = 2, course = 1, actual = false)
                    )
                )
            ),
            programs
        )
        assertEquals("Bearer $STORED_ACCESS", requests.single().headers[HttpHeaders.Authorization])
    }

    @Test
    fun `golden subjects with and without points map to the same subjects as 1_x`() = runTest {
        val repository = repository(mapOf("/api/record_book/50001/3" to fixture("record-book.json")))

        val subjects = (repository.getSubjects(50001, 3) as AppResult.Success).value

        assertEquals(
            listOf(
                RecordbookSubject(
                    name = "Тестовая дисциплина",
                    disciplineId = 70001,
                    entryId = 60001,
                    controlType = "Экзамен",
                    score = 71.5,
                    rate = "4/C",
                    attempt = 1,
                    examDate = Instant.parse("2026-01-15T07:00:00Z"),
                    hasDetails = true,
                    teacherName = "Тестовый Преподаватель Синтетический",
                    lmsLink = null
                ),
                RecordbookSubject(
                    name = "Вторая дисциплина",
                    disciplineId = 70002,
                    entryId = 60002,
                    controlType = "Зачёт",
                    score = null,
                    rate = null,
                    attempt = 0,
                    examDate = null,
                    hasDetails = false,
                    teacherName = null,
                    lmsLink = null
                )
            ),
            subjects
        )
    }

    @Test
    fun `golden controls keep the tree, the pending result and partial teacher names`() = runTest {
        val repository = repository(mapOf("/api/record_book/60001" to fixture("controls.json")))

        val controls = (repository.getControls(60001) as AppResult.Success).value

        assertEquals(
            listOf(
                RecordbookControl(
                    id = 81001,
                    name = "Синтетический контроль",
                    score = 71.5,
                    minimum = 60.0,
                    maximum = 100.0,
                    required = true,
                    date = Instant.parse("2026-01-15T07:00:00Z"),
                    teacherName = "Тестовый Преподаватель Синтетический",
                    parentId = null
                ),
                RecordbookControl(
                    id = 81002,
                    name = "Синтетическая работа",
                    score = null,
                    minimum = 0.0,
                    maximum = 20.0,
                    required = false,
                    date = null,
                    teacherName = "Тестовый",
                    parentId = 81001
                )
            ),
            controls
        )
    }

    @Test
    fun `golden absence of a result is a subject without score, date, teacher or link`() = runTest {
        val repository = repository(mapOf("/api/record_book/50001/1" to fixture("absence.json")))

        val subject = (repository.getSubjects(50001, 1) as AppResult.Success).value.single()

        assertEquals(60003L, subject.entryId)
        assertNull(subject.score)
        assertNull(subject.rate)
        assertEquals(0, subject.attempt)
        assertNull(subject.examDate)
        assertNull(subject.teacherName)
        assertNull(subject.lmsLink)
        assertFalse(subject.hasDetails)
        assertFalse(subject.absent)
    }

    @Test
    fun `golden empty answer is an empty list, not a failure`() = runTest {
        val repository = repository(mapOf(SPECIALIZATIONS to fixture("empty.json")))

        assertEquals(AppResult.Success(emptyList<RecordbookProgram>()), repository.getPrograms())
        assertEquals(emptyList<RecordbookProgram>(), repository.cachedPrograms())
    }

    @Test
    fun `explicit nulls and blank parts stay absent rather than coerced`() = runTest {
        val repository = repository(
            mapOf(
                "/api/record_book/123/2" to """{"error_code":0,"result":[{"name":"  Тестовый предмет  ","discipline_id":1,"est_id":2,"control_type":" Зачет ","current_score":null,"rate":null,"attempt":null,"have_tree":false,"lms_link":"  ","teacher":{"name":null,"surname":" ","patronymic":null}}]}""",
                "/api/record_book/2" to """{"error_code":0,"result":[{"id":3,"parent_id":1,"control_name":" Работа ","min_value":null,"max_value":null,"rate":null,"required":true,"teacher":{"surname":" Тестовый ","name":" Преподаватель ","patronymic":null}}]}"""
            )
        )

        val subject = (repository.getSubjects(123, 2) as AppResult.Success).value.single()
        val control = (repository.getControls(2) as AppResult.Success).value.single()

        assertEquals("Тестовый предмет", subject.name)
        assertEquals("Зачет", subject.controlType)
        assertNull(subject.score)
        assertEquals(0, subject.attempt)
        assertNull(subject.teacherName)
        assertNull(subject.lmsLink)
        assertEquals("Работа", control.name)
        assertEquals(1L, control.parentId)
        assertNull(control.minimum)
        assertNull(control.maximum)
        assertNull(control.score)
        assertEquals("Тестовый Преподаватель", control.teacherName)
    }

    @Test
    fun `successful answers stay in memory until the session is cleared`() = runTest {
        val repository = repository(
            mapOf(
                SPECIALIZATIONS to fixture("specializations.json"),
                "/api/record_book/50001/3" to fixture("record-book.json"),
                "/api/record_book/60001" to fixture("controls.json")
            )
        )
        assertNull(repository.cachedPrograms())
        assertNull(repository.cachedSubjects(50001, 3))
        assertNull(repository.cachedControls(60001))

        val programs = (repository.getPrograms() as AppResult.Success).value
        val subjects = (repository.getSubjects(50001, 3) as AppResult.Success).value
        val controls = (repository.getControls(60001) as AppResult.Success).value
        assertEquals(programs, repository.cachedPrograms())
        assertEquals(subjects, repository.cachedSubjects(50001, 3))
        assertEquals(controls, repository.cachedControls(60001))
        assertNull(repository.cachedSubjects(50001, 2))
        assertEquals(3, requests.size)

        repository.clearSessionData()
        assertNull(repository.cachedPrograms())
        assertNull(repository.cachedSubjects(50001, 3))
        assertNull(repository.cachedControls(60001))
    }

    @Test
    fun `HTTP 200 authorization error is not an empty result`() = runTest {
        val repository = repository(mapOf(SPECIALIZATIONS to """{"error_code":403,"result":null}"""))

        assertEquals(AppResult.Failure(AppError.Forbidden), repository.getPrograms())
        assertNull(repository.cachedPrograms())
    }

    @Test
    fun `a 401 after the refresh needs a new sign-in with one refresh in total`() = runTest {
        val repository = repository(mapOf(SPECIALIZATIONS to UNAUTHORIZED))

        assertEquals(AppResult.Failure(AppError.Unauthorized), repository.getPrograms())
        assertEquals(listOf(MY_HOST, ID_HOST, MY_HOST), hosts())
        assertNull(repository.cachedPrograms())
    }

    @Test
    fun `a 5xx fails at once without a refresh and keeps the cached programs`() = runTest {
        val answers = mutableMapOf(SPECIALIZATIONS to fixture("specializations.json"))
        val repository = repository(answers)
        val cached = (repository.getPrograms() as AppResult.Success).value
        answers[SPECIALIZATIONS] = BAD_GATEWAY
        requests.clear()

        val result = repository.getPrograms()

        assertTrue(result.toString(), (result as AppResult.Failure).error is AppError.Unknown)
        assertEquals(listOf(MY_HOST), hosts())
        assertEquals(cached, repository.cachedPrograms())
    }

    @Test
    fun `the demo recordbook costs no request`() = runTest {
        val repository = repository(emptyMap(), demo = true)

        val program = (repository.getPrograms() as AppResult.Success).value.single()
        val semester = program.periods.single { it.actual }.semester
        val subject = (repository.getSubjects(program.id, semester) as AppResult.Success).value.first()
        repository.getControls(subject.entryId)

        assertEquals(emptyList<HttpRequestData>(), requests)
    }

    /** my.itmo.ru answers by path from [answers]; ITMO.ID answers every refresh with a new token pair. */
    private fun repository(answers: Map<String, String>, demo: Boolean = false): RecordbookRepositoryImpl {
        val engine = MockEngine { request ->
            requests += request
            when (request.url.host) {
                ID_HOST -> respondJson(TOKEN_SUCCESS)
                MY_HOST -> answer(
                    answers[request.url.encodedPath]
                        ?: throw AssertionError("Unexpected MyITMO request ${request.url.encodedPath}")
                )
                else -> throw AssertionError("Unexpected host ${request.url.host}")
            }
        }
        val client = MyItmoClientFactory.create(storage = storage, engine = engine, clock = clock)
        return RecordbookRepositoryImpl(client, FixedAcademicTime(), FakeDemoMode(active = demo), dispatchers)
    }

    private fun MockRequestHandleScope.answer(body: String): HttpResponseData = when (body) {
        UNAUTHORIZED -> respondJson("{}", HttpStatusCode.Unauthorized)
        BAD_GATEWAY -> respond("<html><body>Bad Gateway</body></html>", HttpStatusCode.BadGateway)
        else -> respondJson(body)
    }

    private fun hosts(): List<String> = requests.map { it.url.host }

    private fun fixture(name: String): String =
        checkNotNull(javaClass.getResourceAsStream("/myitmo/recordbook/$name")) { "No fixture $name" }
            .use { it.readBytes().decodeToString() }

    private class InMemoryTokenStorage(private var tokens: TokenSet?) : TokenStorage {
        override suspend fun read(): TokenSet? = tokens

        override suspend fun write(tokens: TokenSet?) {
            this.tokens = tokens
        }
    }

    private companion object {
        const val MY_HOST = "my.itmo.ru"
        const val ID_HOST = "id.itmo.ru"
        const val SPECIALIZATIONS = "/api/record_book/specializations"
        const val STORED_ACCESS = "stored-access"
        const val UNAUTHORIZED = "401"
        const val BAD_GATEWAY = "502"
        const val TOKEN_SUCCESS = """{"access_token":"refreshed-access","expires_in":300,""" +
            """"refresh_token":"refreshed-refresh","refresh_expires_in":3600,""" +
            """"id_token":"header.payload.signature","session_state":"00000000-0000-4000-8000-000000000001"}"""
    }
}
