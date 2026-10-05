package dev.alllexey.itmowidgets.client.reviews

import dev.alllexey.itmowidgets.client.common.ResourceVoteRequest
import dev.alllexey.itmowidgets.client.contract.RequestClaim
import dev.alllexey.itmowidgets.client.contract.ResponseClaim
import dev.alllexey.itmowidgets.client.contract.assertAreaClaims
import dev.alllexey.itmowidgets.client.support.runSuspend
import kotlinx.serialization.builtins.ListSerializer
import kotlin.test.Test

/**
 * Backend's vendored reviews fixtures (`claims/reviews.txt`): every answer decodes through [TeacherReviewsApi] and
 * re-encodes to the fixture's `data`, the save body round-trips. The vote and report bodies are shared with subject
 * links and claimed there.
 */
class TeacherReviewsVendoredFixturesTest {

    private val fixtures = TeacherReviewContractFixtures

    private val responses = listOf(
        ResponseClaim("http/reviews/teacherReviews.json", TeacherReviewsResponse.serializer()) {
            reviews.teacherReviews(TEACHER)
        },
        ResponseClaim("http/reviews/saveMyTeacherReview.json", TeacherReviewsResponse.serializer()) {
            reviews.saveMyTeacherReview(TEACHER, fixtures.save)
        },
        ResponseClaim("http/reviews/deleteMyTeacherReview.json", TeacherReviewsResponse.serializer()) {
            reviews.deleteMyTeacherReview(TEACHER)
        },
        ResponseClaim("http/reviews/voteTeacherReview.json", TeacherReviewsResponse.serializer()) {
            reviews.voteTeacherReview(fixtures.namedId, ResourceVoteRequest(1))
        },
        ResponseClaim("http/reviews/reportTeacherReview.json", TeacherReviewsResponse.serializer()) {
            reviews.reportTeacherReview(fixtures.anonymousId, TeacherReviewsRouteCases.report)
        },
        ResponseClaim("http/reviews/teacherSummaryLevels.json", ListSerializer(TeacherSummaryLevel.serializer())) {
            reviews.teacherSummaryLevels(listOf(TEACHER))
        },
    )

    private val requests = listOf(
        RequestClaim("requests/SaveTeacherReviewRequest.json", SaveTeacherReviewRequest.serializer()),
    )

    @Test
    fun everyClaimDecodesAndRoundTrips() = runSuspend { assertAreaClaims("reviews", responses, requests) }

    private companion object {
        const val TEACHER = 200001
    }
}
