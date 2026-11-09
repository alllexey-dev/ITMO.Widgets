package dev.alllexey.itmowidgets.core.testing

import dev.alllexey.itmowidgets.core.payments.check.PaymentsScheduler

/** Counts what the check `Общежитие и оплата` asked of its scheduler. */
class RecordingPaymentsScheduler : PaymentsScheduler {
    var periodic = 0
    var runs = 0
    var cancels = 0

    override fun ensurePeriodic() {
        periodic++
    }

    override fun runOnce() {
        runs++
    }

    override fun cancel() {
        cancels++
    }
}
