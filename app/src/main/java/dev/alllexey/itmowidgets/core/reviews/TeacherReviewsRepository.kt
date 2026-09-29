package dev.alllexey.itmowidgets.core.reviews

import dev.alllexey.itmowidgets.core.result.AppResult
import kotlinx.coroutines.flow.Flow

/**
 * Backend reviews gated by the ITMO.Widgets opt-in inside the repository.
 * Cached reviews are unavailable while the opt-in is disabled or its state is still unknown.
 * Mutations check the opt-in themselves and validate their input before any network call;
 * invalid input fails with an unknown error.
 */
interface TeacherReviewsRepository {
    fun cachedReviews(isu: Int): TeacherReviews?
    suspend fun reviews(isu: Int): AppResult<TeacherReviews>

    /** Fresh reviews of a teacher after every successful mutation. */
    fun observeUpdates(): Flow<TeacherReviews>

    /** Creates or edits the viewer's review; new content waits for moderation. */
    suspend fun save(isu: Int, draft: TeacherReviewDraft): AppResult<TeacherReviews>
    suspend fun delete(isu: Int): AppResult<TeacherReviews>

    /** [value] is -1, 0 (removes the vote) or 1. */
    suspend fun vote(isu: Int, reviewId: String, value: Int): AppResult<TeacherReviews>
    suspend fun report(isu: Int, reviewId: String, reason: ReviewReportReason, comment: String?): AppResult<TeacherReviews>
}
