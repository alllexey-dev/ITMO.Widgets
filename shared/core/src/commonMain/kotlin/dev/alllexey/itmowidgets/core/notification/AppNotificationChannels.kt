package dev.alllexey.itmowidgets.core.notification

/** Channel ids (stable identifiers, kept from 2.2); the app creates the channels in `AndroidNotificationChannels.kt`. */
object AppNotificationChannels {
    const val SPORT = "sport"
    const val FRIENDS = "friends"
    const val SCHEDULE_CHANGES = "schedule_changes"
    const val MARKS = "marks"
    const val CONNECTIONS = "connections"
    /** The check `Общежитие и оплата`: Место предоставлено and the Платежи reminders. */
    const val DORMITORY_PAYMENTS = "dormitory_payments"

    /** The categories Backend pushes; each has the app's own switch (`PushCategoryPreferences`). */
    val PUSHES = listOf(SPORT, FRIENDS, CONNECTIONS)

    val ALL = listOf(SPORT, FRIENDS, SCHEDULE_CHANGES, MARKS, CONNECTIONS, DORMITORY_PAYMENTS)
}
