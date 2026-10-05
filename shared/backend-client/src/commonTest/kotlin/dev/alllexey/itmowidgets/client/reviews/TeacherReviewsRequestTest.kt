package dev.alllexey.itmowidgets.client.reviews

import dev.alllexey.itmowidgets.client.common.ModerationReportRequest
import dev.alllexey.itmowidgets.client.common.ReportReason
import dev.alllexey.itmowidgets.client.reviews.TeacherReviewContractFixtures.TEACHER
import dev.alllexey.itmowidgets.client.reviews.TeacherReviewContractFixtures.TEXT
import dev.alllexey.itmowidgets.client.support.assertRequest
import dev.alllexey.itmowidgets.client.support.recordRequest
import dev.alllexey.itmowidgets.client.support.runSuspend
import io.ktor.http.HttpMethod
import kotlin.test.Test
import kotlin.test.assertEquals

/** The method, path, query and body of every [TeacherReviewsApi] call ([TeacherReviewsRouteCases]). */
class TeacherReviewsRequestTest {

    private val id = "00000000-0000-0000-0000-000000000071"

    @Test
    fun everyPublicFunctionHasOneCase() {
        assertEquals(6, TeacherReviewsRouteCases.all.size)
        assertEquals(TeacherReviewsRouteCases.all.size, TeacherReviewsRouteCases.all.map { it.name }.toSet().size)
    }

    @Test
    fun teacherReviews() = runSuspend {
        TeacherReviewsRouteCases.teacherReviews.assertRequest(HttpMethod.Get, "/api/teachers/$TEACHER/reviews")
    }

    @Test
    fun saveMyTeacherReview() = runSuspend {
        TeacherReviewsRouteCases.saveMyTeacherReview.assertRequest(
            HttpMethod.Put,
            "/api/teachers/$TEACHER/reviews/mine",
            body = """{"subjectTitle":"Математика","text":"$TEXT","anonymous":false,"flowIds":[93724,93725]}""",
        )
    }

    @Test
    fun deleteMyTeacherReview() = runSuspend {
        TeacherReviewsRouteCases.deleteMyTeacherReview.assertRequest(
            HttpMethod.Delete,
            "/api/teachers/$TEACHER/reviews/mine",
        )
    }

    @Test
    fun voteTeacherReview() = runSuspend {
        TeacherReviewsRouteCases.voteTeacherReview.assertRequest(
            HttpMethod.Put,
            "/api/reviews/$id/vote",
            body = """{"value":-1}""",
        )
    }

    @Test
    fun reportTeacherReview() = runSuspend {
        TeacherReviewsRouteCases.reportTeacherReview.assertRequest(
            HttpMethod.Post,
            "/api/reviews/$id/report",
            body = """{"reason":"WRONG_TEACHER","comment":"Вёл другой"}""",
        )
    }

    @Test
    fun teacherSummaryLevels() = runSuspend {
        TeacherReviewsRouteCases.teacherSummaryLevels.assertRequest(
            HttpMethod.Get,
            "/api/teachers/summary-levels",
            query = listOf("isu" to "123456", "isu" to "234567"),
        )
    }

    @Test
    fun aDefaultSaveAlwaysSendsAnonymousTrue() = runSuspend {
        val request = recordRequest("saveMyTeacherReview") {
            client.reviews.saveMyTeacherReview(TEACHER, SaveTeacherReviewRequest(text = TEXT))
        }

        // A body without `anonymous` would mean true on Backend as well; the client still never leaves it out.
        assertEquals("""{"text":"$TEXT","anonymous":true,"flowIds":[]}""", request.body)
    }

    @Test
    fun aReportWithoutCommentSendsOnlyTheReason() = runSuspend {
        val request = recordRequest("reportTeacherReview") {
            client.reviews.reportTeacherReview(
                TeacherReviewContractFixtures.copyId,
                ModerationReportRequest(ReportReason.OFFENSIVE),
            )
        }

        assertEquals("/api/reviews/00000000-0000-0000-0000-000000000073/report", request.path)
        assertEquals("""{"reason":"OFFENSIVE"}""", request.body)
    }

    @Test
    fun oneSummaryLevelIsOneIsuParameter() = runSuspend {
        val request = recordRequest("teacherSummaryLevels") { client.reviews.teacherSummaryLevels(listOf(123456)) }

        assertEquals("/api/teachers/summary-levels", request.path)
        assertEquals(listOf("isu" to "123456"), request.query)
        assertEquals(null, request.body)
    }
}
