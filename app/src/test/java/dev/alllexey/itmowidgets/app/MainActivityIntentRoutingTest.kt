package dev.alllexey.itmowidgets.app

import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.navigation.AppEntryIntents
import dev.alllexey.itmowidgets.core.navigation.RecordbookSubjectArgs
import dev.alllexey.itmowidgets.core.ui.navigation.AppScreen
import org.junit.Assert.*
import org.junit.Test

class MainActivityIntentRoutingTest {
    private companion object {
        val SUBJECT = RecordbookSubjectArgs(11, 1, 3, "2026/2027", 7, "flow", "7")
        const val VIEW = "android.intent.action.VIEW"
    }

    @Test fun `routes schedule sport and a positive profile ISU to their stable roots`() {
        assertEquals(MainActivityRoute(R.id.navigation_schedule), MainActivityIntentRouting.parse(AppEntryIntents.ACTION_OPEN_SCHEDULE))
        assertEquals(MainActivityRoute(R.id.navigation_sport), MainActivityIntentRouting.parse(AppEntryIntents.ACTION_OPEN_SPORT))
        assertEquals(MainActivityRoute(R.id.navigation_me, 123456), MainActivityIntentRouting.parse(AppEntryIntents.ACTION_OPEN_USER_PROFILE, 123456))
    }

    @Test fun `schedule changes open the history above the schedule and other routes open no screen`() {
        assertEquals(
            MainActivityRoute(R.id.navigation_schedule, screen = AppScreen.SCHEDULE_CHANGES),
            MainActivityIntentRouting.parse(AppEntryIntents.ACTION_OPEN_SCHEDULE_CHANGES)
        )
        listOf(AppEntryIntents.ACTION_OPEN_SCHEDULE, AppEntryIntents.ACTION_OPEN_SPORT).forEach { action ->
            assertNull(MainActivityIntentRouting.parse(action)!!.screen)
        }
        assertNull(MainActivityIntentRouting.parse(AppEntryIntents.ACTION_OPEN_USER_PROFILE, 123456)!!.screen)
    }

    @Test fun `earlier routes carry no subject page and no BARS sign-in`() {
        listOf(
            MainActivityIntentRouting.parse(AppEntryIntents.ACTION_OPEN_SCHEDULE),
            MainActivityIntentRouting.parse(AppEntryIntents.ACTION_OPEN_SPORT),
            MainActivityIntentRouting.parse(AppEntryIntents.ACTION_OPEN_SCHEDULE_CHANGES),
            MainActivityIntentRouting.parse(AppEntryIntents.ACTION_OPEN_USER_PROFILE, 123456)
        ).forEach { route ->
            assertNull(route!!.subject)
            assertFalse(route.barsLogin)
        }
    }

    @Test fun `marks notifications open the recordbook, a subject page or the BARS sign-in`() {
        assertEquals(MainActivityRoute(R.id.navigation_recordbook), MainActivityIntentRouting.parse(AppEntryIntents.ACTION_OPEN_RECORDBOOK))
        assertEquals(
            MainActivityRoute(R.id.navigation_recordbook, screen = AppScreen.RECORDBOOK_SUBJECT, subject = SUBJECT),
            MainActivityIntentRouting.parse(AppEntryIntents.ACTION_OPEN_RECORDBOOK_SUBJECT, subject = SUBJECT)
        )
        assertEquals(
            MainActivityRoute(R.id.navigation_recordbook, barsLogin = true),
            MainActivityIntentRouting.parse(AppEntryIntents.ACTION_OPEN_BARS_LOGIN)
        )
    }

    @Test fun `a subject route without valid arguments opens only the recordbook`() {
        listOf(null, SUBJECT.copy(entryId = 0), SUBJECT.copy(studyYear = "2026")).forEach { subject ->
            assertEquals(
                MainActivityRoute(R.id.navigation_recordbook),
                MainActivityIntentRouting.parse(AppEntryIntents.ACTION_OPEN_RECORDBOOK_SUBJECT, subject = subject)
            )
        }
    }

    @Test fun `missing invalid profile arguments and unknown actions are ignored`() {
        for (isu in listOf(null, 0, -1)) assertNull(MainActivityIntentRouting.parse(AppEntryIntents.ACTION_OPEN_USER_PROFILE, isu))
        assertNull(MainActivityIntentRouting.parse("unknown", 123456))
        assertNull(MainActivityIntentRouting.parse(null))
    }

