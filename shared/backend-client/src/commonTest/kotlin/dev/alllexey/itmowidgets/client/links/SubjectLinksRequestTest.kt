package dev.alllexey.itmowidgets.client.links

import dev.alllexey.itmowidgets.client.error.BackendException
import dev.alllexey.itmowidgets.client.json.BackendJson
import dev.alllexey.itmowidgets.client.links.ResourceContractFixtures.PERIOD
import dev.alllexey.itmowidgets.client.links.ResourceContractFixtures.SUBJECT_ID
import dev.alllexey.itmowidgets.client.support.MockBackend
import dev.alllexey.itmowidgets.client.support.assertRequest
import dev.alllexey.itmowidgets.client.support.recordRequest
import dev.alllexey.itmowidgets.client.support.runSuspend
import io.ktor.http.HttpMethod
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** The method, path, query and body of every [SubjectLinksApi] call ([SubjectLinksRouteCases]). */
class SubjectLinksRequestTest {

    private val id = "00000000-0000-0000-0000-000000000042"

    @Test
    fun everyPublicFunctionHasOneCase() {
        assertEquals(7, SubjectLinksRouteCases.all.size)
        assertEquals(SubjectLinksRouteCases.all.size, SubjectLinksRouteCases.all.map { it.name }.toSet().size)
    }

    @Test
    fun subjectLinks() = runSuspend {
        SubjectLinksRouteCases.subjectLinks.assertRequest(
            HttpMethod.Get,
            "/api/subjects/42/links",
            query = listOf("period" to PERIOD),
        )
    }

    @Test
    fun saveSubjectLink() = runSuspend {
        SubjectLinksRouteCases.saveSubjectLink.assertRequest(
            HttpMethod.Put,
            "/api/links/$id",
            body = """{"subjectId":42,"subjectName":"Предмет","periodKey":"2026-1","category":"QUEUE",
                "url":"https://example.invalid/queue","visibility":"FLOW","flowId":7103}""",
        )
    }

    @Test
    fun deleteSubjectLink() = runSuspend {
        SubjectLinksRouteCases.deleteSubjectLink.assertRequest(HttpMethod.Delete, "/api/links/$id")
    }

    @Test
    fun pinSubjectLink() = runSuspend {
        SubjectLinksRouteCases.pinSubjectLink.assertRequest(
            HttpMethod.Put,
            "/api/subjects/42/links/pin",
            body = """{"periodKey":"2026-1","linkId":"$id"}""",
        )
    }

    @Test
    fun voteSubjectLink() = runSuspend {
        SubjectLinksRouteCases.voteSubjectLink.assertRequest(
            HttpMethod.Put,
            "/api/links/$id/vote",
            body = """{"value":-1}""",
        )
    }

    @Test
    fun reportSubjectLink() = runSuspend {
        SubjectLinksRouteCases.reportSubjectLink.assertRequest(
            HttpMethod.Post,
            "/api/links/$id/report",
            body = """{"reason":"BROKEN","comment":"Не открывается"}""",
        )
    }

    @Test
    fun myRestrictions() = runSuspend {
        SubjectLinksRouteCases.myRestrictions.assertRequest(HttpMethod.Get, "/api/users/me/restrictions")
    }

    @Test
    fun periodIsOneQueryValue() = runSuspend {
        val request = recordRequest("subjectLinks") { client.links.subjectLinks(SUBJECT_ID, "2026-1&x=1 2") }

        assertEquals("/api/subjects/42/links", request.path)
        assertEquals(listOf("period" to "2026-1&x=1 2"), request.query)
    }

    @Test
    fun aPrivateSaveOmitsFlowIdAndTitle() = runSuspend {
        val request = recordRequest("saveSubjectLink") {
            client.links.saveSubjectLink(ResourceContractFixtures.id, ResourceContractFixtures.privateSave)
        }

        assertEquals(
            BackendJson.parseToJsonElement(
                """{"subjectId":42,"subjectName":"Предмет","periodKey":"2026-1","category":"QUEUE",
                "url":"https://example.invalid/queue","visibility":"PRIVATE"}""",
            ),
            BackendJson.parseToJsonElement(request.body.orEmpty()),
        )
    }

    @Test
    fun clearingThePinSendsOnlyThePeriod() = runSuspend {
        val request = recordRequest("pinSubjectLink") {
            client.links.pinSubjectLink(SUBJECT_ID, PinSubjectLinkRequest(PERIOD))
        }

        assertEquals("""{"periodKey":"2026-1"}""", request.body)
    }

    @Test
    fun anUnknownCategoryIsNeverSent() = runSuspend {
        val backend = MockBackend()
        val request = ResourceContractFixtures.privateSave.copy(category = LinkCategory.UNKNOWN)

        // UNKNOWN is decode-only: the body fails to encode, a contract failure before anything reaches Backend.
        assertFailsWith<BackendException.Contract> {
            backend.client.links.saveSubjectLink(ResourceContractFixtures.id, request)
        }
        assertEquals(0, backend.requests.size)
    }
}
