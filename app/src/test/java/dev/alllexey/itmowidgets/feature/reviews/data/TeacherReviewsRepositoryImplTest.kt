package dev.alllexey.itmowidgets.feature.reviews.data

import dev.alllexey.itmowidgets.core.ItmoWidgetsApi
import dev.alllexey.itmowidgets.core.testing.FixedAcademicTime
import dev.alllexey.itmowidgets.core.testing.noDemo
import dev.alllexey.itmowidgets.core.model.ApiResponse
import dev.alllexey.itmowidgets.core.model.GroupData
import dev.alllexey.itmowidgets.core.model.UserCapabilities
import dev.alllexey.itmowidgets.core.model.UserData
import dev.alllexey.itmowidgets.core.model.resources.ModerationReportRequest
import dev.alllexey.itmowidgets.core.model.toUserSummary
import dev.alllexey.itmowidgets.core.model.resources.ReportReason
import dev.alllexey.itmowidgets.core.model.resources.ResourceVoteRequest
import dev.alllexey.itmowidgets.core.model.reviews.OwnTeacherReview as WireOwnReview
import dev.alllexey.itmowidgets.core.model.reviews.SaveTeacherReviewRequest
import dev.alllexey.itmowidgets.core.model.reviews.TeacherReview as WireReview
import dev.alllexey.itmowidgets.core.model.reviews.TeacherReviewKind
import dev.alllexey.itmowidgets.core.model.reviews.TeacherReviewStatus
import dev.alllexey.itmowidgets.core.model.reviews.TeacherReviewsResponse as WireTeacherReviews
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.reviews.OwnReviewStatus
import dev.alllexey.itmowidgets.core.reviews.ReviewDate
import dev.alllexey.itmowidgets.core.reviews.ReviewOrigin
import dev.alllexey.itmowidgets.core.reviews.ReviewReportReason
import dev.alllexey.itmowidgets.core.reviews.TeacherReview
import dev.alllexey.itmowidgets.core.reviews.TeacherReviewDraft
import dev.alllexey.itmowidgets.core.reviews.TeacherReviews
import dev.alllexey.itmowidgets.core.reviews.SummaryConfidence
import dev.alllexey.itmowidgets.core.reviews.SummaryScale
import dev.alllexey.itmowidgets.core.reviews.SummaryScaleKind
import dev.alllexey.itmowidgets.core.reviews.SummaryScaleValue
import dev.alllexey.itmowidgets.core.reviews.SummaryTag
import dev.alllexey.itmowidgets.core.reviews.TeacherLevel
import dev.alllexey.itmowidgets.core.reviews.TeacherSummary
import dev.alllexey.itmowidgets.core.model.reviews.SummaryConfidence as WireConfidence
import dev.alllexey.itmowidgets.core.model.reviews.SummaryLevel as WireLevel
import dev.alllexey.itmowidgets.core.model.reviews.SummaryScaleKind as WireScaleKind
import dev.alllexey.itmowidgets.core.model.reviews.SummaryScaleValue as WireScaleValue
import dev.alllexey.itmowidgets.core.model.reviews.TeacherSummary as WireSummary
import dev.alllexey.itmowidgets.core.model.reviews.TeacherSummaryScale as WireScale
import dev.alllexey.itmowidgets.core.services.CustomServicesRepository
import java.io.IOException
import java.lang.reflect.Proxy
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

@OptIn(ExperimentalCoroutinesApi::class)
class TeacherReviewsRepositoryImplTest {
    @Test
    fun `disabled or unknown opt-in exposes no cache and disabled requests never call Backend`() = runTest {
        val api = FakeApi()
        val repository = TeacherReviewsRepositoryImpl(FakeServices(false), api.instance, backgroundScope, FixedAcademicTime(), noDemo())

        assertNull(repository.cachedReviews(100001))
        assertEquals(AppResult.Failure(AppError.CustomServicesDisabled), repository.reviews(100001))
        runCurrent()

        assertEquals(0, api.calls)
        assertNull(repository.cachedReviews(100001))
    }

