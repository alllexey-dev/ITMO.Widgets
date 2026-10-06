package dev.alllexey.itmowidgets.core.navigation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** `MainActivityIntentRoutingTest`, case for case, on the common parser. */
class EntryRouteParserTest {

    @Test
    fun routesScheduleSportAndAPositiveProfileIsuToTheirTabs() {
        assertEquals(EntryRoute(AppTab.SCHEDULE), parse(AppEntryIntents.ACTION_OPEN_SCHEDULE))
        assertEquals(EntryRoute(AppTab.SPORT), parse(AppEntryIntents.ACTION_OPEN_SPORT))
        assertEquals(
            EntryRoute(AppTab.ME, overlay = AppRoutes.UserProfile(123456)),
            parse(AppEntryIntents.ACTION_OPEN_USER_PROFILE, isu = 123456)
        )
    }

    @Test
    fun scheduleChangesOpenTheHistoryAboveTheScheduleAndTabRoutesOpenNoScreen() {
        assertEquals(
            EntryRoute(AppTab.SCHEDULE, overlay = AppRoutes.ScheduleChanges),
            parse(AppEntryIntents.ACTION_OPEN_SCHEDULE_CHANGES)
        )
        listOf(AppEntryIntents.ACTION_OPEN_SCHEDULE, AppEntryIntents.ACTION_OPEN_SPORT).forEach { action ->
            assertNull(parse(action)!!.overlay, action)
        }
    }

    @Test
    fun earlierRoutesCarryNoSubjectPageAndNoBarsSignIn() {
        listOf(
            parse(AppEntryIntents.ACTION_OPEN_SCHEDULE),
            parse(AppEntryIntents.ACTION_OPEN_SPORT),
            parse(AppEntryIntents.ACTION_OPEN_SCHEDULE_CHANGES),
            parse(AppEntryIntents.ACTION_OPEN_USER_PROFILE, isu = 123456)
        ).forEach { route ->
            assertNull(route!!.activity)
            assertNull(route.overlay as? AppRoutes.RecordbookSubject)
        }
    }

    @Test
    fun marksNotificationsOpenTheRecordbookASubjectPageOrTheBarsSignIn() {
        assertEquals(EntryRoute(AppTab.RECORDBOOK), parse(AppEntryIntents.ACTION_OPEN_RECORDBOOK))
        assertEquals(
            EntryRoute(AppTab.RECORDBOOK, overlay = AppRoutes.RecordbookSubject(SUBJECT)),
            parse(AppEntryIntents.ACTION_OPEN_RECORDBOOK_SUBJECT, subject = SUBJECT)
        )
        assertEquals(
            EntryRoute(AppTab.RECORDBOOK, activity = ActivityRoute.BARS_LOGIN),
            parse(AppEntryIntents.ACTION_OPEN_BARS_LOGIN)
        )
    }

    @Test
    fun aSubjectRouteWithoutValidArgumentsOpensOnlyTheRecordbook() {
        listOf(null, SUBJECT.copy(entryId = 0), SUBJECT.copy(studyYear = "2026")).forEach { subject ->
            assertEquals(
                EntryRoute(AppTab.RECORDBOOK),
                parse(AppEntryIntents.ACTION_OPEN_RECORDBOOK_SUBJECT, subject = subject),
                subject.toString()
            )
        }
    }

    @Test
    fun missingOrInvalidProfileArgumentsAndUnknownActionsAreIgnored() {
        listOf(null, 0, -1).forEach { isu -> assertNull(parse(AppEntryIntents.ACTION_OPEN_USER_PROFILE, isu = isu)) }
        assertNull(parse("unknown", isu = 123456))
        assertNull(parse(null))
    }

    @Test
    fun theQrPassAndTodayShortcutsOpenTheirTabsAndNameTheirShortcut() {
        assertEquals(
            EntryRoute(AppTab.HOME, overlay = AppRoutes.QrPass, shortcutId = "qr_pass"),
            parse(AppEntryIntents.ACTION_OPEN_QR_PASS)
        )
        assertEquals(
            EntryRoute(AppTab.SCHEDULE, request = TabRequest.ScheduleToday, shortcutId = "today"),
            parse(AppEntryIntents.ACTION_OPEN_TODAY)
        )
    }

