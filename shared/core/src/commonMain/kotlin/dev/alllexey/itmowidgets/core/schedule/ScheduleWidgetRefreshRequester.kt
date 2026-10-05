package dev.alllexey.itmowidgets.core.schedule

/** Enqueues a fresh snapshot for both schedule widgets after an actual schedule change. */
fun interface ScheduleWidgetRefreshRequester {
    fun refreshScheduleWidgets()
}
