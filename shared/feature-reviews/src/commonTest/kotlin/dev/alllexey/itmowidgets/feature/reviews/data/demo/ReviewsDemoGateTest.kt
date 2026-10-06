package dev.alllexey.itmowidgets.feature.reviews.data.demo

import dev.alllexey.itmowidgets.client.common.ModerationReportRequest
import dev.alllexey.itmowidgets.client.common.ResourceVoteRequest
import dev.alllexey.itmowidgets.client.reviews.SaveTeacherReviewRequest
import dev.alllexey.itmowidgets.client.reviews.TeacherReviewsApi
import dev.alllexey.itmowidgets.client.reviews.TeacherReviewsResponse
import dev.alllexey.itmowidgets.client.reviews.TeacherSummaryLevel
import dev.alllexey.itmowidgets.core.coroutines.AppDispatchers
import dev.alllexey.itmowidgets.core.demo.DemoPeople
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.reviews.ReviewReportReason
import dev.alllexey.itmowidgets.core.reviews.TeacherReviewDraft
import dev.alllexey.itmowidgets.core.testing.FakeBackendGate
import dev.alllexey.itmowidgets.core.testing.FakeDemoMode
import dev.alllexey.itmowidgets.core.testing.FixedAcademicTime
import dev.alllexey.itmowidgets.feature.reviews.data.TeacherReviewsRepositoryImpl
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest

class ReviewsDemoGateTest {

    @Test
    fun reviewsAndSummariesComeFromTheDemoSetAndEveryChangeIsRefused() = runTest {
        val demo = FakeDemoMode(active = true)
        // Without the stored opt-in the demo still reads as connected.
        val repository = TeacherReviewsRepositoryImpl(
            FakeBackendGate(optedIn = false, demo), UnreachableReviews, backgroundScope, FixedAcademicTime(), demo,
            dispatchers = StandardTestDispatcher(testScheduler).let { AppDispatchers(io = it, default = it, main = it) },
        )
        val refused = AppResult.Failure(AppError.DemoUnavailable)

        val math = (repository.reviews(DemoPeople.MATH_TEACHER.isu) as AppResult.Success).value
        val stranger = (repository.reviews(1) as AppResult.Success).value
        val reviewId = math.reviews.first().id

        assertTrue(math.reviews.size >= 3 && math.canWrite && math.knownTeacher)
        assertNotNull(math.summary)
        assertTrue(math.reviews.map { it.score }.distinct().size > 1)
        assertFalse(stranger.knownTeacher)
        assertEquals(refused, repository.vote(DemoPeople.MATH_TEACHER.isu, reviewId, 1))
        assertEquals(refused, repository.report(DemoPeople.MATH_TEACHER.isu, reviewId, ReviewReportReason.SPAM, null))
        assertEquals(refused, repository.delete(DemoPeople.MATH_TEACHER.isu))
        val draft = TeacherReviewDraft(null, "Понятные лекции и честный экзамен, рекомендую всем.", anonymous = true, flowIds = emptySet())
        assertEquals(refused, repository.save(DemoPeople.MATH_TEACHER.isu, draft))
    }

    /** Core 2.0's reviews area that fails the test on any call: the demo session must not reach Backend. */
    private object UnreachableReviews : TeacherReviewsApi {
        override suspend fun teacherReviews(isu: Int): TeacherReviewsResponse = unreachable("teacherReviews")
        override suspend fun saveMyTeacherReview(isu: Int, request: SaveTeacherReviewRequest): TeacherReviewsResponse =
            unreachable("saveMyTeacherReview")
        override suspend fun deleteMyTeacherReview(isu: Int): TeacherReviewsResponse = unreachable("deleteMyTeacherReview")
        override suspend fun voteTeacherReview(id: Uuid, request: ResourceVoteRequest): TeacherReviewsResponse =
            unreachable("voteTeacherReview")
        override suspend fun reportTeacherReview(id: Uuid, request: ModerationReportRequest): TeacherReviewsResponse =
            unreachable("reportTeacherReview")
        override suspend fun teacherSummaryLevels(isus: List<Int>): List<TeacherSummaryLevel> =
            unreachable("teacherSummaryLevels")

        private fun unreachable(call: String): Nothing = throw AssertionError("The demo session called Backend: $call")
    }
}
