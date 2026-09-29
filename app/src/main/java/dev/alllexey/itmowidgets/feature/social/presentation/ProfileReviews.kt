package dev.alllexey.itmowidgets.feature.social.presentation

import dev.alllexey.itmowidgets.core.reviews.OwnTeacherReview
import dev.alllexey.itmowidgets.core.reviews.TeacherReview
import dev.alllexey.itmowidgets.core.reviews.TeacherReviews
import dev.alllexey.itmowidgets.feature.social.domain.model.Person

/** The reviews section of a profile; [busyId] is the review whose vote or deletion is in flight. */
data class ProfileReviews(
    val items: List<TeacherReview>,
    val mine: OwnTeacherReview?,
    val canWrite: Boolean,
    val canVote: Boolean,
    val canReport: Boolean,
    val busyId: String?,
) {
    /** Every review the viewer sees here, their own one included. */
    val count: Int get() = items.size + if (mine != null) 1 else 0
}

/**
 * The section exists when there is something to read or the viewer may write. Writing needs no own review yet
 * and a person who teaches: Backend has seen them teach, or My ITMO lists any position.
 */
fun profileReviews(reviews: TeacherReviews?, person: Person?, busyId: String?): ProfileReviews? {
    if (reviews == null) return null
    val teaches = reviews.knownTeacher || person?.positions?.isNotEmpty() == true
    val canWrite = reviews.canWrite && reviews.mine == null && teaches
    if (reviews.reviews.isEmpty() && reviews.mine == null && !canWrite) return null
    return ProfileReviews(reviews.reviews, reviews.mine, canWrite, reviews.canVote, reviews.canReport, busyId)
}
