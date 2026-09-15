package dev.alllexey.itmowidgets.core.notification

import dev.alllexey.itmowidgets.core.text.UiText

sealed interface NotificationDestination {
    data object Sport : NotificationDestination
    data class UserProfile(val isu: Int) : NotificationDestination
}

data class AppNotification(
    val channel: String,
    val id: Int,
    val title: UiText,
    val text: UiText,
    val destination: NotificationDestination
)

interface AppNotifier {
    fun show(notification: AppNotification)
    fun clear()
}
