package dev.alllexey.itmowidgets.core.reviews

import java.time.YearMonth

data class TeacherReviews(val isu: Int, val external: List<ExternalTeacherReview>)

data class ExternalTeacherReview(
    val id: String,
    val subject: String?,
    val written: ReviewDate?,
    val sourceTitle: String?,
    val sourceUrl: String,
    val text: String,
)

sealed interface ReviewDate {
    data class Month(val month: YearMonth) : ReviewDate
    data class BeforeYear(val year: Int) : ReviewDate
}
