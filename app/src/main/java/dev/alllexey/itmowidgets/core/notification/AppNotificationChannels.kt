package dev.alllexey.itmowidgets.core.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import dev.alllexey.itmowidgets.R

object AppNotificationChannels {
    const val SPORT = "sport"
    const val FRIENDS = "friends"

    fun create(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannels(listOf(
            NotificationChannel(SPORT, context.getString(R.string.notification_channel_sport), NotificationManager.IMPORTANCE_DEFAULT),
            NotificationChannel(FRIENDS, context.getString(R.string.notification_channel_friends), NotificationManager.IMPORTANCE_DEFAULT)
        ))
        manager.deleteNotificationChannel("fcm_default_channel")
    }
}