    @Test
    fun `maps exact date before year and absent date preserving Backend order`() = runTest {
        val api = FakeApi().apply {
            result = result!!.copy(reviews = listOf(
                review(1).copy(writtenOn = LocalDate.of(2025, 1, 25)),
                review(2).copy(writtenBeforeYear = 2024),
                review(3),
            ))
        }
        val repository = TeacherReviewsRepositoryImpl(FakeServices(true), api.instance, backgroundScope, FixedAcademicTime(), noDemo())

        val reviews = (repository.reviews(100001) as AppResult.Success).value

        assertEquals(listOf(100001), api.requestedIsus)
        assertEquals(100001, reviews.isu)
        assertEquals(listOf(UUID(0, 1).toString(), UUID(0, 2).toString(), UUID(0, 3).toString()), reviews.reviews.map { it.id })
        assertEquals(listOf(ReviewDate.Month(YearMonth.of(2025, 1)), ReviewDate.BeforeYear(2024), null), reviews.reviews.map { it.written })
        assertEquals(reviews, repository.cachedReviews(100001))
    }

    @Test
    fun `keeps https source links and falls back for absent unsafe and malformed links`() = runTest {
        val links = listOf(" https://example.test/source ", "http://example.test/source", null, " ", "not a url", "https:///missing-host")
        val api = FakeApi().apply {
            result = result!!.copy(reviews = links.mapIndexed { index, link -> review(index + 1).copy(sourceLink = link) })
        }
        val repository = TeacherReviewsRepositoryImpl(FakeServices(true), api.instance, backgroundScope, FixedAcademicTime(), noDemo())

        val reviews = (repository.reviews(100001) as AppResult.Success).value

        assertEquals(listOf("https://example.test/source") + List(5) { PROVIDER_URL }, reviews.reviews.map { it.source().sourceUrl })
    }

    @Test
    fun `trims optional strings drops blank reviews and preserves full text and order`() = runTest {
        val api = FakeApi().apply {
            result = result!!.copy(reviews = listOf(
                review(1).copy(subjectTitle = " Предмет ", sourceTitle = " Источник ", text = " Первый\nвторой абзац "),
                review(2).copy(text = " \n\t "),
                review(3).copy(subjectTitle = " ", sourceTitle = " ", text = " Третий "),
            ))
        }
        val repository = TeacherReviewsRepositoryImpl(FakeServices(true), api.instance, backgroundScope, FixedAcademicTime(), noDemo())

        val reviews = (repository.reviews(100001) as AppResult.Success).value.reviews

        assertEquals(listOf(UUID(0, 1).toString(), UUID(0, 3).toString()), reviews.map { it.id })
        assertEquals(listOf("Первый\nвторой абзац", "Третий"), reviews.map { it.text })
        assertEquals("Предмет", reviews[0].subject)
        assertEquals("Источник", reviews[0].source().sourceTitle)
        assertNull(reviews[1].subject)
        assertNull(reviews[1].source().sourceTitle)
    }

    @Test
    fun `HTTP and network failures preserve the successful cache`() = runTest {
        val api = FakeApi().apply { result = result!!.copy(reviews = listOf(review(1))) }
        val repository = TeacherReviewsRepositoryImpl(FakeServices(true), api.instance, backgroundScope, FixedAcademicTime(), noDemo())
        val cached = (repository.reviews(100001) as AppResult.Success).value

        api.failure = HttpException(Response.error<Any>(404, "{}".toResponseBody()))
        assertEquals(AppResult.Failure(AppError.NotFound), repository.reviews(100001))
        assertEquals(cached, repository.cachedReviews(100001))
        api.failure = IOException("Synthetic offline response")
        assertEquals(AppResult.Failure(AppError.Network), repository.reviews(100001))
        assertEquals(cached, repository.cachedReviews(100001))
    }

    @Test
    fun `an absent Backend payload is a failure instead of an empty successful list`() = runTest {
        val api = FakeApi().apply { result = null }
        val repository = TeacherReviewsRepositoryImpl(FakeServices(true), api.instance, backgroundScope, FixedAcademicTime(), noDemo())

        assertTrue((repository.reviews(100001) as AppResult.Failure).error is AppError.Unknown)
        assertNull(repository.cachedReviews(100001))
    }

