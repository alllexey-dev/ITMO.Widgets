package dev.alllexey.itmowidgets.feature.reviews.data

import dev.alllexey.itmowidgets.core.model.resources.ReportReason
import dev.alllexey.itmowidgets.core.model.reviews.TeacherReviewKind
import dev.alllexey.itmowidgets.core.model.toUserSummary
import dev.alllexey.itmowidgets.core.reviews.OwnReviewStatus
import dev.alllexey.itmowidgets.core.reviews.OwnTeacherReview
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
import dev.alllexey.itmowidgets.core.reviews.TeacherReviews
import dev.alllexey.itmowidgets.core.reviews.TeacherSummary
import dev.alllexey.itmowidgets.core.url.HttpsNavigationPolicy
import kotlinx.datetime.YearMonth
import dev.alllexey.itmowidgets.core.model.reviews.OwnTeacherReview as WireOwnReview
import dev.alllexey.itmowidgets.core.model.reviews.TeacherReview as WireReview
import dev.alllexey.itmowidgets.core.model.reviews.TeacherReviewsResponse as WireTeacherReviews
import dev.alllexey.itmowidgets.core.model.reviews.SummaryLevel as WireSummaryLevel
import dev.alllexey.itmowidgets.core.model.reviews.TeacherSummary as WireTeacherSummary

internal fun WireTeacherReviews.toModel() = TeacherReviews(
    isu = teacherIsu,
    reviews = reviews.mapNotNull { it.toModel(providerUrl.trim()) },
    mine = mine?.toModel(),
    canWrite = canWrite,
    canVote = canVote,
    canReport = canReport,
    knownTeacher = knownTeacher,
    summary = summary?.toModel(),
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
        written = writtenOn?.let { ReviewDate.Month(YearMonth(it.year, it.monthValue)) }
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
    written = ReviewDate.Month(YearMonth(writtenOn.year, writtenOn.monthValue)),
)

/** Tags this app does not know are skipped; a summary without a description is no summary. */
internal fun WireTeacherSummary.toModel(): TeacherSummary? {
    val description = description.clean() ?: return null
    return TeacherSummary(
        reviewCount = reviewCount,
        description = description,
        pros = pros.mapNotNull { it.clean() },
        cons = cons.mapNotNull { it.clean() },
        tags = tags.mapNotNull { code -> SummaryTag.entries.firstOrNull { it.name == code.trim() } }.distinct(),
        scales = scales.map { scale ->
            SummaryScale(
                kind = SummaryScaleKind.valueOf(scale.kind.name),
                value = SummaryScaleValue.valueOf(scale.value.name),
                reason = scale.reason.clean(),
            )
        },
        level = level.toModel(),
        confidence = SummaryConfidence.valueOf(confidence.name),
    )
}

internal fun WireSummaryLevel.toModel() = TeacherLevel.valueOf(name)

private fun String?.clean(): String? = this?.trim()?.takeIf(String::isNotEmpty)
