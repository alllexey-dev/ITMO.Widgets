package dev.alllexey.itmowidgets.core.navigation

import kotlinx.serialization.Serializable

/**
 * What a widget, notification, tile, shortcut or app link intent opens, once the session is signed in and the
 * first-run flow is passed: [tab] is selected first, then [overlay] opens above it, [request] waits for the tab's
 * root, the host starts [activity], [alert] opens on top, and the launcher hears [shortcutId].
 */
@Serializable
data class EntryRoute(
    val tab: AppTab,
    val overlay: AppRoute? = null,
    val request: TabRequest? = null,
    val activity: ActivityRoute? = null,
    val alert: AppRoute? = null,
    val shortcutId: String? = null,
)

/**
 * A one-shot request to a tab's root, kept with the back stack (also across process death) until that root consumes
 * it once. Not a Nav3 result: the in-memory result bus does not survive recreation.
 */
@Serializable
sealed interface TabRequest {
    val tab: AppTab

    /** Shows today's day in the own schedule. */
    @Serializable
    data object ScheduleToday : TabRequest {
        override val tab get() = AppTab.SCHEDULE
    }

    /** Shows one lesson on the sport sign page; with [predicted], [lessonId] is the prototype a prediction repeats. */
    @Serializable
    data class SportLesson(val lessonId: Long, val predicted: Boolean = false) : TabRequest {
        override val tab get() = AppTab.SPORT
    }
}

/** An Activity the host starts above everything; Activities stay Activities and are never back-stack keys. */
@Serializable
enum class ActivityRoute { BARS_LOGIN }

/** Ids of the static shortcuts in `res/xml/shortcuts.xml`, reported once their route runs. */
object EntryShortcuts {
    const val QR_PASS = "qr_pass"
    const val TODAY = "today"
}
