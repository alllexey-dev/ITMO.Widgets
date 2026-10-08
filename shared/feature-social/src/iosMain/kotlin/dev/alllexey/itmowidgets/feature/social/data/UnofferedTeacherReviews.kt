package dev.alllexey.itmowidgets.feature.social.data

import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.reviews.ReviewReportReason
import dev.alllexey.itmowidgets.core.reviews.TeacherReviewDraft
import dev.alllexey.itmowidgets.core.reviews.TeacherReviews
import dev.alllexey.itmowidgets.core.reviews.TeacherReviewsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/**
 * The teacher reviews while iOS does not offer them (`PlatformCapabilities.reviews`, App Review 1.2): no cache, no
 * request, every answer `CustomServicesDisabled`, which the profile takes for "no reviews section" without a load
 * error. IO-09f replaces this binding with `reviewsModule` when it ships the editor and the reports.
 */
object UnofferedTeacherReviews : TeacherReviewsRepository {

    private val unoffered = AppResult.Failure(AppError.CustomServicesDisabled)

    override fun cachedReviews(isu: Int): TeacherReviews? = null

    override suspend fun reviews(isu: Int): AppResult<TeacherReviews> = unoffered

    override fun observeUpdates(): Flow<TeacherReviews> = emptyFlow()

    override suspend fun save(isu: Int, draft: TeacherReviewDraft): AppResult<TeacherReviews> = unoffered

    override suspend fun delete(isu: Int): AppResult<TeacherReviews> = unoffered

    override suspend fun vote(isu: Int, reviewId: String, value: Int): AppResult<TeacherReviews> = unoffered

    override suspend fun report(
        isu: Int,
        reviewId: String,
        reason: ReviewReportReason,
        comment: String?,
    ): AppResult<TeacherReviews> = unoffered
}
