package dev.alllexey.itmowidgets.feature.reviews.data

import dev.alllexey.itmowidgets.client.common.GroupData
import dev.alllexey.itmowidgets.client.common.ModerationReportRequest
import dev.alllexey.itmowidgets.client.common.ReportReason
import dev.alllexey.itmowidgets.client.common.ResourceVoteRequest
import dev.alllexey.itmowidgets.client.common.UserCapabilities
import dev.alllexey.itmowidgets.client.common.UserData
import dev.alllexey.itmowidgets.client.error.BackendException
import dev.alllexey.itmowidgets.client.reviews.SaveTeacherReviewRequest
import dev.alllexey.itmowidgets.client.reviews.TeacherReviewKind
import dev.alllexey.itmowidgets.client.reviews.TeacherReviewStatus
import dev.alllexey.itmowidgets.client.reviews.TeacherReviewsApi
import dev.alllexey.itmowidgets.client.reviews.TeacherSummaryLevel
import dev.alllexey.itmowidgets.core.model.toUserSummary
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.reviews.OwnReviewStatus
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
import dev.alllexey.itmowidgets.core.testing.FixedAcademicTime
import dev.alllexey.itmowidgets.core.testing.noDemo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import okio.IOException
import kotlin.time.Instant
import kotlin.uuid.Uuid
import dev.alllexey.itmowidgets.client.reviews.OwnTeacherReview as WireOwnReview
import dev.alllexey.itmowidgets.client.reviews.SummaryConfidence as WireConfidence
import dev.alllexey.itmowidgets.client.reviews.SummaryLevel as WireLevel
import dev.alllexey.itmowidgets.client.reviews.SummaryScaleKind as WireScaleKind
import dev.alllexey.itmowidgets.client.reviews.SummaryScaleValue as WireScaleValue
import dev.alllexey.itmowidgets.client.reviews.TeacherReview as WireReview
import dev.alllexey.itmowidgets.client.reviews.TeacherReviewsResponse as WireTeacherReviews
import dev.alllexey.itmowidgets.client.reviews.TeacherSummary as WireSummary
import dev.alllexey.itmowidgets.client.reviews.TeacherSummaryScale as WireScale

/** The repository's state machine and mapping over a fake [TeacherReviewsApi]; the wire is in the remote test. */
@OptIn(ExperimentalCoroutinesApi::class)
class TeacherReviewsRepositoryImplTest {

    @Test
    fun disabledOrUnknownOptInExposesNoCacheAndDisabledRequestsNeverCallBackend() = runTest {
        val api = FakeTeacherReviewsApi()
        val repository = TeacherReviewsRepositoryImpl(FakeBackendGate(false), api, backgroundScope, FixedAcademicTime(), noDemo(), testAppDispatchers())

        assertNull(repository.cachedReviews(100001))
        assertEquals(AppResult.Failure(AppError.CustomServicesDisabled), repository.reviews(100001))
        runCurrent()

        assertEquals(0, api.calls)
        assertNull(repository.cachedReviews(100001))
    }

    @Test
    fun mapsExactDateBeforeYearAndAbsentDatePreservingBackendOrder() = runTest {
        val api = FakeTeacherReviewsApi().apply {
            result = result.copy(reviews = listOf(
                review(1).copy(writtenOn = LocalDate(2025, 1, 25)),
                review(2).copy(writtenBeforeYear = 2024),
                review(3),
            ))
        }
        val repository = TeacherReviewsRepositoryImpl(FakeBackendGate(true), api, backgroundScope, FixedAcademicTime(), noDemo(), testAppDispatchers())

        val reviews = (repository.reviews(100001) as AppResult.Success).value

        assertEquals(listOf(100001), api.requestedIsus)
        assertEquals(100001, reviews.isu)
        assertEquals(listOf(Uuid.fromLongs(0, 1).toString(), Uuid.fromLongs(0, 2).toString(), Uuid.fromLongs(0, 3).toString()), reviews.reviews.map { it.id })
        assertEquals(listOf(ReviewDate.Month(YearMonth(2025, 1)), ReviewDate.BeforeYear(2024), null), reviews.reviews.map { it.written })
        assertEquals(reviews, repository.cachedReviews(100001))
    }

