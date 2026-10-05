package dev.alllexey.itmowidgets.client.sport

import dev.alllexey.itmowidgets.client.contract.RequestClaim
import dev.alllexey.itmowidgets.client.contract.ResponseClaim
import dev.alllexey.itmowidgets.client.contract.assertAreaClaims
import dev.alllexey.itmowidgets.client.sport.model.FriendsSportBookingsResponse
import dev.alllexey.itmowidgets.client.sport.model.SportAutoSignLimits
import dev.alllexey.itmowidgets.client.sport.model.SportAutoSignRequest
import dev.alllexey.itmowidgets.client.sport.model.SportFreeSignRequest
import dev.alllexey.itmowidgets.client.sport.model.SportQueue
import dev.alllexey.itmowidgets.client.sport.model.SportQueueEntry
import dev.alllexey.itmowidgets.client.sport.model.UserSportBookingsResponse
import dev.alllexey.itmowidgets.client.support.runSuspend
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlin.test.Test

/**
 * Backend's vendored sport fixtures (`claims/sport.txt`): every answer decodes through [SportApi] and re-encodes to
 * the fixture's `data` through the base serializers, `"type"` included; the `Unit` answers only have to succeed,
 * and the three request bodies round-trip. Arguments do not matter, the fixture is the answer.
 */
class SportVendoredFixturesTest {

    private val entry = SportQueueEntry.serializer()
    private val entries = ListSerializer(SportQueueEntry.serializer())
    private val queues = ListSerializer(SportQueue.serializer())

    private val responses = listOf(
        ResponseClaim("http/sport/syncSportLessons.json", null) { sport.syncSportLessons(SportRouteCases.lessonIds) },
        ResponseClaim("http/sport/friendsSportBookings.json", FriendsSportBookingsResponse.serializer()) {
            sport.friendsSportBookings()
        },
        ResponseClaim("http/sport/userSportBookings.json", UserSportBookingsResponse.serializer()) {
            sport.userSportBookings(ISU)
        },
        ResponseClaim("http/sport-free-sign/mySportFreeSignEntries.json", entries) { sport.mySportFreeSignEntries() },
        ResponseClaim("http/sport-free-sign/createSportFreeSignEntry.json", entry) {
            sport.createSportFreeSignEntry(SportRouteCases.freeRequest)
        },
        ResponseClaim("http/sport-free-sign/cancelSportFreeSignEntry.json", null) {
            sport.cancelSportFreeSignEntry(SportRouteCases.ENTRY_ID)
        },
        ResponseClaim("http/sport-free-sign/cancelSportFreeSignEntryByLesson.json", null) {
            sport.cancelSportFreeSignEntryByLesson(SportRouteCases.LESSON_ID)
        },
        ResponseClaim("http/sport-free-sign/currentSportFreeSignQueues.json", queues) {
            sport.currentSportFreeSignQueues()
        },
        ResponseClaim("http/sport-free-sign/markSportFreeSignEntrySatisfiedByLesson.json", null) {
            sport.markSportFreeSignEntrySatisfiedByLesson(SportRouteCases.LESSON_ID)
        },
        ResponseClaim("http/sport-auto-sign/sportAutoSignLimits.json", SportAutoSignLimits.serializer()) {
            sport.sportAutoSignLimits()
        },
        ResponseClaim("http/sport-auto-sign/mySportAutoSignEntries.json", entries) { sport.mySportAutoSignEntries() },
        ResponseClaim("http/sport-auto-sign/createSportAutoSignEntry.json", entry) {
            sport.createSportAutoSignEntry(SportRouteCases.autoRequest)
        },
        ResponseClaim("http/sport-auto-sign/cancelSportAutoSignEntry.json", null) {
            sport.cancelSportAutoSignEntry(SportRouteCases.ENTRY_ID)
        },
        ResponseClaim("http/sport-auto-sign/cancelSportAutoSignEntryByLesson.json", null) {
            sport.cancelSportAutoSignEntryByLesson(SportRouteCases.LESSON_ID)
        },
        ResponseClaim("http/sport-auto-sign/currentSportAutoSignQueues.json", queues) {
            sport.currentSportAutoSignQueues()
        },
        ResponseClaim("http/sport-auto-sign/markSportAutoSignEntrySatisfiedByLesson.json", null) {
            sport.markSportAutoSignEntrySatisfiedByLesson(SportRouteCases.LESSON_ID)
        },
    )

    private val requests = listOf(
        RequestClaim("requests/SportLessonIds.json", ListSerializer(Long.serializer())),
        RequestClaim("requests/SportFreeSignRequest.json", SportFreeSignRequest.serializer()),
        RequestClaim("requests/SportAutoSignRequest.json", SportAutoSignRequest.serializer()),
    )

    @Test
    fun everyClaimDecodesAndRoundTrips() = runSuspend { assertAreaClaims("sport", responses, requests) }

    private companion object {
        const val ISU = 100002
    }
}
