package dev.alllexey.itmowidgets.client.reviews

import dev.alllexey.itmowidgets.client.common.ModerationReportRequest
import dev.alllexey.itmowidgets.client.common.ResourceVoteRequest
import dev.alllexey.itmowidgets.client.http.BackendBody
import dev.alllexey.itmowidgets.client.http.BackendHttp
import dev.alllexey.itmowidgets.client.http.BackendRoute
import dev.alllexey.itmowidgets.client.http.jsonBody
import io.ktor.http.HttpMethod
import kotlinx.serialization.builtins.ListSerializer
import kotlin.uuid.Uuid

internal class KtorTeacherReviewsApi(private val http: BackendHttp) : TeacherReviewsApi {

    override suspend fun teacherReviews(isu: Int): TeacherReviewsResponse =
        reviews(BackendRoute(HttpMethod.Get, teacherSegments(isu)))

    override suspend fun saveMyTeacherReview(isu: Int, request: SaveTeacherReviewRequest): TeacherReviewsResponse =
        reviews(BackendRoute(HttpMethod.Put, teacherSegments(isu) + "mine", body = jsonBody(request)))

    override suspend fun deleteMyTeacherReview(isu: Int): TeacherReviewsResponse =
        reviews(BackendRoute(HttpMethod.Delete, teacherSegments(isu) + "mine"))

    override suspend fun voteTeacherReview(id: Uuid, request: ResourceVoteRequest): TeacherReviewsResponse =
        reviews(reviewRoute(HttpMethod.Put, id, "vote", jsonBody(request)))

    override suspend fun reportTeacherReview(id: Uuid, request: ModerationReportRequest): TeacherReviewsResponse =
        reviews(reviewRoute(HttpMethod.Post, id, "report", jsonBody(request)))

    override suspend fun teacherSummaryLevels(isus: List<Int>): List<TeacherSummaryLevel> =
        http.call(
            BackendRoute(
                HttpMethod.Get,
                listOf("api", "teachers", "summary-levels"),
                query = isus.map { "isu" to it.toString() },
            ),
            ListSerializer(TeacherSummaryLevel.serializer()),
        )

    private suspend fun reviews(route: BackendRoute): TeacherReviewsResponse =
        http.call(route, TeacherReviewsResponse.serializer())

    private fun teacherSegments(isu: Int) = listOf("api", "teachers", isu.toString(), "reviews")

    private fun reviewRoute(method: HttpMethod, id: Uuid, action: String, body: BackendBody) =
        BackendRoute(method, listOf("api", "reviews", id.toHexDashString(), action), body = body)
}
