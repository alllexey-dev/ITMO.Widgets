package dev.alllexey.itmowidgets.core.testing

import dev.alllexey.itmowidgets.core.notification.AppNotification
import dev.alllexey.itmowidgets.core.payments.check.PaymentsNotifier
import kotlin.time.Instant

/**
 * The check's notifier of one test: [shown] at once, [scheduled] for a later moment. With [canSchedule] off it is
 * Android's, which cannot post ahead.
 */
class RecordingPaymentsNotifier(private val canSchedule: Boolean = false) : PaymentsNotifier {
    val shown = mutableListOf<AppNotification>()
    val scheduled = mutableListOf<Pair<AppNotification, Instant>>()

    override fun show(notification: AppNotification) {
        shown += notification
    }

    override suspend fun showAt(notification: AppNotification, at: Instant): Boolean {
        if (canSchedule) scheduled += notification to at
        return canSchedule
    }
}
