package dev.alllexey.itmowidgets.core.payments.check

import dev.alllexey.itmowidgets.core.notification.AppNotification
import dev.alllexey.itmowidgets.core.notification.AppNotifier
import kotlin.time.Instant

/** Posts the check's notifications: at once, or handed to the system for a later moment where the platform can. */
interface PaymentsNotifier {
    fun show(notification: AppNotification)

    /** Posts [notification] for [at]; false where the platform cannot, and the next run after [at] posts it. */
    suspend fun showAt(notification: AppNotification, at: Instant): Boolean
}

/**
 * Android's [PaymentsNotifier]: WorkManager runs the check again within [PaymentsCadence.PERIOD], so a notice of the
 * quiet hours waits for the first run after them instead of a scheduled post.
 */
class ImmediatePaymentsNotifier(private val notifier: AppNotifier) : PaymentsNotifier {

    override fun show(notification: AppNotification) = notifier.show(notification)

    override suspend fun showAt(notification: AppNotification, at: Instant): Boolean = false
}