    @Test
    fun keepsHttpsSourceLinksAndFallsBackForAbsentUnsafeAndMalformedLinks() = runTest {
        val links = listOf(" https://example.test/source ", "http://example.test/source", null, " ", "not a url", "https:///missing-host")
        val api = FakeTeacherReviewsApi().apply {
            result = result.copy(reviews = links.mapIndexed { index, link -> review(index + 1).copy(sourceLink = link) })
        }
        val repository = TeacherReviewsRepositoryImpl(FakeBackendGate(true), api, backgroundScope, FixedAcademicTime(), noDemo(), testAppDispatchers())

        val reviews = (repository.reviews(100001) as AppResult.Success).value

        assertEquals(listOf("https://example.test/source") + List(5) { PROVIDER_URL }, reviews.reviews.map { it.source().sourceUrl })
    }

    @Test
    fun trimsOptionalStringsDropsBlankReviewsAndPreservesFullTextAndOrder() = runTest {
        val api = FakeTeacherReviewsApi().apply {
            result = result.copy(reviews = listOf(
                review(1).copy(subjectTitle = " Предмет ", sourceTitle = " Источник ", text = " Первый\nвторой абзац "),
                review(2).copy(text = " \n\t "),
                review(3).copy(subjectTitle = " ", sourceTitle = " ", text = " Третий "),
            ))
        }
        val repository = TeacherReviewsRepositoryImpl(FakeBackendGate(true), api, backgroundScope, FixedAcademicTime(), noDemo(), testAppDispatchers())

        val reviews = (repository.reviews(100001) as AppResult.Success).value.reviews

        assertEquals(listOf(Uuid.fromLongs(0, 1).toString(), Uuid.fromLongs(0, 3).toString()), reviews.map { it.id })
        assertEquals(listOf("Первый\nвторой абзац", "Третий"), reviews.map { it.text })
        assertEquals("Предмет", reviews[0].subject)
        assertEquals("Источник", reviews[0].source().sourceTitle)
        assertNull(reviews[1].subject)
        assertNull(reviews[1].source().sourceTitle)
    }

    @Test
    fun hTTPAndNetworkFailuresPreserveTheSuccessfulCache() = runTest {
        val api = FakeTeacherReviewsApi().apply { result = result.copy(reviews = listOf(review(1))) }
        val repository = TeacherReviewsRepositoryImpl(FakeBackendGate(true), api, backgroundScope, FixedAcademicTime(), noDemo(), testAppDispatchers())
        val cached = (repository.reviews(100001) as AppResult.Success).value

        api.failure = BackendException.NotFound("not_found")
        assertEquals(AppResult.Failure(AppError.NotFound), repository.reviews(100001))
        assertEquals(cached, repository.cachedReviews(100001))
        api.failure = BackendException.Transport(IOException("Synthetic offline response"))
        assertEquals(AppResult.Failure(AppError.Network), repository.reviews(100001))
        assertEquals(cached, repository.cachedReviews(100001))
    }

    @Test
    fun anAnswerThatBreaksTheContractIsAFailureInsteadOfAnEmptySuccessfulList() = runTest {
        val api = FakeTeacherReviewsApi().apply { failure = BackendException.Contract(IllegalStateException("Synthetic missing data")) }
        val repository = TeacherReviewsRepositoryImpl(FakeBackendGate(true), api, backgroundScope, FixedAcademicTime(), noDemo(), testAppDispatchers())

        assertTrue((repository.reviews(100001) as AppResult.Failure).error is AppError.Unknown)
        assertNull(repository.cachedReviews(100001))
    }

