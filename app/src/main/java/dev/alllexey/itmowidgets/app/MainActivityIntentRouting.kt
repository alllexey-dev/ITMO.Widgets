package dev.alllexey.itmowidgets.app

import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.navigation.RecordbookSubjectArgs
import dev.alllexey.itmowidgets.core.ui.navigation.AppScreen

/** Pure route parsing: malformed public intents never open an arbitrary profile. */
/**
 * [screen] opens above [rootDestination] once the root is selected, with [subject] as its arguments when it is the
 * subject page; [barsLogin] then starts the BARS sign-in above everything.
 */
data class MainActivityRoute(
    val rootDestination: Int,
    val userIsu: Int? = null,
    val screen: AppScreen? = null,
    val subject: RecordbookSubjectArgs? = null,
    val barsLogin: Boolean = false
)

object MainActivityIntentRouting {
    fun parse(action: String?, isu: Int? = null, subject: RecordbookSubjectArgs? = null): MainActivityRoute? = when (action) {
        MainActivity.ACTION_OPEN_SCHEDULE -> MainActivityRoute(R.id.navigation_schedule)
        MainActivity.ACTION_OPEN_SPORT -> MainActivityRoute(R.id.navigation_sport)
        MainActivity.ACTION_OPEN_SCHEDULE_CHANGES ->
            MainActivityRoute(R.id.navigation_schedule, screen = AppScreen.SCHEDULE_CHANGES)
        MainActivity.ACTION_OPEN_USER_PROFILE -> isu?.takeIf { it > 0 }?.let { MainActivityRoute(R.id.navigation_me, it) }
        MainActivity.ACTION_OPEN_RECORDBOOK -> MainActivityRoute(R.id.navigation_recordbook)
        // Arguments that do not describe a page still open the recordbook, where the subject is one tap away.
        MainActivity.ACTION_OPEN_RECORDBOOK_SUBJECT -> subject?.validOrNull()
            ?.let { MainActivityRoute(R.id.navigation_recordbook, screen = AppScreen.RECORDBOOK_SUBJECT, subject = it) }
            ?: MainActivityRoute(R.id.navigation_recordbook)
        MainActivity.ACTION_OPEN_BARS_LOGIN -> MainActivityRoute(R.id.navigation_recordbook, barsLogin = true)
        else -> null
    }
}
