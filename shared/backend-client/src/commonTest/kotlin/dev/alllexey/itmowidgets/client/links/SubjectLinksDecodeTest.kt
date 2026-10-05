package dev.alllexey.itmowidgets.client.links

import dev.alllexey.itmowidgets.client.common.ModerationReportRequest
import dev.alllexey.itmowidgets.client.common.ReportReason
import dev.alllexey.itmowidgets.client.common.ResourceVoteRequest
import dev.alllexey.itmowidgets.client.json.BackendJson
import dev.alllexey.itmowidgets.client.links.ResourceContractFixtures.PERIOD
import dev.alllexey.itmowidgets.client.links.ResourceContractFixtures.SUBJECT_ID
import dev.alllexey.itmowidgets.client.links.ResourceContractFixtures.envelope
import dev.alllexey.itmowidgets.client.support.MockBackend
import dev.alllexey.itmowidgets.client.support.ok
import dev.alllexey.itmowidgets.client.support.runSuspend
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

/** What every [SubjectLinksApi] call returns for a synthetic Backend answer. */
class SubjectLinksDecodeTest {

    private val fixtures = ResourceContractFixtures

    private val linksJson = BackendJson.encodeToString(SubjectLinksResponse.serializer(), fixtures.links)
    private val linkJson = BackendJson.encodeToString(SubjectLink.serializer(), fixtures.link)

    private fun answering(data: String) = MockBackend { ok(envelope(data)) }

    @Test
    fun subjectLinks() = runSuspend {
        assertEquals(fixtures.links, answering(linksJson).client.links.subjectLinks(SUBJECT_ID, PERIOD))
    }

    @Test
    fun saveSubjectLink() = runSuspend {
        val ownJson = BackendJson.encodeToString(SubjectLink.serializer(), fixtures.ownLink)

        val result = answering(ownJson).client.links.saveSubjectLink(fixtures.id, fixtures.privateSave)

        assertEquals(fixtures.ownLink, result)
    }

    @Test
    fun deleteSubjectLinkNeedsNoData() = runSuspend {
        // Backend answers ApiResponse<Unit>, which Jackson writes as `{}`; an absent `data` is fine as well.
        for (body in listOf(envelope("{}"), """{"success":true}""")) {
            val backend = MockBackend { ok(body) }

            backend.client.links.deleteSubjectLink(fixtures.id)

            assertEquals(1, backend.requests.size)
        }
    }

    @Test
    fun pinSubjectLink() = runSuspend {
        val unpinned = fixtures.links.copy(pinnedId = null)
        val body = BackendJson.encodeToString(SubjectLinksResponse.serializer(), unpinned)

        assertEquals(unpinned, answering(body).client.links.pinSubjectLink(SUBJECT_ID, PinSubjectLinkRequest(PERIOD)))
    }

    @Test
    fun voteSubjectLink() = runSuspend {
        val result = answering(linkJson).client.links.voteSubjectLink(fixtures.id, ResourceVoteRequest(1))

        assertEquals(fixtures.link, result)
    }

    @Test
    fun reportSubjectLink() = runSuspend {
        val reported = fixtures.link.copy(reportedByMe = true)
        val body = BackendJson.encodeToString(SubjectLink.serializer(), reported)

        val result = answering(body).client.links
            .reportSubjectLink(fixtures.id, ModerationReportRequest(ReportReason.SPAM, null))

        assertEquals(reported, result)
    }

    @Test
    fun myRestrictions() = runSuspend {
        val body = """[{"id":"00000000-0000-0000-0000-000000000042","capability":"VOTE","reason":"Правила",
            "startsAt":"2026-09-22T12:00:00+03:00","expiresAt":null},
            {"id":"00000000-0000-0000-0000-000000000043","capability":"FUTURE_CAPABILITY","reason":"Правила",
            "startsAt":"2026-09-22T09:00:00Z","expiresAt":"2026-09-29T09:00:00Z"}]"""

        val result = answering(body).client.links.myRestrictions()

        assertEquals(
            listOf(
                fixtures.restriction,
                fixtures.restriction.copy(
                    id = fixtures.otherId,
                    capability = RestrictionCapability.ALL,
                    expiresAt = Instant.parse("2026-09-29T09:00:00Z"),
                ),
            ),
            result,
        )
    }

    @Test
    fun noRestrictionsIsAnEmptyList() = runSuspend {
        val result = answering("[]").client.links.myRestrictions()

        assertEquals(emptyList(), result)
    }
}
