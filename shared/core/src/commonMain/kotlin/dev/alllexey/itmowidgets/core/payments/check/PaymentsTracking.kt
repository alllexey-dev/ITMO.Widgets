package dev.alllexey.itmowidgets.core.payments.check

import dev.alllexey.itmowidgets.core.session.SessionTokenStore
import dev.alllexey.itmowidgets.core.work.BackgroundCheck
import dev.alllexey.itmowidgets.core.work.CheckScheduler

/** Where the check `Общежитие и оплата` runs: WorkManager on Android, the app refresh task on iOS. */
interface PaymentsScheduler : CheckScheduler

/**
 * The check `Общежитие и оплата` among the background checks: it runs whenever there is a session. Its switch gates
 * only the notifications, so the bell and the stored answers of `Нужно сделать` stay fresh with it off.
 */
class PaymentsTracking(
    private val sessionTokens: SessionTokenStore,
    private val scheduler: PaymentsScheduler,
) : BackgroundCheck {

    override suspend fun syncWork() {
        if (sessionTokens.hasRefreshToken()) scheduler.ensurePeriodic() else scheduler.cancel()
    }

    override fun stopWork() = scheduler.cancel()

    /** One run as soon as the network allows; the debug tools use it. */
    fun checkNow() = scheduler.runOnce()
}
