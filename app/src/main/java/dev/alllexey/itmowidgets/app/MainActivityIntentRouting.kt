package dev.alllexey.itmowidgets.app

import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.navigation.AppEntryIntents
import dev.alllexey.itmowidgets.core.navigation.AppLink
import dev.alllexey.itmowidgets.core.navigation.AppLinks
import dev.alllexey.itmowidgets.core.navigation.RecordbookSubjectArgs
import dev.alllexey.itmowidgets.core.ui.navigation.AppScreen

/**
 * Pure route parsing: malformed public intents never open an arbitrary profile.
 *
 * [screen] opens above [rootDestination] once the root is selected, with [subject] as its arguments when it is the
 * subject page; [barsLogin] then starts the BARS sign-in above everything. [today] opens the schedule on today's day.
 * [sportLessonId] asks the sign page to show that lesson, the prototype of a predicted one when
 * [sportLessonPredicted]; [linkUnavailable] explains a damaged link above home.
 */
data class MainActivityRoute(
    val rootDestination: Int,
    val userIsu: Int? = null,
    val screen: AppScreen? = null,
    val subject: RecordbookSubjectArgs? = null,
    val barsLogin: Boolean = false,
    val today: Boolean = false,
    val sportLessonId: Long? = null,
    val sportLessonPredicted: Boolean = false,
    val linkUnavailable: Boolean = false
)

/** The static shortcut this route was started from, reported to the launcher once the route runs. */
fun MainActivityRoute.shortcutId(): String? = when {
    screen == AppScreen.QR_PASS -> AppShortcuts.QR_PASS
    today -> AppShortcuts.TODAY
    else -> null
}

object MainActivityIntentRouting {
    /**
     * An intent replayed from Recents ([launchedFromHistory]) already ran its route and opens the app as it was.
     * A `VIEW` intent carries an app [link]; links that are not the app's open nothing.
     */
    fun parse(
        action: String?,
        isu: Int? = null,
        subject: RecordbookSubjectArgs? = null,
        launchedFromHistory: Boolean = false,
        link: String? = null
    ): MainActivityRoute? = if (launchedFromHistory) null else when (action) {
        ACTION_VIEW -> linkRoute(AppLinks.parse(link))
        AppEntryIntents.ACTION_OPEN_SCHEDULE -> MainActivityRoute(R.id.navigation_schedule)
        AppEntryIntents.ACTION_OPEN_SPORT -> MainActivityRoute(R.id.navigation_sport)
        AppEntryIntents.ACTION_OPEN_SCHEDULE_CHANGES ->
            MainActivityRoute(R.id.navigation_schedule, screen = AppScreen.SCHEDULE_CHANGES)
        AppEntryIntents.ACTION_OPEN_USER_PROFILE -> isu?.takeIf { it > 0 }?.let { MainActivityRoute(R.id.navigation_me, it) }
        AppEntryIntents.ACTION_OPEN_RECORDBOOK -> MainActivityRoute(R.id.navigation_recordbook)
        // Arguments that do not describe a page still open the recordbook, where the subject is one tap away.
        AppEntryIntents.ACTION_OPEN_RECORDBOOK_SUBJECT -> subject?.validOrNull()
            ?.let { MainActivityRoute(R.id.navigation_recordbook, screen = AppScreen.RECORDBOOK_SUBJECT, subject = it) }
            ?: MainActivityRoute(R.id.navigation_recordbook)
        AppEntryIntents.ACTION_OPEN_BARS_LOGIN -> MainActivityRoute(R.id.navigation_recordbook, barsLogin = true)
        AppEntryIntents.ACTION_OPEN_QR_PASS -> MainActivityRoute(R.id.navigation_home, screen = AppScreen.QR_PASS)
        AppEntryIntents.ACTION_OPEN_TODAY -> MainActivityRoute(R.id.navigation_schedule, today = true)
        else -> null
    }

    private fun linkRoute(link: AppLink?): MainActivityRoute? = when (link) {
        is AppLink.Profile -> MainActivityRoute(R.id.navigation_me, userIsu = link.isu)
        is AppLink.SportLesson -> MainActivityRoute(R.id.navigation_sport, sportLessonId = link.lessonId)
        is AppLink.PredictedSportLesson -> MainActivityRoute(
            R.id.navigation_sport,
            sportLessonId = link.prototypeLessonId,
            sportLessonPredicted = true
        )
        AppLink.Malformed -> MainActivityRoute(R.id.navigation_home, linkUnavailable = true)
        null -> null
    }

    /** `Intent.ACTION_VIEW`, spelled out so the parser stays a plain JVM function. */
    private const val ACTION_VIEW = "android.intent.action.VIEW"
}
