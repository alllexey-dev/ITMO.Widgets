package dev.alllexey.itmowidgets.core.payments.check

import dev.alllexey.itmowidgets.core.notification.AppNotification
import dev.alllexey.itmowidgets.core.notification.IosAppNotifier
import kotlin.time.Instant

/**
 * The iOS [PaymentsNotifier] over [IosAppNotifier]: iOS may not wake the app for many hours, so a notice of the quiet
 * hours goes to the system for 06:00 at once (a calendar trigger) and counts as posted, as the mark check does.
 */
class IosPaymentsNotifier(private val notifier: IosAppNotifier) : PaymentsNotifier {

    override fun show(notification: AppNotification) = notifier.show(notification)

    override suspend fun showAt(notification: AppNotification, at: Instant): Boolean {
        notifier.post(notification, deliverAt = at)
        return true
    }
}
