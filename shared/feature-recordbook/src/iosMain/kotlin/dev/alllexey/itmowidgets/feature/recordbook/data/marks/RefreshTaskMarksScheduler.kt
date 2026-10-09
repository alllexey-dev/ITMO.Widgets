package dev.alllexey.itmowidgets.feature.recordbook.data.marks

import dev.alllexey.itmowidgets.core.work.CheckScheduler
import dev.alllexey.itmowidgets.core.work.RefreshStepKeys
import dev.alllexey.itmowidgets.core.work.RefreshStepLog
import dev.alllexey.itmowidgets.feature.recordbook.domain.marks.MarksScheduler

/**
 * The mark check's [MarksScheduler] on iOS (IO-09d3): the app's one refresh task (IO-14) runs the check as its
 * [RefreshStepKeys.MARKS] step within Android's three hours. [runOnce] (a switch turned on, the first BARS answer)
 * makes the step due at once and asks the running app for a run, as Android's one-off `marks-check-now`; [cancel]
 * does nothing, since the check skips itself when every source is off or the user is signed out.
 */
class RefreshTaskMarksScheduler(
    private val refresh: CheckScheduler,
    private val stepLog: RefreshStepLog,
) : MarksScheduler {

    override fun ensurePeriodic() = refresh.ensurePeriodic()

    override fun runOnce() {
        stepLog.forget(RefreshStepKeys.MARKS)
        refresh.runOnce()
    }

    override fun cancel() = Unit
}