    @Test
    fun `maps the whole summary skipping unknown tags and blank items`() = runTest {
        val api = FakeApi().apply { result = result!!.copy(summary = wireSummary()) }
        val repository = TeacherReviewsRepositoryImpl(FakeServices(true), api.instance, backgroundScope, FixedAcademicTime(), noDemo())

        val summary = (repository.reviews(100001) as AppResult.Success).value.summary

        assertEquals(
            TeacherSummary(
                reviewCount = 12,
                description = "Понятно объясняет, но строго принимает лабораторные.",
                pros = listOf("Понятные лекции"),
                cons = listOf("Строгая защита"),
                tags = listOf(SummaryTag.MANY_LABS, SummaryTag.STRICT_DEFENSE),
                scales = listOf(
                    SummaryScale(SummaryScaleKind.EXPLAINS, SummaryScaleValue.HIGH, "Хвалят лекции"),
                    SummaryScale(SummaryScaleKind.ATTITUDE, SummaryScaleValue.MEDIUM, "Ровное отношение"),
                    SummaryScale(SummaryScaleKind.FAIRNESS, SummaryScaleValue.HIGH, "Оценки честные"),
                    SummaryScale(SummaryScaleKind.STRICTNESS, SummaryScaleValue.HIGH, "Строгая защита"),
                    SummaryScale(SummaryScaleKind.WORKLOAD, SummaryScaleValue.NOT_ENOUGH_DATA, null),
                ),
                level = TeacherLevel.POSITIVE,
                confidence = SummaryConfidence.MEDIUM,
            ),
            summary,
        )
        assertTrue(summary!!.showsLevel)
    }

    @Test
    fun `a reply without a summary or with a blank description has no summary`() = runTest {
        val api = FakeApi()
        val repository = TeacherReviewsRepositoryImpl(FakeServices(true), api.instance, backgroundScope, FixedAcademicTime(), noDemo())

        assertNull((repository.reviews(100001) as AppResult.Success).value.summary)
        api.result = api.result!!.copy(summary = wireSummary().copy(description = " \n "))
        assertNull((repository.reviews(100001) as AppResult.Success).value.summary)
    }

    @Test
    fun `a low confidence summary hides its level`() = runTest {
        val api = FakeApi().apply { result = result!!.copy(summary = wireSummary().copy(confidence = WireConfidence.LOW)) }
        val repository = TeacherReviewsRepositoryImpl(FakeServices(true), api.instance, backgroundScope, FixedAcademicTime(), noDemo())

        val summary = (repository.reviews(100001) as AppResult.Success).value.summary!!

        assertEquals(SummaryConfidence.LOW, summary.confidence)
        assertFalse(summary.showsLevel)
    }

    @Test
    fun `opting out clears warm cache and reconnect does not resurrect it`() = runTest {
        val services = FakeServices(true)
        val api = FakeApi()
        val repository = TeacherReviewsRepositoryImpl(services, api.instance, backgroundScope, FixedAcademicTime(), noDemo())
        repository.reviews(100001)
        assertNotNull(repository.cachedReviews(100001))

        services.enabled.value = false
        runCurrent()
        assertNull(repository.cachedReviews(100001))
        services.enabled.value = true
        runCurrent()

        assertNull(repository.cachedReviews(100001))
        assertEquals(1, api.calls)
    }

    @Test
    fun `direct disabled gate clears caches before the observer runs`() = runTest {
        val services = FakeServices(true)
        val api = FakeApi()
        val repository = TeacherReviewsRepositoryImpl(services, api.instance, backgroundScope, FixedAcademicTime(), noDemo())
        repository.reviews(100001)

        services.enabled.value = false
        assertEquals(AppResult.Failure(AppError.CustomServicesDisabled), repository.reviews(100001))
        services.enabled.value = true
        runCurrent()

        assertNull(repository.cachedReviews(100001))
        assertEquals(1, api.calls)
    }

    @Test
    fun `clearing session data forgets cached reviews`() = runTest {
        val api = FakeApi()
        val repository = TeacherReviewsRepositoryImpl(FakeServices(true), api.instance, backgroundScope, FixedAcademicTime(), noDemo())
        repository.reviews(100001)
        assertNotNull(repository.cachedReviews(100001))

        repository.clearSessionData()

        assertNull(repository.cachedReviews(100001))
    }

    @Test
    fun `pending responses cannot report success or restore cache after opting out`() = runTest {
        val services = FakeServices(true)
        val api = FakeApi()
        val repository = TeacherReviewsRepositoryImpl(services, api.instance, backgroundScope, FixedAcademicTime(), noDemo())
        repository.reviews(100001)
        val gate = ResponseGate()
        api.beforeResponse = gate::await
        val pending = async { repository.reviews(100001) }
        gate.entered.await()

        services.enabled.value = false
        runCurrent()
        gate.open()

        assertEquals(AppResult.Failure(AppError.CustomServicesDisabled), pending.await())
        assertNull(repository.cachedReviews(100001))
    }

