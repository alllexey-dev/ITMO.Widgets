package dev.alllexey.itmowidgets.core.testing

import dev.alllexey.itmowidgets.core.notification.AppNotification
import dev.alllexey.itmowidgets.core.notification.AppNotifier

class RecordingAppNotifier : AppNotifier {
    val shown = mutableListOf<AppNotification>()
    val cancelled = mutableListOf<Pair<String, Int>>()
    var cleared = 0

    override fun show(notification: AppNotification) {
        shown += notification
    }

    override fun cancel(channel: String, id: Int) {
        cancelled += channel to id
    }

    override fun clear() {
        cleared++
    }
}
