package dev.alllexey.itmowidgets.core.notification

import dev.alllexey.itmowidgets.core.navigation.ActivityRoute
import dev.alllexey.itmowidgets.core.navigation.AppEntryIntents
import dev.alllexey.itmowidgets.core.navigation.AppRoutes
import dev.alllexey.itmowidgets.core.navigation.AppTab
import dev.alllexey.itmowidgets.core.navigation.EntryRoute
import dev.alllexey.itmowidgets.core.navigation.EntryRouteParser
import dev.alllexey.itmowidgets.core.navigation.RecordbookSubjectArgs
import dev.alllexey.itmowidgets.core.navigation.TabRequest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Where a tapped notification opens the app: a Backend push by its payload type, a local notification by the
 * `userInfo` of its destination, which must open what Android's notification intent opens. Every ISU, lesson and
 * subject is synthetic.
 */
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

    @Test
    fun everyLocalDestinationOpensWhatAndroidsIntentOpens() {
        val destinations = listOf(
            NotificationDestination.Sport,
            NotificationDestination.UserProfile(100002),
            NotificationDestination.ScheduleChanges,
            NotificationDestination.Recordbook,
            NotificationDestination.RecordbookSubject(subject),
            NotificationDestination.RecordbookSubject(subjectWithoutJournal),
            NotificationDestination.BarsLogin,
        )

        for (destination in destinations) {
            val userInfo = NotificationTapRoutes.userInfoOf(destination)

            assertEquals(expected(destination), NotificationTapRoutes.entryRoute(userInfo), destination.toString())
            assertEquals(android(destination), NotificationTapRoutes.entryRoute(userInfo), destination.toString())
        }
    }

    @Test
    fun aLocalUserInfoHoldsOnlyStrings() {
        val userInfo = NotificationTapRoutes.userInfoOf(NotificationDestination.RecordbookSubject(subject))

        assertEquals(
            mapOf(
                "action" to AppEntryIntents.ACTION_OPEN_RECORDBOOK_SUBJECT,
                "entry_id" to "11",
                "program_id" to "1",
                "semester" to "3",
                "study_year" to "2026/2027",
                "bars_plan" to "7",
                "bars_type" to "flow",
                "bars_identifier" to "7",
            ),
            userInfo,
        )
        assertEquals(
            mapOf("action" to AppEntryIntents.ACTION_OPEN_USER_PROFILE, "isu" to "100002"),
            NotificationTapRoutes.userInfoOf(NotificationDestination.UserProfile(100002)),
        )
    }

    @Test
    fun numbersReadAsTheirValues() {
        val userInfo = mapOf<String, Any?>("action" to AppEntryIntents.ACTION_OPEN_USER_PROFILE, "isu" to 100002)

        assertEquals(
            EntryRoute(AppTab.ME, overlay = AppRoutes.UserProfile(100002)),
            NotificationTapRoutes.entryRoute(userInfo),
        )
    }

    @Test
    fun aSubjectThatDoesNotDescribeAPageOpensTheRecordbook() {
        val partial = NotificationTapRoutes.userInfoOf(NotificationDestination.RecordbookSubject(subject))
            .filterKeys { it != "study_year" }
        val broken = NotificationTapRoutes.userInfoOf(NotificationDestination.RecordbookSubject(subject)) +
            ("semester" to "0")

        for (userInfo in listOf(partial, broken)) {
            assertEquals(EntryRoute(AppTab.RECORDBOOK), NotificationTapRoutes.entryRoute(userInfo), userInfo.toString())
        }
    }

    @Test
    fun aProfileWithoutAValidIsuOpensNothing() {
        for (isu in listOf<Any?>(null, "0", "-5", "abc", true)) {
            val userInfo = mapOf<String, Any?>("action" to AppEntryIntents.ACTION_OPEN_USER_PROFILE, "isu" to isu)
            assertNull(NotificationTapRoutes.entryRoute(userInfo), isu.toString())
        }
    }

    @Test
    fun actionsNoLocalNotificationCarriesOpenNothing() {
        for (action in listOf(
            EntryRouteParser.ACTION_VIEW,
            AppEntryIntents.ACTION_OPEN_QR_PASS,
            AppEntryIntents.ACTION_OPEN_TODAY,
            "dev.alllexey.itmowidgets.action.SOMETHING_NEW",
        )) {
            val userInfo = mapOf<String, Any?>("action" to action, "link" to "https://widgets.alllexey.dev/u/100002")
            assertNull(NotificationTapRoutes.entryRoute(userInfo), action)
        }
        assertNull(NotificationTapRoutes.entryRoute(emptyMap<String, Any?>()))
        assertNull(NotificationTapRoutes.entryRoute(mapOf<String, Any?>("action" to 42)))
    }

    @Test
    fun aPushEnvelopeWinsOverAnAction() {
        val userInfo = mapOf<String, Any?>(
            "data" to envelope("SPORT_AUTO_SIGN_LESSONS_PAYLOAD", """{"sportLessons":[{"id":9001}]}"""),
            "action" to AppEntryIntents.ACTION_OPEN_RECORDBOOK,
        )

        assertEquals(
            EntryRoute(AppTab.SPORT, request = TabRequest.SportLesson(9001)),
            NotificationTapRoutes.entryRoute(userInfo),
        )
    }

    /** What each destination opens; a new destination does not compile until it has a row here. */
    private fun expected(destination: NotificationDestination): EntryRoute = when (destination) {
        NotificationDestination.Sport -> EntryRoute(AppTab.SPORT)
        is NotificationDestination.UserProfile ->
            EntryRoute(AppTab.ME, overlay = AppRoutes.UserProfile(destination.isu))
        NotificationDestination.ScheduleChanges -> EntryRoute(AppTab.SCHEDULE, overlay = AppRoutes.ScheduleChanges)
        NotificationDestination.Recordbook -> EntryRoute(AppTab.RECORDBOOK)
        is NotificationDestination.RecordbookSubject ->
            EntryRoute(AppTab.RECORDBOOK, overlay = AppRoutes.RecordbookSubject(destination.args))
        NotificationDestination.BarsLogin -> EntryRoute(AppTab.RECORDBOOK, activity = ActivityRoute.BARS_LOGIN)
    }

    /** The route of Android's notification intent for [destination] (`AndroidAppNotifier`'s action and extras). */
    private fun android(destination: NotificationDestination): EntryRoute? = when (destination) {
        NotificationDestination.Sport -> EntryRouteParser.parse(AppEntryIntents.ACTION_OPEN_SPORT)
        is NotificationDestination.UserProfile ->
            EntryRouteParser.parse(AppEntryIntents.ACTION_OPEN_USER_PROFILE, isu = destination.isu)
        NotificationDestination.ScheduleChanges -> EntryRouteParser.parse(AppEntryIntents.ACTION_OPEN_SCHEDULE_CHANGES)
        NotificationDestination.Recordbook -> EntryRouteParser.parse(AppEntryIntents.ACTION_OPEN_RECORDBOOK)
        is NotificationDestination.RecordbookSubject ->
            EntryRouteParser.parse(AppEntryIntents.ACTION_OPEN_RECORDBOOK_SUBJECT, subject = destination.args)
        NotificationDestination.BarsLogin -> EntryRouteParser.parse(AppEntryIntents.ACTION_OPEN_BARS_LOGIN)
    }

    private val subject = RecordbookSubjectArgs(11, 1, 3, "2026/2027", 7, "flow", "7")
    private val subjectWithoutJournal = RecordbookSubjectArgs(11, 1, 3, "2026/2027")

    private fun envelope(type: String, payload: String) = """{"type":"$type","payload":$payload}"""
}
