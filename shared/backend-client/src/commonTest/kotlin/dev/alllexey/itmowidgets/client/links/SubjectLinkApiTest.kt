package dev.alllexey.itmowidgets.client.links

import dev.alllexey.itmowidgets.client.BackendClient
import dev.alllexey.itmowidgets.client.common.ModerationReportRequest
import dev.alllexey.itmowidgets.client.common.ReportReason
import dev.alllexey.itmowidgets.client.common.ResourceVoteRequest
import dev.alllexey.itmowidgets.client.json.BackendJson
import dev.alllexey.itmowidgets.client.links.ResourceContractFixtures.envelope
import dev.alllexey.itmowidgets.client.support.MockBackend
import dev.alllexey.itmowidgets.client.support.ok
import dev.alllexey.itmowidgets.client.support.runSuspend
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.content.OutgoingContent
import io.ktor.http.content.TextContent
import io.ktor.util.flattenEntries
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.fail

/**
 * Port of Core 1.x `resources/SubjectLinkApiTest`. Not ported: `moderator routes are separate exact and decode the
 * link case target`, since the moderation API is not mirrored (CO-02). The 1.x reflection check that the removed
 * resource routes are gone has no 2.0 counterpart: [SubjectLinksApi] is new and `everyPublicFunctionHasOneCase`
 * pins its size.
 */
class SubjectLinkApiTest {

    private val fixtures = ResourceContractFixtures

    private class Call<T>(
        val method: HttpMethod,
        val path: String,
        val query: List<Pair<String, String>>,
        val body: Any?,
        val reply: T,
        val replySerializer: KSerializer<T>?,
        val run: suspend BackendClient.() -> T,
    )

    @Test
    fun everyLinkRouteHasAnExactVerbPathQueryBodyAndTypedReply() = runSuspend {
        val id = fixtures.id
        val pin = PinSubjectLinkRequest("2026-1", id)
        val unpin = PinSubjectLinkRequest("2026-1")
        val report = ModerationReportRequest(ReportReason.BROKEN, "Не открывается")
        val vote = ResourceVoteRequest(-1)
        val linkSerializer = SubjectLink.serializer()
        val linksSerializer = SubjectLinksResponse.serializer()
        val calls = listOf(
            Call(
                HttpMethod.Get,
                "/api/subjects/42/links",
                listOf("period" to "2026-1"),
                null,
                fixtures.links,
                linksSerializer,
            ) { links.subjectLinks(42, "2026-1") },
            Call(HttpMethod.Put, "/api/links/$id", emptyList(), fixtures.save, fixtures.link, linkSerializer) {
                links.saveSubjectLink(id, fixtures.save)
            },
            Call(
                HttpMethod.Put,
                "/api/links/$id",
                emptyList(),
                fixtures.privateSave,
                fixtures.ownLink,
                linkSerializer,
            ) { links.saveSubjectLink(id, fixtures.privateSave) },
            Call(HttpMethod.Delete, "/api/links/$id", emptyList(), null, Unit, null) { links.deleteSubjectLink(id) },
            Call(HttpMethod.Put, "/api/subjects/42/links/pin", emptyList(), pin, fixtures.links, linksSerializer) {
                links.pinSubjectLink(42, pin)
            },
            Call(
                HttpMethod.Put,
                "/api/subjects/42/links/pin",
                emptyList(),
                unpin,
                fixtures.links.copy(pinnedId = null),
                linksSerializer,
            ) { links.pinSubjectLink(42, unpin) },
            Call(HttpMethod.Put, "/api/links/$id/vote", emptyList(), vote, fixtures.link, linkSerializer) {
                links.voteSubjectLink(id, vote)
            },
            Call(HttpMethod.Post, "/api/links/$id/report", emptyList(), report, fixtures.link, linkSerializer) {
                links.reportSubjectLink(id, report)
            },
            Call(
                HttpMethod.Get,
                "/api/users/me/restrictions",
                emptyList(),
                null,
                listOf(fixtures.restriction),
                ListSerializer(UserRestriction.serializer()),
            ) { links.myRestrictions() },
        )
        for (call in calls) assertCall(call)
    }

    private suspend fun <T> assertCall(call: Call<T>) {
        val data = call.replySerializer?.let { BackendJson.encodeToString(it, call.reply) } ?: "{}"
        val backend = MockBackend { ok(envelope(data)) }

        val result = backend.client.(call.run)()

        assertEquals(call.reply, result, call.path)
        val request = backend.lastRequest
        assertEquals(call.method, request.method, call.path)
        assertEquals(call.path, request.url.encodedPath)
        assertEquals(call.query, request.url.parameters.flattenEntries(), call.path)
        val expectedBody = call.body?.let { encodeBody(it) }
        assertEquals(expectedBody, request.bodyText()?.let(BackendJson::parseToJsonElement), call.path)
        assertNull(request.headers[HttpHeaders.Authorization], call.path)
    }

    private fun encodeBody(body: Any) = when (body) {
        is SaveSubjectLinkRequest -> BackendJson.encodeToJsonElement(SaveSubjectLinkRequest.serializer(), body)
        is PinSubjectLinkRequest -> BackendJson.encodeToJsonElement(PinSubjectLinkRequest.serializer(), body)
        is ResourceVoteRequest -> BackendJson.encodeToJsonElement(ResourceVoteRequest.serializer(), body)
        is ModerationReportRequest -> BackendJson.encodeToJsonElement(ModerationReportRequest.serializer(), body)
        else -> fail("No serializer for ${body::class.simpleName}")
    }

    private fun HttpRequestData.bodyText(): String? = when (val content = body) {
        is OutgoingContent.NoContent -> null
        is TextContent -> content.text
        is OutgoingContent.ByteArrayContent -> content.bytes().decodeToString()
        else -> fail("Unexpected request body ${content::class.simpleName}")
    }
}
