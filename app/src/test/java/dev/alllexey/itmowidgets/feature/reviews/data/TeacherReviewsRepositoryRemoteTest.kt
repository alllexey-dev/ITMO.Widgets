package dev.alllexey.itmowidgets.feature.reviews.data

import dev.alllexey.itmowidgets.core.demo.DemoMode
import dev.alllexey.itmowidgets.core.model.UserGroup
import dev.alllexey.itmowidgets.core.model.UserSharing
import dev.alllexey.itmowidgets.core.model.UserSummary
import dev.alllexey.itmowidgets.core.network.Core2Harness
import dev.alllexey.itmowidgets.core.network.Core2Harness.Companion.errorEnvelope
import dev.alllexey.itmowidgets.core.network.Core2Harness.Companion.session
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.reviews.OwnReviewStatus
import dev.alllexey.itmowidgets.core.reviews.OwnTeacherReview
import dev.alllexey.itmowidgets.core.reviews.ReviewDate
import dev.alllexey.itmowidgets.core.reviews.ReviewOrigin
import dev.alllexey.itmowidgets.core.reviews.ReviewReportReason
import dev.alllexey.itmowidgets.core.reviews.SummaryConfidence
import dev.alllexey.itmowidgets.core.reviews.SummaryScale
import dev.alllexey.itmowidgets.core.reviews.SummaryScaleKind
import dev.alllexey.itmowidgets.core.reviews.SummaryScaleValue
import dev.alllexey.itmowidgets.core.reviews.SummaryTag
import dev.alllexey.itmowidgets.core.reviews.TeacherLevel
import dev.alllexey.itmowidgets.core.reviews.TeacherReview
import dev.alllexey.itmowidgets.core.reviews.TeacherReviewDraft
import dev.alllexey.itmowidgets.core.reviews.TeacherReviews
import dev.alllexey.itmowidgets.core.reviews.TeacherSummary
import dev.alllexey.itmowidgets.core.testing.FakeBackendGate
import dev.alllexey.itmowidgets.core.testing.FakeDemoMode
import dev.alllexey.itmowidgets.core.testing.FixedAcademicTime
import dev.alllexey.itmowidgets.core.testing.MainDispatcherRule
import dev.alllexey.itmowidgets.core.testing.noDemo
import dev.alllexey.itmowidgets.feature.reviews.data.ReviewsRemoteFixtures.ANONYMOUS_ID
import dev.alllexey.itmowidgets.feature.reviews.data.ReviewsRemoteFixtures.COPY_ID
import dev.alllexey.itmowidgets.feature.reviews.data.ReviewsRemoteFixtures.MINE_ID
import dev.alllexey.itmowidgets.feature.reviews.data.ReviewsRemoteFixtures.NAMED_ID
import dev.alllexey.itmowidgets.feature.reviews.data.ReviewsRemoteFixtures.TEACHER_ISU
import dev.alllexey.itmowidgets.testkit.bodyText
import dev.alllexey.itmowidgets.testkit.respondJson
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.YearMonth
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import okio.Path.Companion.toOkioPath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * `TeacherReviewsRepositoryImpl` and `TeacherLevelsRepositoryImpl` over the real Core 2.0 client and a MockEngine:
 * routes, bodies, mapping, errors and the gates.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TeacherReviewsRepositoryRemoteTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule
    val temporary = TemporaryFolder()

    private val dispatchers = mainDispatcherRule.appDispatchers

    @Test
    fun `a teacher's reviews map both kinds, the own review and the summary`() = runTest {
        val harness = Core2Harness(session()) { respondJson(ReviewsRemoteFixtures.REVIEWS) }

        val reviews = (reviewsRepository(harness, backgroundScope).reviews(TEACHER_ISU) as AppResult.Success).value

        val request = harness.backendRequests.single()
        assertEquals(HttpMethod.Get, request.method)
        assertEquals("/api/teachers/$TEACHER_ISU/reviews", request.url.encodedPath)
        assertEquals("Bearer stored-access", request.headers[HttpHeaders.Authorization])
        assertEquals(EXPECTED_REVIEWS, reviews)
    }

    @Test
    fun `save, delete, vote and report send their routes and bodies and publish the updated reviews`() = runTest {
        val harness = Core2Harness(session()) { respondJson(ReviewsRemoteFixtures.AFTER_MUTATION) }
        val repository = reviewsRepository(harness, backgroundScope)
        val updates = mutableListOf<TeacherReviews>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { repository.observeUpdates().toList(updates) }
        val draft = TeacherReviewDraft(" Матанализ ", " $VALID_TEXT\r\nВторая строка ", anonymous = false, flowIds = setOf(7002, 7001))

        val results = listOf(
            repository.save(TEACHER_ISU, draft),
            repository.delete(TEACHER_ISU),
            repository.vote(TEACHER_ISU, NAMED_ID, -1),
            repository.report(TEACHER_ISU, ANONYMOUS_ID, ReviewReportReason.WRONG_TEACHER, "  Это другой преподаватель  "),
            repository.report(TEACHER_ISU, ANONYMOUS_ID, ReviewReportReason.OFFENSIVE, " "),
        )

        assertEquals(
            listOf(
                "PUT /api/teachers/$TEACHER_ISU/reviews/mine",
                "DELETE /api/teachers/$TEACHER_ISU/reviews/mine",
                "PUT /api/reviews/$NAMED_ID/vote",
                "POST /api/reviews/$ANONYMOUS_ID/report",
                "POST /api/reviews/$ANONYMOUS_ID/report",
            ),
            harness.backendRequests.map { "${it.method.value} ${it.url.encodedPath}" },
        )
        assertEquals(
            listOf(
                """{"subjectTitle":"Матанализ","text":"$VALID_TEXT\nВторая строка","anonymous":false,"flowIds":[7001,7002]}""",
                "",
                """{"value":-1}""",
                """{"reason":"WRONG_TEACHER","comment":"Это другой преподаватель"}""",
                """{"reason":"OFFENSIVE"}""",
            ).map(::json),
            harness.backendRequests.map { json(it.bodyText()) },
        )
        val expected = AppResult.Success(AFTER_MUTATION)
        assertEquals(List(5) { expected }, results)
        runCurrent()
        assertEquals(List(5) { AFTER_MUTATION }, updates)
        assertEquals(AFTER_MUTATION, repository.cachedReviews(TEACHER_ISU))
    }

    @Test
    fun `Backend errors map to the released AppErrors and keep the cache`() = runTest {
        var status = HttpStatusCode.OK
        var body = ReviewsRemoteFixtures.REVIEWS
        val harness = Core2Harness(session()) { respondJson(body, status) }
        val repository = reviewsRepository(harness, backgroundScope)
        val cached = (repository.reviews(TEACHER_ISU) as AppResult.Success).value
        val cases = listOf(
            Triple(HttpStatusCode.Unauthorized, errorEnvelope("unauthorized"), AppError.Unauthorized),
            Triple(HttpStatusCode.Forbidden, errorEnvelope("restricted"), AppError.Restricted),
            Triple(HttpStatusCode.Forbidden, errorEnvelope("permission_denied"), AppError.Forbidden),
            Triple(HttpStatusCode.NotFound, errorEnvelope("not_found"), AppError.NotFound),
        )

        cases.forEach { (code, envelope, error) ->
            status = code
            body = envelope
            assertEquals(code.toString(), AppResult.Failure(error), repository.reviews(TEACHER_ISU))
            assertEquals(code.toString(), AppResult.Failure(error), repository.vote(TEACHER_ISU, NAMED_ID, 1))
        }
        listOf(HttpStatusCode.Conflict, HttpStatusCode.InternalServerError, HttpStatusCode.BadGateway).forEach { code ->
            status = code
            body = errorEnvelope("business_rule_violation")
            assertTrue(code.toString(), (repository.reviews(TEACHER_ISU) as AppResult.Failure).error is AppError.Unknown)
        }

        assertEquals(1 + cases.size * 2 + 3, harness.backendRequests.size)
        assertTrue(harness.requests.none { it.url.host == Core2Harness.ITMO_ID_HOST })
        assertEquals(cached, repository.cachedReviews(TEACHER_ISU))
    }

    @Test
    fun `51 teachers go in two sorted batches with a repeated isu parameter and an out-of-range ISU is never sent`() = runTest {
        val isus = (100_001..100_051).toList()
        val harness = Core2Harness(session()) { request ->
            val asked = request.url.parameters.getAll("isu").orEmpty().map(String::toInt)
            respondJson(ReviewsRemoteFixtures.levels(asked.filter { it % 10 == 0 }.associateWith { "NEGATIVE" } +
                asked.filter { it == 100_051 }.associateWith { "SOMETHING_NEW" }))
        }

        val levels = levelsRepository(harness).levels(isus.shuffled().toSet() + setOf(99_999, 10_000_000, 0))

        assertEquals((100_010..100_050 step 10).associateWith { TeacherLevel.NEGATIVE }, levels)
        val requests = harness.backendRequests
        assertEquals(2, requests.size)
        assertTrue(requests.all { it.method == HttpMethod.Get && it.url.encodedPath == "/api/teachers/summary-levels" })
        assertEquals(isus.take(50).joinToString("&") { "isu=$it" }, requests[0].url.encodedQuery)
        assertEquals("isu=100051", requests[1].url.encodedQuery)
        assertTrue(requests.all { it.headers[HttpHeaders.Authorization] == "Bearer stored-access" })
    }

    @Test
    fun `a failed second batch writes nothing`() = runTest {
        val harness = Core2Harness(session()) { request ->
            val asked = request.url.parameters.getAll("isu").orEmpty().map(String::toInt)
            if (100_051 in asked) respondJson(errorEnvelope("internal_error"), HttpStatusCode.InternalServerError)
            else respondJson(ReviewsRemoteFixtures.levels(mapOf(100_001 to "POSITIVE")))
        }

        val levels = levelsRepository(harness).levels((100_001..100_051).toSet())

        assertEquals(emptyMap<Int, TeacherLevel>(), levels)
        assertEquals(2, harness.backendRequests.size)
        assertFalse(File(temporary.root, "teacher_levels/levels.json").exists())
    }

    @Test
    fun `without the opt-in or in the demo nothing reaches Backend`() = runTest {
        val harness = Core2Harness(session()) { throw AssertionError("Unexpected ${it.method.value} ${it.url}") }
        val offline = reviewsRepository(harness, backgroundScope, gate = FakeBackendGate(optedIn = false))
        val demo = FakeDemoMode(active = true)
        val demoReviews = reviewsRepository(harness, backgroundScope, gate = FakeBackendGate(optedIn = false, demo), demo = demo)

        assertEquals(AppResult.Failure(AppError.CustomServicesDisabled), offline.reviews(TEACHER_ISU))
        assertEquals(AppResult.Failure(AppError.CustomServicesDisabled), offline.vote(TEACHER_ISU, NAMED_ID, 1))
        assertEquals(emptyMap<Int, TeacherLevel>(), levelsRepository(harness, gate = FakeBackendGate(optedIn = false)).levels(setOf(TEACHER_ISU)))
        assertTrue(demoReviews.reviews(TEACHER_ISU) is AppResult.Success)
        assertEquals(AppResult.Failure(AppError.DemoUnavailable), demoReviews.delete(TEACHER_ISU))
        levelsRepository(harness, gate = FakeBackendGate(optedIn = false, demo), demo = demo).levels(setOf(TEACHER_ISU))

        assertEquals(0, harness.requests.size)
    }

    private fun reviewsRepository(
        harness: Core2Harness,
        scope: CoroutineScope,
        gate: FakeBackendGate = FakeBackendGate(optedIn = true),
        demo: DemoMode = noDemo(),
    ) = TeacherReviewsRepositoryImpl(gate, harness.client.reviews, scope, FixedAcademicTime(), demo, dispatchers)

    private fun levelsRepository(
        harness: Core2Harness,
        gate: FakeBackendGate = FakeBackendGate(optedIn = true),
        demo: DemoMode = noDemo(),
    ) = TeacherLevelsRepositoryImpl(
        gate,
        harness.client.reviews,
        TeacherLevelsFileStore(File(temporary.root, "teacher_levels").toOkioPath()),
        FIXED_CLOCK,
        demo,
        dispatchers,
    )

    private fun json(text: String): JsonElement? = text.takeIf(String::isNotEmpty)?.let(Json::parseToJsonElement)

    private companion object {
        const val VALID_TEXT = "Понятно объясняет материал и отвечает на вопросы."

        val FIXED_CLOCK = object : Clock {
            override fun now(): Instant = Instant.parse("2026-10-06T10:00:00Z")
        }

        val EXPECTED_REVIEWS = TeacherReviews(
            isu = TEACHER_ISU,
            reviews = listOf(
                TeacherReview(
                    NAMED_ID, "Математический анализ", ReviewDate.Month(YearMonth(2026, 10)),
                    "Синтетический отзыв под именем.", 4, 1,
                    ReviewOrigin.Community(
                        verified = true,
                        author = UserSummary(
                            100002, "Друг Первый", "https://example.org/avatars/100002.jpg",
                            listOf(UserGroup("К3240", 2, "ФИТИП")), UserSharing(sport = false, schedule = true, friends = true),
                        ),
                        reportedByMe = false,
                    ),
                ),
                TeacherReview(
                    ANONYMOUS_ID, null, ReviewDate.Month(YearMonth(2026, 9)), "Синтетический анонимный отзыв.", -1, -1,
                    ReviewOrigin.Community(verified = false, author = null, reportedByMe = true),
                ),
                TeacherReview(
                    COPY_ID, "Программирование", ReviewDate.BeforeYear(2024), "Синтетическая копия отзыва.", 0, 0,
                    ReviewOrigin.Reviews("Reviews", "https://example.org/reviews/1"),
                ),
            ),
            mine = OwnTeacherReview(
                MINE_ID, "Математический анализ", "Мой синтетический отзыв.", anonymous = true,
                status = OwnReviewStatus.PUBLISHED, reviewNote = null, score = 2, verified = true,
                written = ReviewDate.Month(YearMonth(2026, 10)),
            ),
            canWrite = true,
            canVote = true,
            canReport = false,
            knownTeacher = true,
            summary = TeacherSummary(
                reviewCount = 7,
                description = "Синтетическое описание отзывов.",
                pros = listOf("Понятно объясняет"),
                cons = listOf("Строгие сроки"),
                tags = listOf(SummaryTag.STRICT_DEFENSE, SummaryTag.HARD_EXAM),
                scales = listOf(
                    SummaryScale(SummaryScaleKind.EXPLAINS, SummaryScaleValue.HIGH, "Синтетическое пояснение"),
                    SummaryScale(SummaryScaleKind.ATTITUDE, SummaryScaleValue.MEDIUM, "Синтетическое пояснение"),
                    SummaryScale(SummaryScaleKind.FAIRNESS, SummaryScaleValue.LOW, "Синтетическое пояснение"),
                    SummaryScale(SummaryScaleKind.STRICTNESS, SummaryScaleValue.NOT_ENOUGH_DATA, null),
                    SummaryScale(SummaryScaleKind.WORKLOAD, SummaryScaleValue.HIGH, "Синтетическое пояснение"),
                ),
                level = TeacherLevel.POSITIVE,
                confidence = SummaryConfidence.HIGH,
            ),
        )

        val AFTER_MUTATION = TeacherReviews(
            isu = TEACHER_ISU,
            reviews = emptyList(),
            mine = OwnTeacherReview(
                MINE_ID, null, "Мой синтетический отзыв после правки.", anonymous = false,
                status = OwnReviewStatus.PENDING, reviewNote = null, score = 0, verified = false,
                written = ReviewDate.Month(YearMonth(2026, 10)),
            ),
            canWrite = true,
            canVote = true,
            canReport = true,
            knownTeacher = true,
            summary = TeacherSummary(
                reviewCount = 3,
                description = "Синтетическое описание.",
                pros = emptyList(),
                cons = emptyList(),
                tags = emptyList(),
                scales = SummaryScaleKind.entries.map { SummaryScale(it, SummaryScaleValue.NOT_ENOUGH_DATA, null) },
                level = TeacherLevel.MIXED,
                confidence = SummaryConfidence.LOW,
            ),
        )
    }
}