    @Test
    fun mapsTheWholeSummarySkippingUnknownTagsAndBlankItems() = runTest {
        val api = FakeTeacherReviewsApi().apply { result = result.copy(summary = wireSummary()) }
        val repository = TeacherReviewsRepositoryImpl(FakeBackendGate(true), api, backgroundScope, FixedAcademicTime(), noDemo(), testAppDispatchers())

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
    fun aReplyWithoutASummaryOrWithABlankDescriptionHasNoSummary() = runTest {
        val api = FakeTeacherReviewsApi()
        val repository = TeacherReviewsRepositoryImpl(FakeBackendGate(true), api, backgroundScope, FixedAcademicTime(), noDemo(), testAppDispatchers())

        assertNull((repository.reviews(100001) as AppResult.Success).value.summary)
        api.result = api.result.copy(summary = wireSummary().copy(description = " \n "))
        assertNull((repository.reviews(100001) as AppResult.Success).value.summary)
    }

    @Test
    fun aLowConfidenceSummaryHidesItsLevel() = runTest {
        val api = FakeTeacherReviewsApi().apply { result = result.copy(summary = wireSummary().copy(confidence = WireConfidence.LOW)) }
        val repository = TeacherReviewsRepositoryImpl(FakeBackendGate(true), api, backgroundScope, FixedAcademicTime(), noDemo(), testAppDispatchers())

        val summary = (repository.reviews(100001) as AppResult.Success).value.summary!!

        assertEquals(SummaryConfidence.LOW, summary.confidence)
        assertFalse(summary.showsLevel)
    }

    @Test
    fun anUnknownToneOrConfidenceKeepsTheSummaryWithItsToneHidden() = runTest {
        val api = FakeTeacherReviewsApi().apply { result = result.copy(summary = wireSummary().copy(level = WireLevel.UNKNOWN)) }
        val repository = TeacherReviewsRepositoryImpl(FakeBackendGate(true), api, backgroundScope, FixedAcademicTime(), noDemo(), testAppDispatchers())

        val unknownTone = (repository.reviews(100001) as AppResult.Success).value.summary!!
        api.result = api.result.copy(summary = wireSummary().copy(confidence = WireConfidence.UNKNOWN))
        val unknownConfidence = (repository.reviews(100001) as AppResult.Success).value.summary!!

        assertEquals("Понятно объясняет, но строго принимает лабораторные.", unknownTone.description)
        assertFalse(unknownTone.showsLevel)
        assertEquals(TeacherLevel.POSITIVE, unknownConfidence.level)
        assertFalse(unknownConfidence.showsLevel)
    }

    @Test
    fun aScaleOfAnUnknownKindOrValueIsLeftOut() = runTest {
        val scales = listOf(
            WireScale(WireScaleKind.EXPLAINS, WireScaleValue.UNKNOWN, "Новое значение"),
            WireScale(WireScaleKind.ATTITUDE, WireScaleValue.MEDIUM, "Ровное отношение"),
            WireScale(WireScaleKind.FAIRNESS, WireScaleValue.HIGH, "Оценки честные"),
            WireScale(WireScaleKind.STRICTNESS, WireScaleValue.HIGH, "Строгая защита"),
            WireScale(WireScaleKind.UNKNOWN, WireScaleValue.LOW, "Новая шкала"),
        )
        val api = FakeTeacherReviewsApi().apply { result = result.copy(summary = wireSummary().copy(scales = scales)) }
        val repository = TeacherReviewsRepositoryImpl(FakeBackendGate(true), api, backgroundScope, FixedAcademicTime(), noDemo(), testAppDispatchers())

        val summary = (repository.reviews(100001) as AppResult.Success).value.summary!!

        assertEquals(listOf(SummaryScaleKind.ATTITUDE, SummaryScaleKind.FAIRNESS, SummaryScaleKind.STRICTNESS), summary.scales.map { it.kind })
    }

    @Test
    fun aReviewOfAnUnknownKindIsHiddenAndAnUnknownOwnStatusReadsAsPending() = runTest {
        val api = FakeTeacherReviewsApi().apply {
            result = result.copy(
                reviews = listOf(review(1).copy(kind = TeacherReviewKind.UNKNOWN), review(2)),
                mine = ownWire().copy(status = TeacherReviewStatus.UNKNOWN),
            )
        }
        val repository = TeacherReviewsRepositoryImpl(FakeBackendGate(true), api, backgroundScope, FixedAcademicTime(), noDemo(), testAppDispatchers())

        val reviews = (repository.reviews(100001) as AppResult.Success).value

        assertEquals(listOf(Uuid.fromLongs(0, 2).toString()), reviews.reviews.map { it.id })
        assertEquals(OwnReviewStatus.PENDING, reviews.mine?.status)
    }

    @Test
    fun optingOutClearsWarmCacheAndReconnectDoesNotResurrectIt() = runTest {
        val services = FakeBackendGate(true)
        val api = FakeTeacherReviewsApi()
        val repository = TeacherReviewsRepositoryImpl(services, api, backgroundScope, FixedAcademicTime(), noDemo(), testAppDispatchers())
        repository.reviews(100001)
        assertNotNull(repository.cachedReviews(100001))

        services.optedIn.value = false
        runCurrent()
        assertNull(repository.cachedReviews(100001))
        services.optedIn.value = true
        runCurrent()

        assertNull(repository.cachedReviews(100001))
        assertEquals(1, api.calls)
    }

    @Test
    fun directDisabledGateClearsCachesBeforeTheObserverRuns() = runTest {
        val services = FakeBackendGate(true)
        val api = FakeTeacherReviewsApi()
        val repository = TeacherReviewsRepositoryImpl(services, api, backgroundScope, FixedAcademicTime(), noDemo(), testAppDispatchers())
        repository.reviews(100001)

        services.optedIn.value = false
        assertEquals(AppResult.Failure(AppError.CustomServicesDisabled), repository.reviews(100001))
        services.optedIn.value = true
        runCurrent()

        assertNull(repository.cachedReviews(100001))
        assertEquals(1, api.calls)
    }

    @Test
    fun clearingSessionDataForgetsCachedReviews() = runTest {
        val api = FakeTeacherReviewsApi()
        val repository = TeacherReviewsRepositoryImpl(FakeBackendGate(true), api, backgroundScope, FixedAcademicTime(), noDemo(), testAppDispatchers())
        repository.reviews(100001)
        assertNotNull(repository.cachedReviews(100001))

        repository.clearSessionData()

        assertNull(repository.cachedReviews(100001))
    }

    @Test
    fun pendingResponsesCannotReportSuccessOrRestoreCacheAfterOptingOut() = runTest {
        val services = FakeBackendGate(true)
        val api = FakeTeacherReviewsApi()
        val repository = TeacherReviewsRepositoryImpl(services, api, backgroundScope, FixedAcademicTime(), noDemo(), testAppDispatchers())
        repository.reviews(100001)
        val gate = ResponseGate()
        api.beforeResponse = gate::await
        val pending = async { repository.reviews(100001) }
        gate.entered.await()

        services.optedIn.value = false
        runCurrent()
        gate.open()

        assertEquals(AppResult.Failure(AppError.CustomServicesDisabled), pending.await())
        assertNull(repository.cachedReviews(100001))
    }

    @Test
    fun preDisconnectResponsesCannotReplaceFreshReviewsAfterReconnect() = runTest {
        val services = FakeBackendGate(true)
        val api = FakeTeacherReviewsApi().apply { result = result.copy(reviews = listOf(review(1))) }
        val repository = TeacherReviewsRepositoryImpl(services, api, backgroundScope, FixedAcademicTime(), noDemo(), testAppDispatchers())
        val gate = ResponseGate()
        api.beforeResponse = gate::await
        val pending = async { repository.reviews(100001) }
        gate.entered.await()
        services.optedIn.value = false
        runCurrent()
        services.optedIn.value = true
        runCurrent()
        assertNull(repository.cachedReviews(100001))

        api.beforeResponse = {}
        api.result = api.result.copy(reviews = listOf(review(2)))
        val fresh = (repository.reviews(100001) as AppResult.Success).value
        gate.open()

        assertEquals(AppResult.Failure(AppError.CustomServicesDisabled), pending.await())
        assertEquals(fresh, repository.cachedReviews(100001))
        assertEquals(Uuid.fromLongs(0, 2).toString(), fresh.reviews.single().id)
    }

    @Test
    fun preClearResponsesCannotReplaceFreshSessionCache() = runTest {
        val api = FakeTeacherReviewsApi().apply { result = result.copy(reviews = listOf(review(1))) }
        val repository = TeacherReviewsRepositoryImpl(FakeBackendGate(true), api, backgroundScope, FixedAcademicTime(), noDemo(), testAppDispatchers())
        val gate = ResponseGate()
        api.beforeResponse = gate::await
        val pending = async { repository.reviews(100001) }
        gate.entered.await()

        repository.clearSessionData()
        api.beforeResponse = {}
        api.result = api.result.copy(reviews = listOf(review(2)))
        val fresh = (repository.reviews(100001) as AppResult.Success).value
        gate.open()

        assertEquals(AppResult.Failure(AppError.CustomServicesDisabled), pending.await())
        assertEquals(fresh, repository.cachedReviews(100001))
    }

    @Test
    fun initialEnabledObservationAcceptsAMatchingSuspendedGateRead() = runTest {
        val services = FakeBackendGate(true)
        val gate = CompletableDeferred<Unit>()
        services.beforeAnswer = { gate.await() }
        val api = FakeTeacherReviewsApi()
        val repository = TeacherReviewsRepositoryImpl(services, api, backgroundScope, FixedAcademicTime(), noDemo(), testAppDispatchers())
        val pending = async(start = CoroutineStart.UNDISPATCHED) { repository.reviews(100001) }
        runCurrent()
        assertEquals(0, api.calls)

        gate.complete(Unit)

        assertTrue(pending.await() is AppResult.Success)
        assertNotNull(repository.cachedReviews(100001))
    }

    @Test
    fun aSuspendedEnabledReadCannotReachBackendAfterDisconnectAndReconnect() = runTest {
        val services = FakeBackendGate(true)
        val api = FakeTeacherReviewsApi()
        val repository = TeacherReviewsRepositoryImpl(services, api, backgroundScope, FixedAcademicTime(), noDemo(), testAppDispatchers())
        runCurrent()
        val gate = CompletableDeferred<Unit>()
        services.beforeAnswer = { gate.await() }
        val pending = async(start = CoroutineStart.UNDISPATCHED) { repository.reviews(100001) }

        services.optedIn.value = false
        runCurrent()
        services.optedIn.value = true
        runCurrent()
        gate.complete(Unit)

        assertEquals(AppResult.Failure(AppError.CustomServicesDisabled), pending.await())
        assertNull(repository.cachedReviews(100001))
        assertEquals(0, api.calls)
    }

    @Test
    fun aSuspendedDisabledReadCannotClearNewerConnectedReviews() = runTest {
        val services = FakeBackendGate(false)
        val api = FakeTeacherReviewsApi()
        val repository = TeacherReviewsRepositoryImpl(services, api, backgroundScope, FixedAcademicTime(), noDemo(), testAppDispatchers())
        runCurrent()
        val gate = CompletableDeferred<Unit>()
        services.beforeAnswer = { gate.await() }
        val pending = async(start = CoroutineStart.UNDISPATCHED) { repository.reviews(100001) }
        services.optedIn.value = true
        runCurrent()
        services.beforeAnswer = null
        val fresh = (repository.reviews(100001) as AppResult.Success).value

        gate.complete(Unit)

        assertEquals(AppResult.Failure(AppError.CustomServicesDisabled), pending.await())
        assertEquals(fresh, repository.cachedReviews(100001))
        assertEquals(1, api.calls)
    }

    @Test
    fun sessionCleanupInvalidatesASuspendedOptInReadBeforeTheAPICall() = runTest {
        val services = FakeBackendGate(true)
        val api = FakeTeacherReviewsApi()
        val repository = TeacherReviewsRepositoryImpl(services, api, backgroundScope, FixedAcademicTime(), noDemo(), testAppDispatchers())
        runCurrent()
        val gate = CompletableDeferred<Unit>()
        services.beforeAnswer = { gate.await() }
        val pending = async(start = CoroutineStart.UNDISPATCHED) { repository.reviews(100001) }

        repository.clearSessionData()
        gate.complete(Unit)

        assertEquals(AppResult.Failure(AppError.CustomServicesDisabled), pending.await())
        assertNull(repository.cachedReviews(100001))
        assertEquals(0, api.calls)
    }

    @Test
    fun requestCancellationRemainsCancellation() = runTest {
        val api = FakeTeacherReviewsApi().apply { failure = CancellationException("Synthetic cancellation") }
        val repository = TeacherReviewsRepositoryImpl(FakeBackendGate(true), api, backgroundScope, FixedAcademicTime(), noDemo(), testAppDispatchers())

        try {
            repository.reviews(100001)
            fail("Cancellation was swallowed")
        } catch (actual: CancellationException) {
            assertEquals("Synthetic cancellation", actual.message)
        }
    }

    @Test
    fun mapsOwnCommunityReviewsCopiesOwnReviewAndViewerCapabilities() = runTest {
        val author = UserData(100002, " Автор Отзыва ", null, listOf(GroupData("M3234", 2, "ФИТиП")),
            UserCapabilities(canViewSchedule = true, canViewSport = false, canViewFriends = false))
        val api = FakeTeacherReviewsApi().apply {
            result = result.copy(
                reviews = listOf(
                    community(1).copy(verified = true, author = author, writtenOn = LocalDate(2026, 9, 12), score = 3, myVote = 1),
                    community(2).copy(reportedByMe = true, myVote = -1),
                    review(3).copy(sourceTitle = " Отзывы ПИ ", sourceLink = "https://example.test/review/3"),
                    review(4).copy(sourceLink = "http://example.test/review/4", writtenBeforeYear = 2023),
                ),
                mine = WireOwnReview(Uuid.fromLongs(0, 9), " Предмет ", " Мой отзыв ", false, TeacherReviewStatus.REJECTED,
                    " Грубость ", 2, true, LocalDate(2026, 9, 20)),
                canWrite = true, canVote = true, canReport = false, knownTeacher = true,
            )
        }
        val repository = TeacherReviewsRepositoryImpl(FakeBackendGate(true), api, backgroundScope, FixedAcademicTime(), noDemo(), testAppDispatchers())

        val reviews = (repository.reviews(100001) as AppResult.Success).value

        assertEquals(listOf(
            TeacherReview(Uuid.fromLongs(0, 1).toString(), null, ReviewDate.Month(YearMonth(2026, 9)), "Отзыв 1", 3, 1,
                ReviewOrigin.Community(verified = true, author = author.toUserSummary(), reportedByMe = false)),
            TeacherReview(Uuid.fromLongs(0, 2).toString(), null, null, "Отзыв 2", 0, -1,
                ReviewOrigin.Community(verified = false, author = null, reportedByMe = true)),
            TeacherReview(Uuid.fromLongs(0, 3).toString(), null, null, "Отзыв 3", 0, 0,
                ReviewOrigin.Reviews("Отзывы ПИ", "https://example.test/review/3")),
            TeacherReview(Uuid.fromLongs(0, 4).toString(), null, ReviewDate.BeforeYear(2023), "Отзыв 4", 0, 0,
                ReviewOrigin.Reviews(null, PROVIDER_URL)),
        ), reviews.reviews)
        assertEquals("Автор Отзыва", (reviews.reviews[0].origin as ReviewOrigin.Community).author?.name)
        val mine = checkNotNull(reviews.mine)
        assertEquals(Uuid.fromLongs(0, 9).toString(), mine.id)
        assertEquals("Предмет", mine.subject)
        assertEquals("Мой отзыв", mine.text)
        assertFalse(mine.anonymous)
        assertEquals(OwnReviewStatus.REJECTED, mine.status)
        assertEquals("Грубость", mine.reviewNote)
        assertEquals(2, mine.score)
        assertTrue(mine.verified)
        assertEquals(ReviewDate.Month(YearMonth(2026, 9)), mine.written)
        assertEquals(listOf(true, true, false, true), listOf(reviews.canWrite, reviews.canVote, reviews.canReport, reviews.knownTeacher))
    }

    @Test
    fun saveSendsCleanedContentAndSortedFlowsAndPublishesTheAnswer() = runTest {
        val api = FakeTeacherReviewsApi()
        val repository = TeacherReviewsRepositoryImpl(FakeBackendGate(true), api, backgroundScope, FixedAcademicTime(), noDemo(), testAppDispatchers())
        val updates = collectUpdates(repository)
        api.result = api.result.copy(mine = ownWire())

        val result = repository.save(100001, TeacherReviewDraft(" Матанализ ", "  $VALID_TEXT\r\nВторая строка ", false, setOf(30, 10, 20)))

        val saved = (result as AppResult.Success).value
        assertEquals(listOf(Request("saveMyTeacherReview", listOf(100001,
            SaveTeacherReviewRequest("Матанализ", "$VALID_TEXT\nВторая строка", false, listOf(10L, 20L, 30L))))), api.requests)
        assertEquals(saved, repository.cachedReviews(100001))
        runCurrent()
        assertEquals(listOf(saved), updates)
    }

    @Test
    fun saveSendsABlankSubjectAsNullAndAtMostFiftyFlows() = runTest {
        val api = FakeTeacherReviewsApi()
        val repository = TeacherReviewsRepositoryImpl(FakeBackendGate(true), api, backgroundScope, FixedAcademicTime(), noDemo(), testAppDispatchers())

        repository.save(100001, TeacherReviewDraft("   ", VALID_TEXT, true, (60L downTo 1L).toSet()))

        val request = api.requests.single().arguments[1] as SaveTeacherReviewRequest
        assertNull(request.subjectTitle)
        assertTrue(request.anonymous)
        assertEquals((1L..50L).toList(), request.flowIds)
    }

    @Test
    fun deleteVoteAndReportCallTheirRoutesWithTheReviewUUID() = runTest {
        val api = FakeTeacherReviewsApi()
        val repository = TeacherReviewsRepositoryImpl(FakeBackendGate(true), api, backgroundScope, FixedAcademicTime(), noDemo(), testAppDispatchers())
        val updates = collectUpdates(repository)
        val id = Uuid.fromLongs(0, 7)

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
    fun mutationsWithoutTheOptInNeverCallBackend() = runTest {
        val api = FakeTeacherReviewsApi()
        val repository = TeacherReviewsRepositoryImpl(FakeBackendGate(false), api, backgroundScope, FixedAcademicTime(), noDemo(), testAppDispatchers())
        val id = Uuid.fromLongs(0, 7).toString()

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
    fun invalidInputFailsBeforeTheNetwork() = runTest {
        val api = FakeTeacherReviewsApi()
        val repository = TeacherReviewsRepositoryImpl(FakeBackendGate(true), api, backgroundScope, FixedAcademicTime(), noDemo(), testAppDispatchers())
        val id = Uuid.fromLongs(0, 7).toString()

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

        results.forEach { assertTrue((it as AppResult.Failure).error is AppError.Unknown, it.toString()) }
        assertEquals(0, api.calls)
    }

    @Test
    fun limitsCountCodePointsLikeBackend() = runTest {
        val api = FakeTeacherReviewsApi()
        val repository = TeacherReviewsRepositoryImpl(FakeBackendGate(true), api, backgroundScope, FixedAcademicTime(), noDemo(), testAppDispatchers())
        val emoji = "\uD83D\uDE00"

        assertTrue(repository.save(100001, TeacherReviewDraft(emoji.repeat(200), emoji.repeat(3000), true, emptySet())) is AppResult.Success)
        assertTrue(repository.save(100001, TeacherReviewDraft(null, emoji.repeat(3001), true, emptySet())) is AppResult.Failure)

        assertEquals(1, api.calls)
    }

    @Test
    fun restrictedAndNetworkFailuresKeepTheCacheAndPublishNothing() = runTest {
        val api = FakeTeacherReviewsApi().apply { result = result.copy(reviews = listOf(review(1))) }
        val repository = TeacherReviewsRepositoryImpl(FakeBackendGate(true), api, backgroundScope, FixedAcademicTime(), noDemo(), testAppDispatchers())
        val cached = (repository.reviews(100001) as AppResult.Success).value
        val updates = collectUpdates(repository)
        api.result = api.result.copy(reviews = emptyList())

        api.failure = BackendException.Forbidden("restricted")
        assertEquals(AppResult.Failure(AppError.Restricted), repository.vote(100001, Uuid.fromLongs(0, 1).toString(), 1))
        api.failure = BackendException.Transport(IOException("Synthetic offline response"))
        assertEquals(AppResult.Failure(AppError.Network), repository.save(100001, TeacherReviewDraft(null, VALID_TEXT, true, emptySet())))

        runCurrent()
        assertEquals(cached, repository.cachedReviews(100001))
        assertEquals(emptyList<TeacherReviews>(), updates)
    }

    @Test
    fun aMutationAnswerAfterOptingOutReachesNeitherCacheNorUpdates() = runTest {
        val services = FakeBackendGate(true)
        val api = FakeTeacherReviewsApi()
        val repository = TeacherReviewsRepositoryImpl(services, api, backgroundScope, FixedAcademicTime(), noDemo(), testAppDispatchers())
        val updates = collectUpdates(repository)
        val gate = ResponseGate()
        api.beforeResponse = gate::await
        val pending = async { repository.delete(100001) }
        gate.entered.await()

        services.optedIn.value = false
        runCurrent()
        services.optedIn.value = true
        runCurrent()
        gate.open()

        assertEquals(AppResult.Failure(AppError.CustomServicesDisabled), pending.await())
        runCurrent()
        assertNull(repository.cachedReviews(100001))
        assertEquals(emptyList<TeacherReviews>(), updates)
    }

    @Test
    fun aMutationAnswerAfterSessionCleanupReachesNeitherCacheNorUpdates() = runTest {
        val api = FakeTeacherReviewsApi()
        val repository = TeacherReviewsRepositoryImpl(FakeBackendGate(true), api, backgroundScope, FixedAcademicTime(), noDemo(), testAppDispatchers())
        val updates = collectUpdates(repository)
        val gate = ResponseGate()
        api.beforeResponse = gate::await
        val pending = async { repository.vote(100001, Uuid.fromLongs(0, 1).toString(), -1) }
        gate.entered.await()

        repository.clearSessionData()
        gate.open()

        assertEquals(AppResult.Failure(AppError.CustomServicesDisabled), pending.await())
        runCurrent()
        assertNull(repository.cachedReviews(100001))
        assertEquals(emptyList<TeacherReviews>(), updates)
    }

    private fun TestScope.collectUpdates(repository: TeacherReviewsRepositoryImpl): List<TeacherReviews> {
        val updates = mutableListOf<TeacherReviews>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { repository.observeUpdates().toList(updates) }
        return updates
    }

    private fun community(number: Int) = review(number).copy(kind = TeacherReviewKind.COMMUNITY)

    private fun ownWire() = WireOwnReview(Uuid.fromLongs(0, 9), null, VALID_TEXT, true, TeacherReviewStatus.PENDING, null, 0, false,
        LocalDate(2026, 9, 29))

    private fun review(number: Int) = WireReview(Uuid.fromLongs(0, number.toLong()), TeacherReviewKind.REVIEWS, null, null, null,
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

    /** Holds a response until the test opens it; `runTest` fails a gate that is never opened by its timeout. */
    private class ResponseGate {
        val entered = CompletableDeferred<Unit>()
        private val release = CompletableDeferred<Unit>()

        suspend fun await() {
            entered.complete(Unit)
            release.await()
        }

        fun open() = release.complete(Unit)
    }

    private class FakeTeacherReviewsApi : TeacherReviewsApi {
        var result = WireTeacherReviews(100001, PROVIDER_URL, emptyList(), null, false, false, false, false, null)
        var failure: Exception? = null
        var beforeResponse: suspend () -> Unit = {}
        val requestedIsus = mutableListOf<Int>()
        val requests = mutableListOf<Request>()
        var calls = 0
            private set

        override suspend fun teacherReviews(isu: Int): WireTeacherReviews {
            requestedIsus += isu
            return answer()
        }

        override suspend fun saveMyTeacherReview(isu: Int, request: SaveTeacherReviewRequest) =
            record("saveMyTeacherReview", isu, request)

        override suspend fun deleteMyTeacherReview(isu: Int) = record("deleteMyTeacherReview", isu)

        override suspend fun voteTeacherReview(id: Uuid, request: ResourceVoteRequest) =
            record("voteTeacherReview", id, request)

        override suspend fun reportTeacherReview(id: Uuid, request: ModerationReportRequest) =
            record("reportTeacherReview", id, request)

        override suspend fun teacherSummaryLevels(isus: List<Int>): List<TeacherSummaryLevel> =
            throw AssertionError("Reviews never ask for levels")

        private suspend fun record(method: String, vararg arguments: Any): WireTeacherReviews {
            requests += Request(method, arguments.toList())
            return answer()
        }

        private suspend fun answer(): WireTeacherReviews {
            calls += 1
            failure?.let { throw it }
            val response = result
            beforeResponse()
            return response
        }
    }

    /** A mutation route with its arguments. */
    private data class Request(val method: String, val arguments: List<Any?>)

    private companion object {
        const val PROVIDER_URL = "https://onetwozzzplus.github.io/reviews/#/teacher/100001"
        const val VALID_TEXT = "Понятно объясняет материал и отвечает на вопросы."
    }
}