    @Test
    fun `pre-disconnect responses cannot replace fresh reviews after reconnect`() = runTest {
        val services = FakeServices(true)
        val api = FakeApi().apply { result = result!!.copy(reviews = listOf(review(1))) }
        val repository = TeacherReviewsRepositoryImpl(services, api.instance, backgroundScope, FixedAcademicTime(), noDemo())
        val gate = ResponseGate()
        api.beforeResponse = gate::await
        val pending = async { repository.reviews(100001) }
        gate.entered.await()
        services.enabled.value = false
        runCurrent()
        services.enabled.value = true
        runCurrent()
        assertNull(repository.cachedReviews(100001))

        api.beforeResponse = {}
        api.result = api.result!!.copy(reviews = listOf(review(2)))
        val fresh = (repository.reviews(100001) as AppResult.Success).value
        gate.open()

        assertEquals(AppResult.Failure(AppError.CustomServicesDisabled), pending.await())
        assertEquals(fresh, repository.cachedReviews(100001))
        assertEquals(UUID(0, 2).toString(), fresh.reviews.single().id)
    }

    @Test
    fun `pre-clear responses cannot replace fresh session cache`() = runTest {
        val api = FakeApi().apply { result = result!!.copy(reviews = listOf(review(1))) }
        val repository = TeacherReviewsRepositoryImpl(FakeServices(true), api.instance, backgroundScope, FixedAcademicTime(), noDemo())
        val gate = ResponseGate()
        api.beforeResponse = gate::await
        val pending = async { repository.reviews(100001) }
        gate.entered.await()

        repository.clearSessionData()
        api.beforeResponse = {}
        api.result = api.result!!.copy(reviews = listOf(review(2)))
        val fresh = (repository.reviews(100001) as AppResult.Success).value
        gate.open()

        assertEquals(AppResult.Failure(AppError.CustomServicesDisabled), pending.await())
        assertEquals(fresh, repository.cachedReviews(100001))
    }

    @Test
    fun `initial enabled observation accepts a matching suspended gate read`() = runTest {
        val services = FakeServices(true)
        val gate = CompletableDeferred<Unit>()
        services.readGate = { gate.await() }
        val api = FakeApi()
        val repository = TeacherReviewsRepositoryImpl(services, api.instance, backgroundScope, FixedAcademicTime(), noDemo())
        val pending = async(start = CoroutineStart.UNDISPATCHED) { repository.reviews(100001) }
        runCurrent()
        assertEquals(0, api.calls)

        gate.complete(Unit)

        assertTrue(pending.await() is AppResult.Success)
        assertNotNull(repository.cachedReviews(100001))
    }

    @Test
    fun `a suspended enabled read cannot reach Backend after disconnect and reconnect`() = runTest {
        val services = FakeServices(true)
        val api = FakeApi()
        val repository = TeacherReviewsRepositoryImpl(services, api.instance, backgroundScope, FixedAcademicTime(), noDemo())
        runCurrent()
        val gate = CompletableDeferred<Unit>()
        services.readGate = { gate.await() }
        val pending = async(start = CoroutineStart.UNDISPATCHED) { repository.reviews(100001) }

        services.enabled.value = false
        runCurrent()
        services.enabled.value = true
        runCurrent()
        gate.complete(Unit)

        assertEquals(AppResult.Failure(AppError.CustomServicesDisabled), pending.await())
        assertNull(repository.cachedReviews(100001))
        assertEquals(0, api.calls)
    }

    @Test
    fun `a suspended disabled read cannot clear newer connected reviews`() = runTest {
        val services = FakeServices(false)
        val api = FakeApi()
        val repository = TeacherReviewsRepositoryImpl(services, api.instance, backgroundScope, FixedAcademicTime(), noDemo())
        runCurrent()
        val gate = CompletableDeferred<Unit>()
        services.readGate = { gate.await() }
        val pending = async(start = CoroutineStart.UNDISPATCHED) { repository.reviews(100001) }
        services.enabled.value = true
        runCurrent()
        services.readGate = null
        val fresh = (repository.reviews(100001) as AppResult.Success).value

        gate.complete(Unit)

        assertEquals(AppResult.Failure(AppError.CustomServicesDisabled), pending.await())
        assertEquals(fresh, repository.cachedReviews(100001))
        assertEquals(1, api.calls)
    }

