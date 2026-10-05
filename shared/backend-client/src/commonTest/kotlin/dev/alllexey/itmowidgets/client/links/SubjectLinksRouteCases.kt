package dev.alllexey.itmowidgets.client.links

import dev.alllexey.itmowidgets.client.common.ModerationReportRequest
import dev.alllexey.itmowidgets.client.common.ReportReason
import dev.alllexey.itmowidgets.client.common.ResourceVoteRequest
import dev.alllexey.itmowidgets.client.links.ResourceContractFixtures.PERIOD
import dev.alllexey.itmowidgets.client.links.ResourceContractFixtures.SUBJECT_ID
import dev.alllexey.itmowidgets.client.support.RouteCase

/** One [RouteCase] per public function of [SubjectLinksApi], with synthetic arguments. */
object SubjectLinksRouteCases {
    private val fixtures = ResourceContractFixtures

    val pin = PinSubjectLinkRequest(PERIOD, fixtures.id)
    val report = ModerationReportRequest(ReportReason.BROKEN, "Не открывается")

    val subjectLinks = RouteCase("subjectLinks") { links.subjectLinks(SUBJECT_ID, PERIOD) }
    val saveSubjectLink = RouteCase("saveSubjectLink") { links.saveSubjectLink(fixtures.id, fixtures.save) }
    val deleteSubjectLink = RouteCase("deleteSubjectLink") { links.deleteSubjectLink(fixtures.id) }
    val pinSubjectLink = RouteCase("pinSubjectLink") { links.pinSubjectLink(SUBJECT_ID, pin) }
    val voteSubjectLink = RouteCase("voteSubjectLink") { links.voteSubjectLink(fixtures.id, ResourceVoteRequest(-1)) }
    val reportSubjectLink = RouteCase("reportSubjectLink") { links.reportSubjectLink(fixtures.id, report) }
    val myRestrictions = RouteCase("myRestrictions") { links.myRestrictions() }

    val all: List<RouteCase> = listOf(
        subjectLinks,
        saveSubjectLink,
        deleteSubjectLink,
        pinSubjectLink,
        voteSubjectLink,
        reportSubjectLink,
        myRestrictions,
    )
}
