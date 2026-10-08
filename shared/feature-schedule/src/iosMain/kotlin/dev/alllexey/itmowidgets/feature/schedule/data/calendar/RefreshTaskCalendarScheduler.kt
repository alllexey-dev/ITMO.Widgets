package dev.alllexey.itmowidgets.feature.schedule.data.calendar

import dev.alllexey.itmowidgets.core.work.CheckScheduler
import dev.alllexey.itmowidgets.core.work.RefreshStepKeys
import dev.alllexey.itmowidgets.core.work.RefreshStepLog
import dev.alllexey.itmowidgets.feature.schedule.domain.calendar.CalendarSyncScheduler

/**
 * The calendar sync's [CalendarSyncScheduler] on iOS: the app's one refresh task (IO-14) runs the sync as its
 * [RefreshStepKeys.CALENDAR_SYNC] step within Android's two hours. [runOnce] (turning on, a pull on the own schedule)
 * makes the step due at once and asks the running app for a run, as Android's one-off `calendar-sync-now`; [cancel]
 * does nothing, since the step skips itself when there is nothing to sync.
 */
class RefreshTaskCalendarScheduler(
    private val refresh: CheckScheduler,
    private val stepLog: RefreshStepLog,
) : CalendarSyncScheduler {

    override fun ensurePeriodic() = refresh.ensurePeriodic()

    override fun runOnce() {
        stepLog.forget(RefreshStepKeys.CALENDAR_SYNC)
        refresh.runOnce()
    }

    override fun cancel() = Unit
}