    @Test
    fun `session cleanup invalidates a suspended opt-in read before the API call`() = runTest {
        val services = FakeServices(true)
        val api = FakeApi()
        val repository = TeacherReviewsRepositoryImpl(services, api.instance, backgroundScope, FixedAcademicTime(), noDemo())
        runCurrent()
        val gate = CompletableDeferred<Unit>()
        services.readGate = { gate.await() }
        val pending = async(start = CoroutineStart.UNDISPATCHED) { repository.reviews(100001) }

        repository.clearSessionData()
        gate.complete(Unit)

        assertEquals(AppResult.Failure(AppError.CustomServicesDisabled), pending.await())
        assertNull(repository.cachedReviews(100001))
        assertEquals(0, api.calls)
    }

    @Test
    fun `request cancellation remains cancellation`() = runTest {
        val api = FakeApi().apply { failure = CancellationException("Synthetic cancellation") }
        val repository = TeacherReviewsRepositoryImpl(FakeServices(true), api.instance, backgroundScope, FixedAcademicTime(), noDemo())

        try {
            repository.reviews(100001)
            fail("Cancellation was swallowed")
        } catch (actual: CancellationException) {
            assertEquals("Synthetic cancellation", actual.message)
        }
    }

    @Test
    fun `maps own community reviews copies own review and viewer capabilities`() = runTest {
        val author = UserData(100002, " Автор Отзыва ", null, listOf(GroupData("M3234", 2, "ФИТиП")),
            UserCapabilities(canViewSchedule = true, canViewSport = false))
        val api = FakeApi().apply {
            result = result!!.copy(
                reviews = listOf(
                    community(1).copy(verified = true, author = author, writtenOn = LocalDate.of(2026, 9, 12), score = 3, myVote = 1),
                    community(2).copy(reportedByMe = true, myVote = -1),
                    review(3).copy(sourceTitle = " Отзывы ПИ ", sourceLink = "https://example.test/review/3"),
                    review(4).copy(sourceLink = "http://example.test/review/4", writtenBeforeYear = 2023),
                ),
                mine = WireOwnReview(UUID(0, 9), " Предмет ", " Мой отзыв ", false, TeacherReviewStatus.REJECTED,
                    " Грубость ", 2, true, LocalDate.of(2026, 9, 20)),
                canWrite = true, canVote = true, canReport = false, knownTeacher = true,
            )
        }
        val repository = TeacherReviewsRepositoryImpl(FakeServices(true), api.instance, backgroundScope, FixedAcademicTime(), noDemo())

        val reviews = (repository.reviews(100001) as AppResult.Success).value

        assertEquals(listOf(
            TeacherReview(UUID(0, 1).toString(), null, ReviewDate.Month(YearMonth.of(2026, 9)), "Отзыв 1", 3, 1,
                ReviewOrigin.Community(verified = true, author = author.toUserSummary(), reportedByMe = false)),
            TeacherReview(UUID(0, 2).toString(), null, null, "Отзыв 2", 0, -1,
                ReviewOrigin.Community(verified = false, author = null, reportedByMe = true)),
            TeacherReview(UUID(0, 3).toString(), null, null, "Отзыв 3", 0, 0,
                ReviewOrigin.Reviews("Отзывы ПИ", "https://example.test/review/3")),
            TeacherReview(UUID(0, 4).toString(), null, ReviewDate.BeforeYear(2023), "Отзыв 4", 0, 0,
                ReviewOrigin.Reviews(null, PROVIDER_URL)),
        ), reviews.reviews)
        assertEquals("Автор Отзыва", (reviews.reviews[0].origin as ReviewOrigin.Community).author?.name)
        val mine = checkNotNull(reviews.mine)
        assertEquals(UUID(0, 9).toString(), mine.id)
        assertEquals("Предмет", mine.subject)
        assertEquals("Мой отзыв", mine.text)
        assertFalse(mine.anonymous)
        assertEquals(OwnReviewStatus.REJECTED, mine.status)
        assertEquals("Грубость", mine.reviewNote)
        assertEquals(2, mine.score)
        assertTrue(mine.verified)
        assertEquals(ReviewDate.Month(YearMonth.of(2026, 9)), mine.written)
        assertEquals(listOf(true, true, false, true), listOf(reviews.canWrite, reviews.canVote, reviews.canReport, reviews.knownTeacher))
    }

