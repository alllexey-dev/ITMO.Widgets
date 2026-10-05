package dev.alllexey.itmowidgets.client.schedule

import dev.alllexey.itmowidgets.client.common.RelationshipState
import dev.alllexey.itmowidgets.client.common.UserCapabilities
import dev.alllexey.itmowidgets.client.support.record
import dev.alllexey.itmowidgets.client.support.runSuspend
import dev.alllexey.itmowidgets.client.users.SyntheticUsers
import dev.alllexey.itmowidgets.client.users.SyntheticUsers.answering
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Port of Core 1.7.0 `schedule/LessonContextApiTest`. The Retrofit reflection check that `usersByPairId` is gone
 * becomes an assertion that every schedule route case sends one of the three mirrored routes.
 */
class LessonContextApiTest {

    private val capabilities = UserCapabilities(canViewSchedule = true, canViewSport = false, canViewFriends = false)
    private val friendJson = SyntheticUsers.profileJson(
        "FRIENDS",
        SyntheticUsers.capabilitiesJson(schedule = true, sport = false, friends = false),
    )

    @Test
    fun friendsOnALessonResolveThePairPathAndSendTheOccurrenceDateAsTheOnlyQuery() = runSuspend {
        val backend = answering("[$friendJson]")

        val friends = backend.client.schedule.friendsOnLesson(2_147_483_648L, ScheduleRouteCases.occurrence)

        assertEquals(listOf(SyntheticUsers.profile(RelationshipState.FRIENDS, capabilities)), friends)
        val request = backend.lastRequest
        assertEquals(HttpMethod.Get, request.method)
        assertEquals("/api/schedule/lessons/2147483648/friends", request.url.encodedPath)
        assertEquals("date=2026-09-08", request.url.encodedQuery)
        assertEquals(0L, request.body.contentLength ?: 0L)
        assertNull(request.headers[HttpHeaders.Authorization])
    }

    @Test
    fun anEmptyFriendListIsAPlainEmptyList() = runSuspend {
        val friends = answering("[]").client.schedule.friendsOnLesson(42, ScheduleRouteCases.occurrence)

        assertEquals(emptyList(), friends)
    }

    @Test
    fun theUnrestrictedParticipantCallIsGoneFromTheContract() = runSuspend {
        val mirrored = listOf(
            Regex("/api/schedule/lessons/sync"),
            Regex("/api/schedule/lessons/user/\\d+"),
            Regex("/api/schedule/lessons/\\d+/friends"),
        )
        for (case in ScheduleRouteCases.all) {
            val path = case.record().path
            assertTrue(mirrored.any { it.matches(path) }, "${case.name} sends $path")
        }
    }
}
