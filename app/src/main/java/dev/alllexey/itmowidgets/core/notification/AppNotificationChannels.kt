package dev.alllexey.itmowidgets.core.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.ui.withAppLocale

object AppNotificationChannels {
    const val SPORT = "sport"
    const val FRIENDS = "friends"
    const val SCHEDULE_CHANGES = "schedule_changes"
    const val MARKS = "marks"

    fun create(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        val localized = context.withAppLocale()
        manager.createNotificationChannels(listOf(
            NotificationChannel(SPORT, localized.getString(R.string.notification_channel_sport), NotificationManager.IMPORTANCE_DEFAULT),
            NotificationChannel(FRIENDS, localized.getString(R.string.notification_channel_friends), NotificationManager.IMPORTANCE_DEFAULT),
            NotificationChannel(SCHEDULE_CHANGES, localized.getString(R.string.notification_channel_schedule_changes),
                NotificationManager.IMPORTANCE_DEFAULT),
            NotificationChannel(MARKS, localized.getString(R.string.notification_channel_marks), NotificationManager.IMPORTANCE_DEFAULT)
        ))
        manager.deleteNotificationChannel("fcm_default_channel")
    }
}
