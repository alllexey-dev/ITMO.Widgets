package dev.alllexey.itmowidgets.client.reviews

import dev.alllexey.itmowidgets.client.common.ModerationReportRequest
import dev.alllexey.itmowidgets.client.common.ReportReason
import dev.alllexey.itmowidgets.client.common.ResourceVoteRequest
import dev.alllexey.itmowidgets.client.reviews.TeacherReviewContractFixtures.TEACHER
import dev.alllexey.itmowidgets.client.support.RouteCase

/** One [RouteCase] per public function of [TeacherReviewsApi], with synthetic arguments. */
object TeacherReviewsRouteCases {
    private val fixtures = TeacherReviewContractFixtures

    val report = ModerationReportRequest(ReportReason.WRONG_TEACHER, "Вёл другой")
    val isus = listOf(123456, 234567)

    val teacherReviews = RouteCase("teacherReviews") { reviews.teacherReviews(TEACHER) }
    val saveMyTeacherReview = RouteCase("saveMyTeacherReview") { reviews.saveMyTeacherReview(TEACHER, fixtures.save) }
    val deleteMyTeacherReview = RouteCase("deleteMyTeacherReview") { reviews.deleteMyTeacherReview(TEACHER) }
    val voteTeacherReview = RouteCase("voteTeacherReview") {
        reviews.voteTeacherReview(fixtures.namedId, ResourceVoteRequest(-1))
    }
    val reportTeacherReview = RouteCase("reportTeacherReview") {
        reviews.reportTeacherReview(fixtures.namedId, report)
    }
    val teacherSummaryLevels = RouteCase("teacherSummaryLevels") { reviews.teacherSummaryLevels(isus) }

    val all: List<RouteCase> = listOf(
        teacherReviews,
        saveMyTeacherReview,
        deleteMyTeacherReview,
        voteTeacherReview,
        reportTeacherReview,
        teacherSummaryLevels,
    )
}
