package dev.alllexey.itmowidgets.core.notification

import dev.alllexey.itmowidgets.core.navigation.RecordbookSubjectArgs
import dev.alllexey.itmowidgets.core.text.UiText

sealed interface NotificationDestination {
    data object Sport : NotificationDestination
    data class UserProfile(val isu: Int) : NotificationDestination
    data object ScheduleChanges : NotificationDestination
    data object Recordbook : NotificationDestination
    data class RecordbookSubject(val args: RecordbookSubjectArgs) : NotificationDestination
    /** The recordbook with the BARS sign-in above it. */
    data object BarsLogin : NotificationDestination
}

data class AppNotification(
    val channel: String,
    val id: Int,
    val title: UiText,
    val text: UiText,
    val destination: NotificationDestination,
    /** Shown without sound or vibration, whatever the channel says. */
    val silent: Boolean = false,
    /** The lock screen's version: this title only. Without it the lock screen hides the notification's content. */
    val publicTitle: UiText? = null
)

interface AppNotifier {
    fun show(notification: AppNotification)
    /** Removes one shown notification; [channel] is its tag, as in [show]. */
    fun cancel(channel: String, id: Int)
    fun clear()
}
