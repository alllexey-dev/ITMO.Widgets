package dev.alllexey.itmowidgets.client.reviews

import dev.alllexey.itmowidgets.client.BackendClient
import dev.alllexey.itmowidgets.client.common.ModerationReportRequest
import dev.alllexey.itmowidgets.client.common.ReportReason
import dev.alllexey.itmowidgets.client.common.ResourceVoteRequest
import dev.alllexey.itmowidgets.client.error.BackendException
import dev.alllexey.itmowidgets.client.reviews.TeacherReviewContractFixtures.answering
import dev.alllexey.itmowidgets.client.reviews.TeacherReviewContractFixtures.responseJson
import dev.alllexey.itmowidgets.client.support.assertJsonEquals
import dev.alllexey.itmowidgets.client.support.runSuspend
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.content.OutgoingContent
import io.ktor.http.content.TextContent
import io.ktor.util.flattenEntries
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.fail

/**
 * Port of Core 1.x `reviews/TeacherReviewApiTest`, one test per 1.x test with the same name. MockWebServer calls
 * become [dev.alllexey.itmowidgets.client.support.MockBackend]; `ApiResponse` checks become the unwrapped value, and a
 * malformed answer is a [BackendException.Contract] instead of a Gson `JsonParseException`.
 */
class TeacherReviewApiTest {

    private val fixtures = TeacherReviewContractFixtures

    private class Call(
        val method: HttpMethod,
        val path: String,
        val body: String?,
        val reply: TeacherReviewsResponse,
        val run: suspend BackendClient.() -> TeacherReviewsResponse,
    )

    @Test
    fun everyReviewRouteHasAnExactVerbPathBodyAndTypedReply() = runSuspend {
        val id = fixtures.namedId
        val withoutMine = fixtures.response.copy(mine = null)
        val implicit = SaveTeacherReviewRequest(text = fixtures.TEXT)
        val calls = listOf(
            Call(HttpMethod.Get, "/api/teachers/100001/reviews", null, fixtures.response) {
                reviews.teacherReviews(100001)
            },
            Call(
                HttpMethod.Put,
                "/api/teachers/100001/reviews/mine",
                """{"subjectTitle":"Математика","text":"${fixtures.TEXT}","anonymous":false,"flowIds":[93724,93725]}""",
                fixtures.response,
            ) { reviews.saveMyTeacherReview(100001, fixtures.save) },
            Call(
                HttpMethod.Put,
                "/api/teachers/100001/reviews/mine",
                """{"text":"${fixtures.TEXT}","anonymous":true,"flowIds":[]}""",
                fixtures.response,
            ) { reviews.saveMyTeacherReview(100001, implicit) },
            Call(HttpMethod.Delete, "/api/teachers/100001/reviews/mine", null, withoutMine) {
                reviews.deleteMyTeacherReview(100001)
            },
            Call(HttpMethod.Put, "/api/reviews/$id/vote", """{"value":-1}""", fixtures.response) {
                reviews.voteTeacherReview(id, ResourceVoteRequest(-1))
            },
            Call(
                HttpMethod.Post,
                "/api/reviews/$id/report",
                """{"reason":"WRONG_TEACHER","comment":"Вёл другой"}""",
                fixtures.response,
            ) { reviews.reportTeacherReview(id, ModerationReportRequest(ReportReason.WRONG_TEACHER, "Вёл другой")) },
            Call(
                HttpMethod.Post,
                "/api/reviews/${fixtures.copyId}/report",
                """{"reason":"OFFENSIVE"}""",
                fixtures.response,
            ) { reviews.reportTeacherReview(fixtures.copyId, ModerationReportRequest(ReportReason.OFFENSIVE)) },
        )
        for (call in calls) assertCall(call)
    }

