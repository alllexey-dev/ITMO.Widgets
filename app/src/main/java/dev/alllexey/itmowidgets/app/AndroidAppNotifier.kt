package dev.alllexey.itmowidgets.app

import android.Manifest
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.navigation.UserScreenArgs
import dev.alllexey.itmowidgets.core.notification.AppNotification
import dev.alllexey.itmowidgets.core.notification.AppNotificationChannels
import dev.alllexey.itmowidgets.core.notification.AppNotifier
import dev.alllexey.itmowidgets.core.notification.NotificationDestination
import dev.alllexey.itmowidgets.core.ui.resolve
import javax.inject.Inject

class AndroidAppNotifier @Inject constructor(
    @param:ApplicationContext private val context: Context
) : AppNotifier {
    override fun show(notification: AppNotification) {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) return
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return
        AppNotificationChannels.create(context)
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            data = Uri.parse("itmowidgets-notification://${notification.channel}/${notification.id}")
            when (val target = notification.destination) {
                NotificationDestination.Sport -> action = MainActivity.ACTION_OPEN_SPORT
                is NotificationDestination.UserProfile -> {
                    action = MainActivity.ACTION_OPEN_USER_PROFILE
                    putExtra(UserScreenArgs.ISU, target.isu)
                }
            }
        }
        val pendingIntent = PendingIntent.getActivity(context, notification.id, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val built = NotificationCompat.Builder(context, notification.channel)
            .setSmallIcon(R.drawable.ic_stat_notifications)
            .setContentTitle(notification.title.resolve(context))
            .setContentText(notification.text.resolve(context))
            .setStyle(NotificationCompat.BigTextStyle().bigText(notification.text.resolve(context)))
            .setContentIntent(pendingIntent)
            .apply {
                if (notification.channel == AppNotificationChannels.FRIENDS) {
                    setGroup(AppNotificationChannels.FRIENDS)
                    setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_CHILDREN)
                }
            }
            .setAutoCancel(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .build()
        try {
            manager.notify(notification.channel, notification.id, built)
            if (notification.channel == AppNotificationChannels.FRIENDS) {
                // notify() is asynchronous: activeNotifications may still omit the previous child.
                // Always publish a summary, rather than racing a count and leaving an invisible group.
                manager.notify("friends-summary", 0,
                    NotificationCompat.Builder(context, AppNotificationChannels.FRIENDS)
                        .setSmallIcon(R.drawable.ic_stat_notifications)
                        .setContentTitle(context.getString(R.string.notification_channel_friends))
                        .setContentText(notification.text.resolve(context))
                        .setGroup(AppNotificationChannels.FRIENDS)
                        .setGroupSummary(true)
                        .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_CHILDREN)
                        .setContentIntent(pendingIntent)
                        .setAutoCancel(true)
                        .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                        .build())
            }
        } catch (_: SecurityException) {
            // Notification permission can be revoked between the check and notify.
        }
    }

    override fun clear() {
        context.getSystemService(NotificationManager::class.java).cancelAll()
    }
}
