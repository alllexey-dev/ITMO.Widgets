package dev.alllexey.itmowidgets.feature.reviews.data

import dev.alllexey.itmowidgets.core.model.reviews.ExternalTeacherReview as WireReview
import dev.alllexey.itmowidgets.core.model.reviews.TeacherReviewsResponse as WireTeacherReviews
import dev.alllexey.itmowidgets.core.reviews.ExternalTeacherReview
import dev.alllexey.itmowidgets.core.reviews.ReviewDate
import dev.alllexey.itmowidgets.core.reviews.TeacherReviews
import dev.alllexey.itmowidgets.core.util.HttpsNavigationPolicy
import java.time.YearMonth

internal fun WireTeacherReviews.toModel() = TeacherReviews(
    isu = teacherIsu,
    external = external.mapNotNull { it.toModel(providerUrl.trim()) },
)

private fun WireReview.toModel(providerUrl: String): ExternalTeacherReview? {
    val text = text.clean() ?: return null
    return ExternalTeacherReview(
        id = id.toString(),
        subject = subjectTitle.clean(),
        written = writtenOn?.let { ReviewDate.Month(YearMonth.from(it)) }
            ?: writtenBeforeYear?.let(ReviewDate::BeforeYear),
        sourceTitle = sourceTitle.clean(),
        sourceUrl = sourceLink.clean()?.takeIf(HttpsNavigationPolicy::isNavigable) ?: providerUrl,
        text = text,
    )
}

private fun String?.clean(): String? = this?.trim()?.takeIf(String::isNotEmpty)