    @Test
    fun summaryLevelsRepeatTheIsuParameterAndReturnATypedList() = runSuspend {
        val backend = answering("""[{"teacherIsu":123456,"level":"POSITIVE"},{"teacherIsu":234567,"level":"MIXED"}]""")

        val result = backend.client.reviews.teacherSummaryLevels(listOf(123456, 234567))

        assertEquals(
            listOf(TeacherSummaryLevel(123456, SummaryLevel.POSITIVE), TeacherSummaryLevel(234567, SummaryLevel.MIXED)),
            result,
        )
        val request = backend.lastRequest
        assertEquals(HttpMethod.Get, request.method)
        assertEquals("/api/teachers/summary-levels", request.url.encodedPath)
        assertEquals(listOf("isu" to "123456", "isu" to "234567"), request.url.parameters.flattenEntries())
        assertNull(request.bodyText())
    }

    @Test
    fun aSummaryLevelWithAnUnknownToneIsRejected() = runSuspend {
        // 2.0 policy (13 Q7 (b)): SummaryLevel is a display enum, so an unknown tone decodes as UNKNOWN instead of
        // failing the list; 1.x rejected it. A tone that is not a string is still rejected.
        val unknown = answering("""[{"teacherIsu":123456,"level":"NEUTRAL"}]""")
        assertEquals(
            listOf(TeacherSummaryLevel(123456, SummaryLevel.UNKNOWN)),
            unknown.client.reviews.teacherSummaryLevels(listOf(123456)),
        )

        val numeric = answering("""[{"teacherIsu":123456,"level":2}]""")
        assertFailsWith<BackendException.Contract> { numeric.client.reviews.teacherSummaryLevels(listOf(123456)) }
    }

    @Test
    fun aReviewReplyCarriesItsSummary() = runSuspend {
        val withSummary = fixtures.response.copy(summary = fixtures.summary)

        val result = answering(responseJson(withSummary)).client.reviews.teacherReviews(100001)

        assertEquals(withSummary, result)
    }

    @Test
    fun aTeacherWithoutReviewsReturnsAnEmptyTypedResponse() = runSuspend {
        val providerUrl = "https://example.invalid/reviews/#/teacher/100002"
        val backend = answering(
            """{"teacherIsu":100002,"providerUrl":"$providerUrl","reviews":[],"mine":null,"canWrite":true,
            "canVote":true,"canReport":true,"knownTeacher":false,"summary":null}""",
        )

        val result = backend.client.reviews.teacherReviews(100002)

        assertEquals(
            TeacherReviewsResponse(
                teacherIsu = 100002,
                providerUrl = providerUrl,
                reviews = emptyList(),
                mine = null,
                canWrite = true,
                canVote = true,
                canReport = true,
                knownTeacher = false,
                summary = null,
            ),
            result,
        )
        assertEquals("/api/teachers/100002/reviews", backend.lastRequest.url.encodedPath)
    }

    @Test
    fun aResponseInTheReplacedExternalShapeIsRejected() = runSuspend {
        val backend = answering("""{"teacherIsu":100001,"providerUrl":"${fixtures.PROVIDER_URL}","external":[]}""")

        assertFailsWith<BackendException.Contract> { backend.client.reviews.teacherReviews(100001) }
    }

    private suspend fun assertCall(call: Call) {
        val backend = answering(responseJson(call.reply))

        val result = backend.client.(call.run)()

        assertEquals(call.reply, result, call.path)
        val request = backend.lastRequest
        assertEquals(call.method, request.method, call.path)
        assertEquals(call.path, request.url.encodedPath)
        assertEquals(emptyList(), request.url.parameters.flattenEntries(), call.path)
        val body = request.bodyText()
        when {
            call.body == null -> assertNull(body, call.path)
            body == null -> fail("${call.path} sent no body")
            else -> assertJsonEquals(call.body, body)
        }
        assertNull(request.headers[HttpHeaders.Authorization], call.path)
    }

    private fun HttpRequestData.bodyText(): String? = when (val content = body) {
        is OutgoingContent.NoContent -> null
        is TextContent -> content.text
        is OutgoingContent.ByteArrayContent -> content.bytes().decodeToString()
        else -> fail("Unexpected request body ${content::class.simpleName}")
    }
}
