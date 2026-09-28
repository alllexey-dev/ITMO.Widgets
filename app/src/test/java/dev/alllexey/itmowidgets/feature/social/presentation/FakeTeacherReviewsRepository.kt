package dev.alllexey.itmowidgets.feature.social.presentation

import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.reviews.TeacherReviews
import dev.alllexey.itmowidgets.core.reviews.TeacherReviewsRepository

internal class FakeTeacherReviewsRepository : TeacherReviewsRepository {
    var results: Map<Int, AppResult<TeacherReviews>> = emptyMap()
    var cached: Map<Int, TeacherReviews> = emptyMap()
    var gate: suspend () -> Unit = {}
    var calls = 0
        private set

    override fun cachedReviews(isu: Int): TeacherReviews? = cached[isu]

    override suspend fun reviews(isu: Int): AppResult<TeacherReviews> {
        calls += 1
        gate()
        return results[isu] ?: AppResult.Failure(AppError.CustomServicesDisabled)
    }
}
