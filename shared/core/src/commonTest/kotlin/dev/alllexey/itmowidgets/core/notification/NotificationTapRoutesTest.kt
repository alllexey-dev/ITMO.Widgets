package dev.alllexey.itmowidgets.core.notification

import dev.alllexey.itmowidgets.core.navigation.AppRoutes
import dev.alllexey.itmowidgets.core.navigation.AppTab
import dev.alllexey.itmowidgets.core.navigation.EntryRoute
import dev.alllexey.itmowidgets.core.navigation.TabRequest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Where a tapped Backend push opens the app, by its payload type; every ISU and lesson is synthetic. */
class NotificationTapRoutesTest {

    @Test
    fun aFriendshipEventOpensTheActorsProfile() {
        val data = envelope(
            "FRIENDSHIP_EVENT_PAYLOAD",
            """{"event":"REQUEST_RECEIVED","user":{"isu":100002,"name":"Synthetic"},""" +
                """"occurredAt":"2026-10-05T12:00+03:00"}"""
        )

        assertEquals(
            EntryRoute(AppTab.ME, overlay = AppRoutes.UserProfile(100002)),
            NotificationTapRoutes.entryRoute(data)
        )
    }

    @Test
    fun aSportBookingOfOneLessonOpensThatLesson() {
        for (type in listOf("SPORT_AUTO_SIGN_LESSONS_PAYLOAD", "SPORT_FREE_SIGN_LESSONS_PAYLOAD")) {
            val data = envelope(type, """{"sportLessons":[{"id":9001,"sectionName":"Synthetic"}]}""")

            assertEquals(
                EntryRoute(AppTab.SPORT, request = TabRequest.SportLesson(9001)),
                NotificationTapRoutes.entryRoute(data),
                type
            )
        }
    }

    @Test
    fun aSportBookingOfSeveralLessonsOpensTheSportTab() {
        val data = envelope("SPORT_AUTO_SIGN_LESSONS_PAYLOAD", """{"sportLessons":[{"id":9001},{"id":9002}]}""")

        assertEquals(EntryRoute(AppTab.SPORT), NotificationTapRoutes.entryRoute(data))
    }

    @Test
    fun aFriendshipWithoutAValidActorOpensNothing() {
        for (payload in listOf("""{"user":{"isu":0}}""", """{"user":"100002"}""", "{}")) {
            assertNull(NotificationTapRoutes.entryRoute(envelope("FRIENDSHIP_EVENT_PAYLOAD", payload)), payload)
        }
    }

    @Test
    fun unknownTypesAndBrokenEnvelopesOpenNothing() {
        assertNull(NotificationTapRoutes.entryRoute(envelope("SOMETHING_NEW", "{}")))
        assertNull(NotificationTapRoutes.entryRoute("not json"))
        assertNull(NotificationTapRoutes.entryRoute("""{"payload":{}}"""))
        assertNull(NotificationTapRoutes.entryRoute(null))
    }

    private fun envelope(type: String, payload: String) = """{"type":"$type","payload":$payload}"""
}
