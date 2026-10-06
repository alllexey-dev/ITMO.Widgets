package dev.alllexey.itmowidgets.feature.reviews.data

import dev.alllexey.itmowidgets.client.common.ReportReason
import dev.alllexey.itmowidgets.client.reviews.TeacherReviewKind
import dev.alllexey.itmowidgets.client.reviews.TeacherReviewStatus
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
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import dev.alllexey.itmowidgets.client.reviews.OwnTeacherReview as WireOwnReview
import dev.alllexey.itmowidgets.client.reviews.SummaryConfidence as WireConfidence
import dev.alllexey.itmowidgets.client.reviews.SummaryLevel as WireSummaryLevel
import dev.alllexey.itmowidgets.client.reviews.SummaryScaleKind as WireScaleKind
import dev.alllexey.itmowidgets.client.reviews.SummaryScaleValue as WireScaleValue
import dev.alllexey.itmowidgets.client.reviews.TeacherReview as WireReview
import dev.alllexey.itmowidgets.client.reviews.TeacherReviewsResponse as WireTeacherReviews
import dev.alllexey.itmowidgets.client.reviews.TeacherSummary as WireTeacherSummary

// Core 2.0 answers into the domain: the one place that reads Core's reviews types. Core decodes a value a newer
// Backend adds as UNKNOWN; each mapping below says what such a value shows.

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

internal fun ReviewReportReason.toWire(): ReportReason = when (this) {
    ReviewReportReason.OFFENSIVE -> ReportReason.OFFENSIVE
    ReviewReportReason.WRONG_TEACHER -> ReportReason.WRONG_TEACHER
    ReviewReportReason.SPAM -> ReportReason.SPAM
    ReviewReportReason.OTHER -> ReportReason.OTHER
}

/** A review of a kind this app does not know is not shown. */
private fun WireReview.toModel(providerUrl: String): TeacherReview? {
    val text = text.clean() ?: return null
    val origin = when (kind) {
        TeacherReviewKind.COMMUNITY -> ReviewOrigin.Community(verified, author?.toUserSummary(), reportedByMe)
        TeacherReviewKind.REVIEWS -> ReviewOrigin.Reviews(
            sourceTitle = sourceTitle.clean(),
            sourceUrl = sourceLink.clean()?.takeIf(HttpsNavigationPolicy::isNavigable) ?: providerUrl,
        )
        TeacherReviewKind.UNKNOWN -> return null
    }
    return TeacherReview(
        id = id.toString(),
        subject = subjectTitle.clean(),
        written = writtenOn?.toMonth() ?: writtenBeforeYear?.let(ReviewDate::BeforeYear),
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
    status = status.toModel(),
    reviewNote = reviewNote.clean(),
    score = score,
    verified = verified,
    written = writtenOn.toMonth(),
)

/** A status a newer Backend adds shows as waiting for review: the app never claims a review is public. */
private fun TeacherReviewStatus.toModel(): OwnReviewStatus = when (this) {
    TeacherReviewStatus.PENDING, TeacherReviewStatus.UNKNOWN -> OwnReviewStatus.PENDING
    TeacherReviewStatus.PUBLISHED -> OwnReviewStatus.PUBLISHED
    TeacherReviewStatus.REJECTED -> OwnReviewStatus.REJECTED
    TeacherReviewStatus.HIDDEN -> OwnReviewStatus.HIDDEN
}

/**
 * Tags this app does not know are skipped; a summary without a description is no summary. A scale of an unknown
 * kind or value is left out, so the card shows its kind as "мало данных". An unknown tone or confidence keeps the
 * summary with its tone hidden ([SummaryConfidence.LOW]).
 */
internal fun WireTeacherSummary.toModel(): TeacherSummary? {
    val description = description.clean() ?: return null
    val tone = level.toModel()
    return TeacherSummary(
        reviewCount = reviewCount,
        description = description,
        pros = pros.mapNotNull { it.clean() },
        cons = cons.mapNotNull { it.clean() },
        tags = tags.mapNotNull { code -> SummaryTag.entries.firstOrNull { it.name == code.trim() } }.distinct(),
        scales = scales.mapNotNull { scale ->
            val kind = scale.kind.toModel() ?: return@mapNotNull null
            val value = scale.value.toModel() ?: return@mapNotNull null
            SummaryScale(kind, value, scale.reason.clean())
        },
        level = tone ?: TeacherLevel.MIXED,
        confidence = if (tone == null) SummaryConfidence.LOW else confidence.toModel(),
    )
}

/** Null for a tone this app does not know: such a teacher shows no tone. */
internal fun WireSummaryLevel.toModel(): TeacherLevel? = when (this) {
    WireSummaryLevel.VERY_NEGATIVE -> TeacherLevel.VERY_NEGATIVE
    WireSummaryLevel.NEGATIVE -> TeacherLevel.NEGATIVE
    WireSummaryLevel.MIXED -> TeacherLevel.MIXED
    WireSummaryLevel.POSITIVE -> TeacherLevel.POSITIVE
    WireSummaryLevel.VERY_POSITIVE -> TeacherLevel.VERY_POSITIVE
    WireSummaryLevel.UNKNOWN -> null
}

private fun WireConfidence.toModel(): SummaryConfidence = when (this) {
    WireConfidence.LOW, WireConfidence.UNKNOWN -> SummaryConfidence.LOW
    WireConfidence.MEDIUM -> SummaryConfidence.MEDIUM
    WireConfidence.HIGH -> SummaryConfidence.HIGH
}

private fun WireScaleKind.toModel(): SummaryScaleKind? = when (this) {
    WireScaleKind.EXPLAINS -> SummaryScaleKind.EXPLAINS
    WireScaleKind.ATTITUDE -> SummaryScaleKind.ATTITUDE
    WireScaleKind.FAIRNESS -> SummaryScaleKind.FAIRNESS
    WireScaleKind.STRICTNESS -> SummaryScaleKind.STRICTNESS
    WireScaleKind.WORKLOAD -> SummaryScaleKind.WORKLOAD
    WireScaleKind.UNKNOWN -> null
}

private fun WireScaleValue.toModel(): SummaryScaleValue? = when (this) {
    WireScaleValue.LOW -> SummaryScaleValue.LOW
    WireScaleValue.MEDIUM -> SummaryScaleValue.MEDIUM
    WireScaleValue.HIGH -> SummaryScaleValue.HIGH
    WireScaleValue.NOT_ENOUGH_DATA -> SummaryScaleValue.NOT_ENOUGH_DATA
    WireScaleValue.UNKNOWN -> null
}

private fun LocalDate.toMonth() = ReviewDate.Month(YearMonth(year, month))

private fun String?.clean(): String? = this?.trim()?.takeIf(String::isNotEmpty)
