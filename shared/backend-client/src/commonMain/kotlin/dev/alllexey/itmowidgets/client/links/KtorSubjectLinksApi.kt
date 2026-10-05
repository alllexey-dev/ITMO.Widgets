package dev.alllexey.itmowidgets.client.links

import dev.alllexey.itmowidgets.client.common.ModerationReportRequest
import dev.alllexey.itmowidgets.client.common.ResourceVoteRequest
import dev.alllexey.itmowidgets.client.http.BackendHttp
import dev.alllexey.itmowidgets.client.http.BackendRoute
import dev.alllexey.itmowidgets.client.http.jsonBody
import io.ktor.http.HttpMethod
import kotlinx.serialization.builtins.ListSerializer
import kotlin.uuid.Uuid

internal class KtorSubjectLinksApi(private val http: BackendHttp) : SubjectLinksApi {

    override suspend fun subjectLinks(subjectId: Long, period: String): SubjectLinksResponse =
        http.call(
            BackendRoute(HttpMethod.Get, subjectSegments(subjectId), query = listOf("period" to period)),
            SubjectLinksResponse.serializer(),
        )

    override suspend fun saveSubjectLink(id: Uuid, request: SaveSubjectLinkRequest): SubjectLink =
        http.call(
            BackendRoute(HttpMethod.Put, linkSegments(id), body = jsonBody(request)),
            SubjectLink.serializer(),
        )

    override suspend fun deleteSubjectLink(id: Uuid) =
        http.callUnit(BackendRoute(HttpMethod.Delete, linkSegments(id)))

    override suspend fun pinSubjectLink(subjectId: Long, request: PinSubjectLinkRequest): SubjectLinksResponse =
        http.call(
            BackendRoute(HttpMethod.Put, subjectSegments(subjectId) + "pin", body = jsonBody(request)),
            SubjectLinksResponse.serializer(),
        )

    override suspend fun voteSubjectLink(id: Uuid, request: ResourceVoteRequest): SubjectLink =
        http.call(
            BackendRoute(HttpMethod.Put, linkSegments(id) + "vote", body = jsonBody(request)),
            SubjectLink.serializer(),
        )

    override suspend fun reportSubjectLink(id: Uuid, request: ModerationReportRequest): SubjectLink =
        http.call(
            BackendRoute(HttpMethod.Post, linkSegments(id) + "report", body = jsonBody(request)),
            SubjectLink.serializer(),
        )

    override suspend fun myRestrictions(): List<UserRestriction> =
        http.call(
            BackendRoute(HttpMethod.Get, listOf("api", "users", "me", "restrictions")),
            ListSerializer(UserRestriction.serializer()),
        )

    private fun subjectSegments(subjectId: Long) = listOf("api", "subjects", subjectId.toString(), "links")

    private fun linkSegments(id: Uuid) = listOf("api", "links", id.toHexDashString())
}
