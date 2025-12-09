package dev.alllexey.itmowidgets.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dev.alllexey.itmowidgets.ItmoWidgetsApp
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.model.QueueEntryStatus
import dev.alllexey.itmowidgets.core.model.fcm.FcmJsonWrapper
import dev.alllexey.itmowidgets.core.model.fcm.impl.SportAutoSignLessonsPayload
import dev.alllexey.itmowidgets.core.model.fcm.impl.SportFreeSignLessonsPayload
import dev.alllexey.itmowidgets.core.model.fcm.impl.SportNewLessonsPayload
import dev.alllexey.itmowidgets.ui.main.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class MyFirebaseMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        Log.d(TAG, "Refreshed token: $token")
        sendTokenToServer(token)
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        val appContainer = (applicationContext as ItmoWidgetsApp).appContainer
        Log.d(TAG, "From: ${remoteMessage.from}")

        remoteMessage.notification?.let {
            Log.d(TAG, "Message Notification Body: ${it.body}")
            sendNotification(it.title, it.body)
        }

        // todo: remove later (test only)
        remoteMessage.data["data"]?.let { jsonPayload ->
            val gson = appContainer.gson
            try {
                val wrapper = gson.fromJson(jsonPayload, FcmJsonWrapper::class.java)
                when (wrapper.type) {
                    SportNewLessonsPayload.TYPE -> {
                        val data = gson.fromJson(wrapper.payload, SportNewLessonsPayload::class.java)
                        sendNotification("SPORT NOTIF", "${data.sportLessonIds.size} new lessons")
                    }
                    SportFreeSignLessonsPayload.TYPE -> {
                        val data = gson.fromJson(wrapper.payload, SportFreeSignLessonsPayload::class.java)
                        CoroutineScope(Dispatchers.IO).launch {
                            val freeSignEntries = appContainer.itmoWidgets.api().mySportFreeSignEntries()
                            data.sportLessonIds.forEach {
                                try {
                                    appContainer.myItmo.api().signInLessons(listOf(it)).execute().body()!!.result
                                    freeSignEntries.data?.firstOrNull { e -> e.status == QueueEntryStatus.NOTIFIED }
                                        ?.let { e -> appContainer.itmoWidgets.api().markSportFreeSignEntrySatisfied(e.id) }
                                    sendNotification("Автозапись (при освобождении)", "Вы успешно записаны на занятие!")
                                } catch (e: Exception) {
                                    appContainer.errorLogRepository.logThrowable(e,
                                        MyFirebaseMessagingService::class.java.name)
                                }
                            }
                        }
                    }
                    SportAutoSignLessonsPayload.TYPE -> {
                        val data = gson.fromJson(wrapper.payload, SportFreeSignLessonsPayload::class.java)
                        CoroutineScope(Dispatchers.IO).launch {
                            val autoSignEntries = appContainer.itmoWidgets.api().mySportAutoSignEntries()
                            data.sportLessonIds.forEach {
                                try {
                                    appContainer.myItmo.api().signInLessons(listOf(it)).execute().body()!!.result
                                    autoSignEntries.data?.firstOrNull { e -> e.status == QueueEntryStatus.NOTIFIED }
                                        ?.let { e -> appContainer.itmoWidgets.api().markSportAutoSignEntrySatisfied(e.id) }
                                    sendNotification("Автозапись (на прогнозируемое занятие)", "Вы успешно записаны на занятие!")
                                } catch (e: Exception) {
                                    appContainer.errorLogRepository.logThrowable(e,
                                        MyFirebaseMessagingService::class.java.name)
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                try {
                    sendNotification("FCM", "Ошибка обработки события, посмотрите логи")
                    appContainer.errorLogRepository.logThrowable(e, MyFirebaseMessagingService::class.java.name)
                } catch (e: Exception) {
                    appContainer.errorLogRepository.logThrowable(e, MyFirebaseMessagingService::class.java.name)
                }
            }
        }
    }

    private fun sendNotification(title: String?, messageBody: String?) {
        val intent = Intent(this, MainActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pendingIntent = PendingIntent.getActivity(this, 0, intent,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE)

        val channelId = getString(R.string.default_notification_channel_id)
        val notificationBuilder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(messageBody)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        val channel = NotificationChannel(
            channelId,
            "Default Channel",
            NotificationManager.IMPORTANCE_DEFAULT
        )
        notificationManager.createNotificationChannel(channel)

        notificationManager.notify(0, notificationBuilder.build())
    }

    private fun sendTokenToServer(token: String) {
        val appContainer = (applicationContext as ItmoWidgetsApp).appContainer
        val itmoWidgets = appContainer.itmoWidgets
        appContainer.storage.utility.setFirebaseToken(token)
        if (appContainer.storage.settings.getCustomServicesState()) {
            runBlocking { itmoWidgets.sendFirebaseToken(token) }
        }
    }

    companion object {
        private const val TAG = "MyFirebaseMsgService"
    }
}