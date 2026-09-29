package dev.alllexey.itmowidgets.core.reviews

import dev.alllexey.itmowidgets.core.model.UserSummary
import java.time.YearMonth

/**
 * Reviews of one teacher for the current viewer. [reviews] keeps Backend's ranked order and never holds the
 * viewer's own review, which comes only in [mine]. [knownTeacher] means Backend has seen the person teach.
 * [summary] is Backend's shown AI summary, null without one.
 */
data class TeacherReviews(
    val isu: Int,
    val reviews: List<TeacherReview>,
    val mine: OwnTeacherReview?,
    val canWrite: Boolean,
    val canVote: Boolean,
    val canReport: Boolean,
    val knownTeacher: Boolean,
    val summary: TeacherSummary? = null,
)

/** A published review as another viewer sees it; [myVote] is -1, 0 or 1. */
data class TeacherReview(
    val id: String,
    val subject: String?,
    val written: ReviewDate?,
    val text: String,
    val score: Int,
    val myVote: Int,
    val origin: ReviewOrigin,
)

sealed interface ReviewOrigin {
    /** An ITMO.Widgets user's review; [author] is null when it is anonymous. */
    data class Community(val verified: Boolean, val author: UserSummary?, val reportedByMe: Boolean) : ReviewOrigin

    /** A copy from the Reviews project; [sourceUrl] is always a navigable https link. */
    data class Reviews(val sourceTitle: String?, val sourceUrl: String) : ReviewOrigin
}

/** The viewer's own review with its newest content; [reviewNote] is set only for [OwnReviewStatus.REJECTED]. */
data class OwnTeacherReview(
    val id: String,
    val subject: String?,
    val text: String,
    val anonymous: Boolean,
    val status: OwnReviewStatus,
    val reviewNote: String?,
    val score: Int,
    val verified: Boolean,
    val written: ReviewDate.Month,
)

enum class OwnReviewStatus { PENDING, PUBLISHED, REJECTED, HIDDEN }

enum class ReviewReportReason { OFFENSIVE, WRONG_TEACHER, SPAM, OTHER }

/** [flowIds] are candidate ISU flows from the author's schedule history; Backend checks them itself. */
data class TeacherReviewDraft(
    val subject: String?,
    val text: String,
    val anonymous: Boolean,
    val flowIds: Set<Long>,
)

/** Backend's limits; lengths are counted in code points, as Backend does. */
object TeacherReviewLimits {
    const val MIN_TEXT = 30
    const val MAX_TEXT = 3000
    const val MAX_SUBJECT = 200
    const val MAX_COMMENT = 500
    const val MAX_FLOWS = 50

    fun length(text: String): Int = text.codePointCount(0, text.length)
}

sealed interface ReviewDate {
    data class Month(val month: YearMonth) : ReviewDate
    data class BeforeYear(val year: Int) : ReviewDate
}
