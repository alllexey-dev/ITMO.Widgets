package dev.alllexey.itmowidgets.core.navigation

/**
 * A request to show today's day in the own schedule.
 *
 * Set as a Fragment result on the Activity's FragmentManager, which keeps it, also in the saved state, until a
 * listener is `STARTED` and delivers it once. Only the root schedule listens: a FragmentManager holds one listener
 * per key, and another user's schedule would otherwise take it over.
 */
object ScheduleTodayRequest {
    const val KEY = "schedule_today_request"
}
