package dev.alllexey.itmowidgets.app

import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.ui.navigation.AppScreen

/** Pure route parsing: malformed public intents never open an arbitrary profile. */
/** [screen] opens above [rootDestination] once the root is selected. */
data class MainActivityRoute(val rootDestination: Int, val userIsu: Int? = null, val screen: AppScreen? = null)

object MainActivityIntentRouting {
    fun parse(action: String?, isu: Int? = null): MainActivityRoute? = when (action) {
        MainActivity.ACTION_OPEN_SCHEDULE -> MainActivityRoute(R.id.navigation_schedule)
        MainActivity.ACTION_OPEN_SPORT -> MainActivityRoute(R.id.navigation_sport)
        MainActivity.ACTION_OPEN_SCHEDULE_CHANGES ->
            MainActivityRoute(R.id.navigation_schedule, screen = AppScreen.SCHEDULE_CHANGES)
        MainActivity.ACTION_OPEN_USER_PROFILE -> isu?.takeIf { it > 0 }?.let { MainActivityRoute(R.id.navigation_me, it) }
        else -> null
    }
}