    @Test
    fun `save sends cleaned content and sorted flows and publishes the answer`() = runTest {
        val api = FakeApi()
        val repository = TeacherReviewsRepositoryImpl(FakeServices(true), api.instance, backgroundScope, FixedAcademicTime(), noDemo())
        val updates = collectUpdates(repository)
        api.result = api.result!!.copy(mine = ownWire())

        val result = repository.save(100001, TeacherReviewDraft(" Матанализ ", "  $VALID_TEXT\r\nВторая строка ", false, setOf(30, 10, 20)))

        val saved = (result as AppResult.Success).value
        assertEquals(listOf(Request("saveMyTeacherReview", listOf(100001,
            SaveTeacherReviewRequest("Матанализ", "$VALID_TEXT\nВторая строка", false, listOf(10L, 20L, 30L))))), api.requests)
        assertEquals(saved, repository.cachedReviews(100001))
        runCurrent()
        assertEquals(listOf(saved), updates)
    }

    @Test
    fun `save sends a blank subject as null and at most fifty flows`() = runTest {
        val api = FakeApi()
        val repository = TeacherReviewsRepositoryImpl(FakeServices(true), api.instance, backgroundScope, FixedAcademicTime(), noDemo())

        repository.save(100001, TeacherReviewDraft("   ", VALID_TEXT, true, (60L downTo 1L).toSet()))

        val request = api.requests.single().arguments[1] as SaveTeacherReviewRequest
        assertNull(request.subjectTitle)
        assertTrue(request.anonymous)
        assertEquals((1L..50L).toList(), request.flowIds)
    }

    @Test
    fun `delete vote and report call their routes with the review UUID`() = runTest {
        val api = FakeApi()
        val repository = TeacherReviewsRepositoryImpl(FakeServices(true), api.instance, backgroundScope, FixedAcademicTime(), noDemo())
        val updates = collectUpdates(repository)
        val id = UUID(0, 7)

        assertTrue(repository.delete(100001) is AppResult.Success)
        assertTrue(repository.vote(100001, id.toString(), 1) is AppResult.Success)
        assertTrue(repository.report(100001, id.toString(), ReviewReportReason.OFFENSIVE, "  Грубо  ") is AppResult.Success)
        assertTrue(repository.report(100001, id.toString(), ReviewReportReason.WRONG_TEACHER, "   ") is AppResult.Success)

        assertEquals(listOf(
            Request("deleteMyTeacherReview", listOf(100001)),
            Request("voteTeacherReview", listOf(id, ResourceVoteRequest(1))),
            Request("reportTeacherReview", listOf(id, ModerationReportRequest(ReportReason.OFFENSIVE, "Грубо"))),
            Request("reportTeacherReview", listOf(id, ModerationReportRequest(ReportReason.WRONG_TEACHER, null))),
        ), api.requests)
        runCurrent()
        assertEquals(4, updates.size)
    }

    @Test
    fun `mutations without the opt-in never call Backend`() = runTest {
        val api = FakeApi()
        val repository = TeacherReviewsRepositoryImpl(FakeServices(false), api.instance, backgroundScope, FixedAcademicTime(), noDemo())
        val id = UUID(0, 7).toString()

        val results = listOf(
            repository.save(100001, TeacherReviewDraft(null, VALID_TEXT, true, emptySet())),
            repository.delete(100001),
            repository.vote(100001, id, 1),
            repository.report(100001, id, ReviewReportReason.SPAM, null),
        )

        assertEquals(List(4) { AppResult.Failure(AppError.CustomServicesDisabled) }, results)
        assertEquals(0, api.calls)
    }

