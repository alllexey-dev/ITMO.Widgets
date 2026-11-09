package dev.alllexey.itmowidgets.core.payments.check

import dev.alllexey.itmowidgets.core.work.CheckScheduler
import dev.alllexey.itmowidgets.core.work.RefreshStepKeys
import dev.alllexey.itmowidgets.core.work.RefreshStepLog

/**
 * The [PaymentsScheduler] on iOS: the app's one refresh task runs the check as its [RefreshStepKeys.PAYMENTS] step.
 * [runOnce] (the debug tools) makes the step due at once and asks for a run; [cancel] does nothing, since the check
 * skips itself without a session.
 */
class RefreshTaskPaymentsScheduler(
    private val refresh: CheckScheduler,
    private val stepLog: RefreshStepLog,
) : PaymentsScheduler {

    override fun ensurePeriodic() = refresh.ensurePeriodic()

    override fun runOnce() {
        stepLog.forget(RefreshStepKeys.PAYMENTS)
        refresh.runOnce()
    }

    override fun cancel() = Unit
}