    @Test fun `the QR pass and today shortcuts open their roots and name their shortcut`() {
        val qr = MainActivityIntentRouting.parse(AppEntryIntents.ACTION_OPEN_QR_PASS)
        assertEquals(MainActivityRoute(R.id.navigation_home, screen = AppScreen.QR_PASS), qr)
        assertEquals("qr_pass", qr!!.shortcutId())
        val today = MainActivityIntentRouting.parse(AppEntryIntents.ACTION_OPEN_TODAY)
        assertEquals(MainActivityRoute(R.id.navigation_schedule, today = true), today)
        assertEquals("today", today!!.shortcutId())
    }

    @Test fun `an intent replayed from Recents opens no route`() {
        listOf(
            AppEntryIntents.ACTION_OPEN_SCHEDULE,
            AppEntryIntents.ACTION_OPEN_SPORT,
            AppEntryIntents.ACTION_OPEN_SCHEDULE_CHANGES,
            AppEntryIntents.ACTION_OPEN_USER_PROFILE,
            AppEntryIntents.ACTION_OPEN_RECORDBOOK,
            AppEntryIntents.ACTION_OPEN_RECORDBOOK_SUBJECT,
            AppEntryIntents.ACTION_OPEN_BARS_LOGIN,
            AppEntryIntents.ACTION_OPEN_QR_PASS,
            AppEntryIntents.ACTION_OPEN_TODAY
        ).forEach { action ->
            assertNull(action, MainActivityIntentRouting.parse(action, 123456, SUBJECT, launchedFromHistory = true))
        }
    }

    @Test fun `earlier routes keep their reading place and report no shortcut`() {
        listOf(
            MainActivityIntentRouting.parse(AppEntryIntents.ACTION_OPEN_SCHEDULE),
            MainActivityIntentRouting.parse(AppEntryIntents.ACTION_OPEN_SPORT),
            MainActivityIntentRouting.parse(AppEntryIntents.ACTION_OPEN_SCHEDULE_CHANGES),
            MainActivityIntentRouting.parse(AppEntryIntents.ACTION_OPEN_USER_PROFILE, 123456),
            MainActivityIntentRouting.parse(AppEntryIntents.ACTION_OPEN_RECORDBOOK),
            MainActivityIntentRouting.parse(AppEntryIntents.ACTION_OPEN_RECORDBOOK_SUBJECT, subject = SUBJECT),
            MainActivityIntentRouting.parse(AppEntryIntents.ACTION_OPEN_BARS_LOGIN)
        ).forEach { route ->
            assertFalse(route!!.today)
            assertNull(route.shortcutId())
        }
    }

    @Test fun `app links open the profile, the sport sign page or the unavailable dialog at home`() {
        fun view(link: String?) = MainActivityIntentRouting.parse(VIEW, link = link)
        assertEquals(MainActivityRoute(R.id.navigation_me, userIsu = 100001), view("https://dev.widgets.alllexey.dev/u/100001"))
        assertEquals(
            MainActivityRoute(R.id.navigation_sport, sportLessonId = 42),
            view("https://widgets.alllexey.dev/sport/42")
        )
        assertEquals(
            MainActivityRoute(R.id.navigation_sport, sportLessonId = 42, sportLessonPredicted = true),
            view("https://widgets.alllexey.dev/sport/p/42")
        )
        assertEquals(MainActivityRoute(R.id.navigation_home, linkUnavailable = true), view("https://widgets.alllexey.dev/sport/abc"))
        assertNull(view("https://example.com/u/1"))
        assertNull(view(null))
    }

    @Test fun `an app link replayed from Recents opens no route`() {
        listOf("https://widgets.alllexey.dev/u/1", "https://widgets.alllexey.dev/sport/1", "https://widgets.alllexey.dev/u/x")
            .forEach { link -> assertNull(link, MainActivityIntentRouting.parse(VIEW, launchedFromHistory = true, link = link)) }
    }

    @Test fun `earlier actions ignore a link`() {
        assertEquals(
            MainActivityRoute(R.id.navigation_sport),
            MainActivityIntentRouting.parse(AppEntryIntents.ACTION_OPEN_SPORT, link = "https://widgets.alllexey.dev/u/1")
        )
    }
}
