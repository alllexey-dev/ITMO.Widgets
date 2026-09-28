package dev.alllexey.itmowidgets.core.reviews

import dev.alllexey.itmowidgets.core.result.AppResult

/**
 * Backend reviews gated by the ITMO.Widgets opt-in inside the repository.
 * Cached reviews are unavailable while the opt-in is disabled or its state is still unknown.
 */
interface TeacherReviewsRepository {
    fun cachedReviews(isu: Int): TeacherReviews?
    suspend fun reviews(isu: Int): AppResult<TeacherReviews>
}