    @Test
    fun `invalid input fails before the network`() = runTest {
        val api = FakeApi()
        val repository = TeacherReviewsRepositoryImpl(FakeServices(true), api.instance, backgroundScope, FixedAcademicTime(), noDemo())
        val id = UUID(0, 7).toString()

        val results = listOf(
            repository.save(100001, TeacherReviewDraft(null, "a".repeat(29), true, emptySet())),
            repository.save(100001, TeacherReviewDraft(null, " ".repeat(10) + "a".repeat(29) + " ".repeat(10), true, emptySet())),
            repository.save(100001, TeacherReviewDraft(null, "a".repeat(3001), true, emptySet())),
            repository.save(100001, TeacherReviewDraft("п".repeat(201), VALID_TEXT, true, emptySet())),
            repository.vote(100001, id, 2),
            repository.vote(100001, "x", 1),
            repository.report(100001, "x", ReviewReportReason.OTHER, null),
            repository.report(100001, id, ReviewReportReason.OTHER, "к".repeat(501)),
        )

        results.forEach { assertTrue(it.toString(), (it as AppResult.Failure).error is AppError.Unknown) }
        assertEquals(0, api.calls)
    }

    @Test
    fun `limits count code points like Backend`() = runTest {
        val api = FakeApi()
        val repository = TeacherReviewsRepositoryImpl(FakeServices(true), api.instance, backgroundScope, FixedAcademicTime(), noDemo())
        val emoji = "\uD83D\uDE00"

        assertTrue(repository.save(100001, TeacherReviewDraft(emoji.repeat(200), emoji.repeat(3000), true, emptySet())) is AppResult.Success)
        assertTrue(repository.save(100001, TeacherReviewDraft(null, emoji.repeat(3001), true, emptySet())) is AppResult.Failure)

        assertEquals(1, api.calls)
    }

    @Test
    fun `restricted and network failures keep the cache and publish nothing`() = runTest {
        val api = FakeApi().apply { result = result!!.copy(reviews = listOf(review(1))) }
        val repository = TeacherReviewsRepositoryImpl(FakeServices(true), api.instance, backgroundScope, FixedAcademicTime(), noDemo())
        val cached = (repository.reviews(100001) as AppResult.Success).value
        val updates = collectUpdates(repository)
        api.result = api.result!!.copy(reviews = emptyList())

        api.failure = HttpException(Response.error<Any>(403, """{"error":{"code":"restricted"}}""".toResponseBody()))
        assertEquals(AppResult.Failure(AppError.Restricted), repository.vote(100001, UUID(0, 1).toString(), 1))
        api.failure = IOException("Synthetic offline response")
        assertEquals(AppResult.Failure(AppError.Network), repository.save(100001, TeacherReviewDraft(null, VALID_TEXT, true, emptySet())))

        runCurrent()
        assertEquals(cached, repository.cachedReviews(100001))
        assertEquals(emptyList<TeacherReviews>(), updates)
    }

    @Test
    fun `a mutation answer after opting out reaches neither cache nor updates`() = runTest {
        val services = FakeServices(true)
        val api = FakeApi()
        val repository = TeacherReviewsRepositoryImpl(services, api.instance, backgroundScope, FixedAcademicTime(), noDemo())
        val updates = collectUpdates(repository)
        val gate = ResponseGate()
        api.beforeResponse = gate::await
        val pending = async { repository.delete(100001) }
        gate.entered.await()

        services.enabled.value = false
        runCurrent()
        services.enabled.value = true
        runCurrent()
        gate.open()

        assertEquals(AppResult.Failure(AppError.CustomServicesDisabled), pending.await())
        runCurrent()
        assertNull(repository.cachedReviews(100001))
        assertEquals(emptyList<TeacherReviews>(), updates)
    }

    @Test
    fun `a mutation answer after session cleanup reaches neither cache nor updates`() = runTest {
        val api = FakeApi()
        val repository = TeacherReviewsRepositoryImpl(FakeServices(true), api.instance, backgroundScope, FixedAcademicTime(), noDemo())
        val updates = collectUpdates(repository)
        val gate = ResponseGate()
        api.beforeResponse = gate::await
        val pending = async { repository.vote(100001, UUID(0, 1).toString(), -1) }
        gate.entered.await()

        repository.clearSessionData()
        gate.open()

        assertEquals(AppResult.Failure(AppError.CustomServicesDisabled), pending.await())
        runCurrent()
        assertNull(repository.cachedReviews(100001))
        assertEquals(emptyList<TeacherReviews>(), updates)
    }

    private fun TestScope.collectUpdates(repository: TeacherReviewsRepositoryImpl): List<TeacherReviews> {
        val updates = CopyOnWriteArrayList<TeacherReviews>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { repository.observeUpdates().toList(updates) }
        return updates
    }

    private fun community(number: Int) = review(number).copy(kind = TeacherReviewKind.COMMUNITY)

