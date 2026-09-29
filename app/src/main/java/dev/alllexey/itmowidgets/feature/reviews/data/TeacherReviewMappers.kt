package dev.alllexey.itmowidgets.feature.reviews.data

import dev.alllexey.itmowidgets.core.model.resources.ReportReason
import dev.alllexey.itmowidgets.core.model.reviews.TeacherReviewKind
import dev.alllexey.itmowidgets.core.model.toUserSummary
import dev.alllexey.itmowidgets.core.reviews.OwnReviewStatus
import dev.alllexey.itmowidgets.core.reviews.OwnTeacherReview
import dev.alllexey.itmowidgets.core.reviews.ReviewDate
import dev.alllexey.itmowidgets.core.reviews.ReviewOrigin
import dev.alllexey.itmowidgets.core.reviews.ReviewReportReason
import dev.alllexey.itmowidgets.core.reviews.TeacherReview
import dev.alllexey.itmowidgets.core.reviews.TeacherReviews
import dev.alllexey.itmowidgets.core.util.HttpsNavigationPolicy
import java.time.YearMonth
import dev.alllexey.itmowidgets.core.model.reviews.OwnTeacherReview as WireOwnReview
import dev.alllexey.itmowidgets.core.model.reviews.TeacherReview as WireReview
import dev.alllexey.itmowidgets.core.model.reviews.TeacherReviewsResponse as WireTeacherReviews

internal fun WireTeacherReviews.toModel() = TeacherReviews(
    isu = teacherIsu,
    reviews = reviews.mapNotNull { it.toModel(providerUrl.trim()) },
    mine = mine?.toModel(),
    canWrite = canWrite,
    canVote = canVote,
    canReport = canReport,
    knownTeacher = knownTeacher,
)

internal fun ReviewReportReason.toWire() = ReportReason.valueOf(name)

private fun WireReview.toModel(providerUrl: String): TeacherReview? {
    val text = text.clean() ?: return null
    val origin = when (kind) {
        TeacherReviewKind.COMMUNITY -> ReviewOrigin.Community(verified, author?.toUserSummary(), reportedByMe)
        TeacherReviewKind.REVIEWS -> ReviewOrigin.Reviews(
            sourceTitle = sourceTitle.clean(),
            sourceUrl = sourceLink.clean()?.takeIf(HttpsNavigationPolicy::isNavigable) ?: providerUrl,
        )
    }
    return TeacherReview(
        id = id.toString(),
        subject = subjectTitle.clean(),
        written = writtenOn?.let { ReviewDate.Month(YearMonth.from(it)) }
            ?: writtenBeforeYear?.let(ReviewDate::BeforeYear),
        text = text,
        score = score,
        myVote = myVote,
        origin = origin,
    )
}

private fun WireOwnReview.toModel() = OwnTeacherReview(
    id = id.toString(),
    subject = subjectTitle.clean(),
    text = text.trim(),
    anonymous = anonymous,
    status = OwnReviewStatus.valueOf(status.name),
    reviewNote = reviewNote.clean(),
    score = score,
    verified = verified,
    written = ReviewDate.Month(YearMonth.from(writtenOn)),
)

private fun String?.clean(): String? = this?.trim()?.takeIf(String::isNotEmpty)