    @Test
    fun anIntentReplayedFromRecentsOpensNoRoute() {
        ENTRY_ACTIONS.forEach { action ->
            assertNull(parse(action, isu = 123456, subject = SUBJECT, launchedFromHistory = true), action)
        }
    }

    @Test
    fun earlierRoutesKeepTheirReadingPlaceAndReportNoShortcut() {
        listOf(
            parse(AppEntryIntents.ACTION_OPEN_SCHEDULE),
            parse(AppEntryIntents.ACTION_OPEN_SPORT),
            parse(AppEntryIntents.ACTION_OPEN_SCHEDULE_CHANGES),
            parse(AppEntryIntents.ACTION_OPEN_USER_PROFILE, isu = 123456),
            parse(AppEntryIntents.ACTION_OPEN_RECORDBOOK),
            parse(AppEntryIntents.ACTION_OPEN_RECORDBOOK_SUBJECT, subject = SUBJECT),
            parse(AppEntryIntents.ACTION_OPEN_BARS_LOGIN)
        ).forEach { route ->
            assertNull(route!!.request)
            assertNull(route.shortcutId)
        }
    }

    @Test
    fun appLinksOpenTheProfileTheSportSignPageOrTheUnavailableDialogAtHome() {
        assertEquals(
            EntryRoute(AppTab.ME, overlay = AppRoutes.UserProfile(100001)),
            view("https://dev.widgets.alllexey.dev/u/100001")
        )
        assertEquals(
            EntryRoute(AppTab.SPORT, request = TabRequest.SportLesson(42)),
            view("https://widgets.alllexey.dev/sport/42")
        )
        assertEquals(
            EntryRoute(AppTab.SPORT, request = TabRequest.SportLesson(42, predicted = true)),
            view("https://widgets.alllexey.dev/sport/p/42")
        )
        assertEquals(
            EntryRoute(AppTab.HOME, alert = AppRoutes.LinkUnavailable),
            view("https://widgets.alllexey.dev/sport/abc")
        )
        assertNull(view("https://example.com/u/1"))
        assertNull(view(null))
    }

    @Test
    fun anAppLinkReplayedFromRecentsOpensNoRoute() {
        listOf(
            "https://widgets.alllexey.dev/u/1",
            "https://widgets.alllexey.dev/sport/1",
            "https://widgets.alllexey.dev/u/x"
        ).forEach { link ->
                assertNull(parse(EntryRouteParser.ACTION_VIEW, launchedFromHistory = true, link = link), link)
            }
    }

    @Test
    fun earlierActionsIgnoreALink() {
        assertEquals(
            EntryRoute(AppTab.SPORT),
            parse(AppEntryIntents.ACTION_OPEN_SPORT, link = "https://widgets.alllexey.dev/u/1")
        )
    }

    private fun parse(
        action: String?,
        isu: Int? = null,
        subject: RecordbookSubjectArgs? = null,
        launchedFromHistory: Boolean = false,
        link: String? = null,
    ) = EntryRouteParser.parse(action, isu, subject, launchedFromHistory, link)

    private fun view(link: String?) = parse(EntryRouteParser.ACTION_VIEW, link = link)

    private companion object {
        val SUBJECT = RecordbookSubjectArgs(11, 1, 3, "2026/2027", 7, "flow", "7")
        val ENTRY_ACTIONS = listOf(
            AppEntryIntents.ACTION_OPEN_SCHEDULE,
            AppEntryIntents.ACTION_OPEN_SPORT,
            AppEntryIntents.ACTION_OPEN_SCHEDULE_CHANGES,
            AppEntryIntents.ACTION_OPEN_USER_PROFILE,
            AppEntryIntents.ACTION_OPEN_RECORDBOOK,
            AppEntryIntents.ACTION_OPEN_RECORDBOOK_SUBJECT,
            AppEntryIntents.ACTION_OPEN_BARS_LOGIN,
            AppEntryIntents.ACTION_OPEN_QR_PASS,
            AppEntryIntents.ACTION_OPEN_TODAY
        )
    }
}
