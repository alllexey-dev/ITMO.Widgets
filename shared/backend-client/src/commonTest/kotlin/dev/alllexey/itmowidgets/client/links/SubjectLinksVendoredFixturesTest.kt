package dev.alllexey.itmowidgets.client.links

import dev.alllexey.itmowidgets.client.common.ModerationReportRequest
import dev.alllexey.itmowidgets.client.common.ResourceVoteRequest
import dev.alllexey.itmowidgets.client.contract.RequestClaim
import dev.alllexey.itmowidgets.client.contract.ResponseClaim
import dev.alllexey.itmowidgets.client.contract.assertAreaClaims
import dev.alllexey.itmowidgets.client.support.runSuspend
import kotlinx.serialization.builtins.ListSerializer
import kotlin.test.Test

/**
 * Backend's vendored links fixtures (`claims/links.txt`), plus `http/users/myRestrictions.json` that [SubjectLinksApi]
 * mirrors: every answer decodes through the API and re-encodes to the fixture's `data`, every request body
 * round-trips. The vote and report bodies are shared with teacher reviews and claimed here once.
 */
class SubjectLinksVendoredFixturesTest {

    private val fixtures = ResourceContractFixtures

    private val responses = listOf(
        ResponseClaim("http/links/subjectLinks.json", SubjectLinksResponse.serializer()) {
            links.subjectLinks(SUBJECT_ID, PERIOD)
        },
        ResponseClaim("http/links/saveSubjectLink.json", SubjectLink.serializer()) {
            links.saveSubjectLink(fixtures.id, fixtures.save)
        },
        ResponseClaim("http/links/deleteSubjectLink.json", null) { links.deleteSubjectLink(fixtures.id) },
        ResponseClaim("http/links/pinSubjectLink.json", SubjectLinksResponse.serializer()) {
            links.pinSubjectLink(SUBJECT_ID, SubjectLinksRouteCases.pin)
        },
        ResponseClaim("http/links/voteSubjectLink.json", SubjectLink.serializer()) {
            links.voteSubjectLink(fixtures.id, ResourceVoteRequest(1))
        },
        ResponseClaim("http/links/reportSubjectLink.json", SubjectLink.serializer()) {
            links.reportSubjectLink(fixtures.id, SubjectLinksRouteCases.report)
        },
        ResponseClaim("http/users/myRestrictions.json", ListSerializer(UserRestriction.serializer())) {
            links.myRestrictions()
        },
    )

    private val requests = listOf(
        RequestClaim("requests/ModerationReportRequest.json", ModerationReportRequest.serializer()),
        RequestClaim("requests/PinSubjectLinkRequest.json", PinSubjectLinkRequest.serializer()),
        RequestClaim("requests/ResourceVoteRequest.json", ResourceVoteRequest.serializer()),
        RequestClaim("requests/SaveSubjectLinkRequest.json", SaveSubjectLinkRequest.serializer()),
    )

    @Test
    fun everyClaimDecodesAndRoundTrips() = runSuspend { assertAreaClaims("links", responses, requests) }

    private companion object {
        const val SUBJECT_ID = 501L
        const val PERIOD = "2026-1"
    }
}
