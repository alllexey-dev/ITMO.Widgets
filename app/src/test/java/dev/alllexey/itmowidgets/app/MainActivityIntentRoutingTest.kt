package dev.alllexey.itmowidgets.app

import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.navigation.RecordbookSubjectArgs
import dev.alllexey.itmowidgets.core.ui.navigation.AppScreen
import org.junit.Assert.*
import org.junit.Test

class MainActivityIntentRoutingTest {
    private companion object {
        val SUBJECT = RecordbookSubjectArgs(11, 1, 3, "2026/2027", 7, "flow", "7")
    }

    @Test fun `routes schedule sport and a positive profile ISU to their stable roots`() {
        assertEquals(MainActivityRoute(R.id.navigation_schedule), MainActivityIntentRouting.parse(MainActivity.ACTION_OPEN_SCHEDULE))
        assertEquals(MainActivityRoute(R.id.navigation_sport), MainActivityIntentRouting.parse(MainActivity.ACTION_OPEN_SPORT))
        assertEquals(MainActivityRoute(R.id.navigation_me, 123456), MainActivityIntentRouting.parse(MainActivity.ACTION_OPEN_USER_PROFILE, 123456))
    }

    @Test fun `schedule changes open the history above the schedule and other routes open no screen`() {
        assertEquals(
            MainActivityRoute(R.id.navigation_schedule, screen = AppScreen.SCHEDULE_CHANGES),
            MainActivityIntentRouting.parse(MainActivity.ACTION_OPEN_SCHEDULE_CHANGES)
        )
        listOf(MainActivity.ACTION_OPEN_SCHEDULE, MainActivity.ACTION_OPEN_SPORT).forEach { action ->
            assertNull(MainActivityIntentRouting.parse(action)!!.screen)
        }
        assertNull(MainActivityIntentRouting.parse(MainActivity.ACTION_OPEN_USER_PROFILE, 123456)!!.screen)
    }

    @Test fun `earlier routes carry no subject page and no BARS sign-in`() {
        listOf(
            MainActivityIntentRouting.parse(MainActivity.ACTION_OPEN_SCHEDULE),
            MainActivityIntentRouting.parse(MainActivity.ACTION_OPEN_SPORT),
            MainActivityIntentRouting.parse(MainActivity.ACTION_OPEN_SCHEDULE_CHANGES),
            MainActivityIntentRouting.parse(MainActivity.ACTION_OPEN_USER_PROFILE, 123456)
        ).forEach { route ->
            assertNull(route!!.subject)
            assertFalse(route.barsLogin)
        }
    }

    @Test fun `marks notifications open the recordbook, a subject page or the BARS sign-in`() {
        assertEquals(MainActivityRoute(R.id.navigation_recordbook), MainActivityIntentRouting.parse(MainActivity.ACTION_OPEN_RECORDBOOK))
        assertEquals(
            MainActivityRoute(R.id.navigation_recordbook, screen = AppScreen.RECORDBOOK_SUBJECT, subject = SUBJECT),
            MainActivityIntentRouting.parse(MainActivity.ACTION_OPEN_RECORDBOOK_SUBJECT, subject = SUBJECT)
        )
        assertEquals(
            MainActivityRoute(R.id.navigation_recordbook, barsLogin = true),
            MainActivityIntentRouting.parse(MainActivity.ACTION_OPEN_BARS_LOGIN)
        )
    }

    @Test fun `a subject route without valid arguments opens only the recordbook`() {
        listOf(null, SUBJECT.copy(entryId = 0), SUBJECT.copy(studyYear = "2026")).forEach { subject ->
            assertEquals(
                MainActivityRoute(R.id.navigation_recordbook),
                MainActivityIntentRouting.parse(MainActivity.ACTION_OPEN_RECORDBOOK_SUBJECT, subject = subject)
            )
        }
    }

    @Test fun `missing invalid profile arguments and unknown actions are ignored`() {
        for (isu in listOf(null, 0, -1)) assertNull(MainActivityIntentRouting.parse(MainActivity.ACTION_OPEN_USER_PROFILE, isu))
        assertNull(MainActivityIntentRouting.parse("unknown", 123456))
        assertNull(MainActivityIntentRouting.parse(null))
    }
}
