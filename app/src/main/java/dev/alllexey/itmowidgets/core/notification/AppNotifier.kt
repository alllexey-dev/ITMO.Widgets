package dev.alllexey.itmowidgets.core.notification

import dev.alllexey.itmowidgets.core.text.UiText

sealed interface NotificationDestination {
    data object Sport : NotificationDestination
    data class UserProfile(val isu: Int) : NotificationDestination
    data object ScheduleChanges : NotificationDestination
}

data class AppNotification(
    val channel: String,
    val id: Int,
    val title: UiText,
    val text: UiText,
    val destination: NotificationDestination,
    /** Shown without sound or vibration, whatever the channel says. */
    val silent: Boolean = false
)

interface AppNotifier {
    fun show(notification: AppNotification)
    /** Removes one shown notification; [channel] is its tag, as in [show]. */
    fun cancel(channel: String, id: Int)
    fun clear()
}