    private fun ownWire() = WireOwnReview(UUID(0, 9), null, VALID_TEXT, true, TeacherReviewStatus.PENDING, null, 0, false,
        LocalDate.of(2026, 9, 29))

    private fun review(number: Int) = WireReview(UUID(0, number.toLong()), TeacherReviewKind.REVIEWS, null, null, null,
        "Отзыв $number", 0, 0, false, false, null, null, null)

    private fun TeacherReview.source() = origin as ReviewOrigin.Reviews

    private fun wireSummary() = WireSummary(
        reviewCount = 12,
        description = " Понятно объясняет, но строго принимает лабораторные. ",
        pros = listOf(" Понятные лекции ", " "),
        cons = listOf("Строгая защита"),
        tags = listOf("MANY_LABS", "NEW_TAG", " STRICT_DEFENSE ", "MANY_LABS"),
        scales = listOf(
            WireScale(WireScaleKind.EXPLAINS, WireScaleValue.HIGH, "Хвалят лекции"),
            WireScale(WireScaleKind.ATTITUDE, WireScaleValue.MEDIUM, " Ровное отношение "),
            WireScale(WireScaleKind.FAIRNESS, WireScaleValue.HIGH, "Оценки честные"),
            WireScale(WireScaleKind.STRICTNESS, WireScaleValue.HIGH, "Строгая защита"),
            WireScale(WireScaleKind.WORKLOAD, WireScaleValue.NOT_ENOUGH_DATA, null),
        ),
        level = WireLevel.POSITIVE,
        confidence = WireConfidence.MEDIUM,
        generatedAt = Instant.parse("2026-09-29T03:00:00Z"),
    )

    private class FakeServices(enabled: Boolean) : CustomServicesRepository {
        val enabled = MutableStateFlow(enabled)
        var readGate: (suspend () -> Unit)? = null

        override fun observeEnabled(): Flow<Boolean> = enabled
        override suspend fun isEnabled(): Boolean {
            val on = enabled.value
            readGate?.invoke()
            return on
        }
        override suspend fun setEnabled(enabled: Boolean) { this.enabled.value = enabled }
    }

    private class ResponseGate {
        val entered = CompletableDeferred<Unit>()
        private val release = CountDownLatch(1)

        fun await() {
            entered.complete(Unit)
            assertTrue("Synthetic response gate was not released", release.await(10, TimeUnit.SECONDS))
        }

        fun open() = release.countDown()
    }

    private class FakeApi {
        var result: WireTeacherReviews? = WireTeacherReviews(100001, PROVIDER_URL, emptyList(), null, false, false, false, false)
        var failure: Exception? = null
        var beforeResponse: () -> Unit = {}
        val requestedIsus = CopyOnWriteArrayList<Int>()
        val requests = CopyOnWriteArrayList<Request>()
        private val callCount = AtomicInteger()
        val calls: Int get() = callCount.get()

        val instance: ItmoWidgetsApi = Proxy.newProxyInstance(
            ItmoWidgetsApi::class.java.classLoader,
            arrayOf(ItmoWidgetsApi::class.java),
        ) { proxy, method, arguments ->
            when (method.name) {
                "equals" -> return@newProxyInstance proxy === arguments?.firstOrNull()
                "hashCode" -> return@newProxyInstance System.identityHashCode(proxy)
                "toString" -> return@newProxyInstance "FakeReviewsApi"
            }
            check(method.name in ROUTES) { "Unexpected ItmoWidgetsApi call: ${method.name}" }
            callCount.incrementAndGet()
            if (method.name == "teacherReviews") requestedIsus += arguments[0] as Int
            else requests += Request(method.name, arguments.dropLast(1))
            failure?.let { throw it }
            val response = ApiResponse.success(result)
            beforeResponse()
            response
        } as ItmoWidgetsApi
    }

    /** A mutation route with its arguments, without the continuation. */
    private data class Request(val method: String, val arguments: List<Any?>)

    private companion object {
        const val PROVIDER_URL = "https://onetwozzzplus.github.io/reviews/#/teacher/100001"
        const val VALID_TEXT = "Понятно объясняет материал и отвечает на вопросы."
        val ROUTES = setOf("teacherReviews", "saveMyTeacherReview", "deleteMyTeacherReview", "voteTeacherReview", "reportTeacherReview")
    }
}
