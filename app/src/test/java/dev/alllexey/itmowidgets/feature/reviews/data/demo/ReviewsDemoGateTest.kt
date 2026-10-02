package dev.alllexey.itmowidgets.feature.reviews.data.demo

import dev.alllexey.itmowidgets.core.ItmoWidgetsApi
import dev.alllexey.itmowidgets.core.demo.DemoPeople
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.reviews.ReviewReportReason
import dev.alllexey.itmowidgets.core.reviews.TeacherReviewDraft
import dev.alllexey.itmowidgets.core.services.CustomServicesRepository
import dev.alllexey.itmowidgets.core.testing.FakeDemoMode
import dev.alllexey.itmowidgets.core.testing.FixedAcademicTime
import dev.alllexey.itmowidgets.core.testing.unreachable
import dev.alllexey.itmowidgets.feature.reviews.data.TeacherReviewsRepositoryImpl
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReviewsDemoGateTest {

    @Test
    fun `reviews and summaries come from the demo set and every change is refused`() = runTest {
        val repository = TeacherReviewsRepositoryImpl(Connected, unreachable<ItmoWidgetsApi>(), backgroundScope, FixedAcademicTime(), FakeDemoMode(active = true))
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

    private object Connected : CustomServicesRepository {
        override fun observeEnabled(): Flow<Boolean> = flowOf(true)
        override suspend fun isEnabled() = true
        override suspend fun setEnabled(enabled: Boolean) = Unit
    }
}
