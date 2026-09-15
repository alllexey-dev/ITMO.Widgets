package dev.alllexey.itmowidgets.app

import dev.alllexey.itmowidgets.R
import org.junit.Assert.*
import org.junit.Test

class MainActivityIntentRoutingTest {
    @Test fun `routes schedule sport and a positive profile ISU to their stable roots`() {
        assertEquals(MainActivityRoute(R.id.navigation_schedule), MainActivityIntentRouting.parse(MainActivity.ACTION_OPEN_SCHEDULE))
        assertEquals(MainActivityRoute(R.id.navigation_sport), MainActivityIntentRouting.parse(MainActivity.ACTION_OPEN_SPORT))
        assertEquals(MainActivityRoute(R.id.navigation_me, 123456), MainActivityIntentRouting.parse(MainActivity.ACTION_OPEN_USER_PROFILE, 123456))
    }

    @Test fun `missing invalid profile arguments and unknown actions are ignored`() {
        for (isu in listOf(null, 0, -1)) assertNull(MainActivityIntentRouting.parse(MainActivity.ACTION_OPEN_USER_PROFILE, isu))
        assertNull(MainActivityIntentRouting.parse("unknown", 123456))
        assertNull(MainActivityIntentRouting.parse(null))
    }
}
