package dev.alllexey.itmowidgets.app

import dev.alllexey.itmowidgets.R

/** Pure route parsing: malformed public intents never open an arbitrary profile. */
data class MainActivityRoute(val rootDestination: Int, val userIsu: Int? = null)

object MainActivityIntentRouting {
    fun parse(action: String?, isu: Int? = null): MainActivityRoute? = when (action) {
        MainActivity.ACTION_OPEN_SCHEDULE -> MainActivityRoute(R.id.navigation_schedule)
        MainActivity.ACTION_OPEN_SPORT -> MainActivityRoute(R.id.navigation_sport)
        MainActivity.ACTION_OPEN_USER_PROFILE -> isu?.takeIf { it > 0 }?.let { MainActivityRoute(R.id.navigation_me, it) }
        else -> null
    }
}
