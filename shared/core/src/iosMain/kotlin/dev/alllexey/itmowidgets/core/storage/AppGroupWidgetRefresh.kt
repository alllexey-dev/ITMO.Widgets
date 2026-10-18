package dev.alllexey.itmowidgets.core.storage

/**
 * A writer that keeps an App Group widget file current. After a change only the caller knows of (a sport booking)
 * the iOS `ScheduleWidgetRefreshRequester` asks every bound one to write again; it never waits for the write.
 */
fun interface AppGroupWidgetRefresh {
    fun requestWrite()
}
