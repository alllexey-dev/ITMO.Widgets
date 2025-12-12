package dev.alllexey.itmowidgets.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.google.gson.JsonElement
import dev.alllexey.itmowidgets.ItmoWidgetsApp
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.model.ApiResponse
import dev.alllexey.itmowidgets.core.model.QueueEntry
import dev.alllexey.itmowidgets.core.model.QueueEntryStatus
import dev.alllexey.itmowidgets.core.model.fcm.FcmJsonWrapper
import dev.alllexey.itmowidgets.core.model.fcm.impl.SportAutoSignLessonsPayload
import dev.alllexey.itmowidgets.core.model.fcm.impl.SportFreeSignLessonsPayload
import dev.alllexey.itmowidgets.core.model.fcm.impl.SportNewLessonsPayload
import dev.alllexey.itmowidgets.ui.main.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlin.random.Random

class MyFirebaseMessagingService : FirebaseMessagingService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val appContainer by lazy { (applicationContext as ItmoWidgetsApp).appContainer }
    private val gson by lazy { appContainer.gson }
    private val errorLogRepository by lazy { appContainer.errorLogRepository }
    private val myItmoApi by lazy { appContainer.myItmo.api() }
    private val widgetsApi by lazy { appContainer.itmoWidgets.api() }

    override fun onNewToken(token: String) {
        Log.d(TAG, "Refreshed token: $token")
        sendTokenToServer(token)
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        Log.d(TAG, "From: ${remoteMessage.from}")

        remoteMessage.notification?.let {
            Log.d(TAG, "Message Notification Body: ${it.body}")
            sendNotification(it.title, it.body)
        }

        remoteMessage.data["data"]?.let { jsonPayload ->
            handleDataMessage(jsonPayload)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    private fun handleDataMessage(jsonPayload: String) {
        try {
            val wrapper = gson.fromJson(jsonPayload, FcmJsonWrapper::class.java)
            when (wrapper.type) {
                SportNewLessonsPayload.TYPE -> handleNewLessons(wrapper.payload)
                SportFreeSignLessonsPayload.TYPE -> handleFreeSign(wrapper.payload)
                SportAutoSignLessonsPayload.TYPE -> handleAutoSign(wrapper.payload)
                else -> Log.w(TAG, "Unknown payload type: ${wrapper.type}")
            }
        } catch (e: Exception) {
            handleError(e)
        }
    }

    private fun handleNewLessons(payloadJson: JsonElement) {
        // unused
//        val data = gson.fromJson(payloadJson, SportNewLessonsPayload::class.java)
//        sendNotification("Уведомление фильтра", "${data.sportLessonIds.size} new lessons")
    }

    private fun handleFreeSign(payloadJson: JsonElement) {
        val data = gson.fromJson(payloadJson, SportFreeSignLessonsPayload::class.java)

        serviceScope.launch {
            processLessonSignUp(
                lessonIds = data.sportLessonIds,
                notificationTitle = "Автозапись (при освобождении)",
                fetchEntries = { widgetsApi.mySportFreeSignEntries() },
                markSatisfied = { id -> widgetsApi.markSportFreeSignEntrySatisfied(id) }
            )
        }
    }

    private fun handleAutoSign(payloadJson: JsonElement) {
        val data = gson.fromJson(payloadJson, SportFreeSignLessonsPayload::class.java)

        serviceScope.launch {
            processLessonSignUp(
                lessonIds = data.sportLessonIds,
                notificationTitle = "Автозапись (на прогнозируемое занятие)",
                fetchEntries = { widgetsApi.mySportAutoSignEntries() },
                markSatisfied = { id -> widgetsApi.markSportAutoSignEntrySatisfied(id) }
            )
        }
    }

    private suspend fun <T : QueueEntry> processLessonSignUp(
        lessonIds: List<Long>,
        notificationTitle: String,
        fetchEntries: suspend () -> ApiResponse<List<T>>,
        markSatisfied: suspend (Long) -> Unit
    ) {
        val entriesResponse = try {
            fetchEntries()
        } catch (e: Exception) {
            errorLogRepository.logThrowable(e, TAG)
            return
        }

        lessonIds.forEach { lessonId ->
            try {
                val body = myItmoApi.signInLessons(listOf(lessonId)).execute().body()

                if (body?.result != null) {
                    entriesResponse.data
                        ?.firstOrNull { e -> e.status == QueueEntryStatus.NOTIFIED }
                        ?.let { entry ->
                            markSatisfied(entry.id)
                        }

                    sendNotification(notificationTitle, "Вы успешно записаны на занятие!")
                } else {
                    throw RuntimeException("Could not sign in sport lesson: ${body?.errorMessage}")
                }
            } catch (e: Exception) {
                errorLogRepository.logThrowable(e, TAG)
            }
        }
    }

    private fun sendNotification(title: String?, messageBody: String?) {
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )

        val channelId = getString(R.string.default_notification_channel_id)

        createNotificationChannel(channelId)

        val notificationBuilder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(messageBody)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        val notificationId = (messageBody?.hashCode() ?: Random.nextInt())
        notificationManager.notify(notificationId, notificationBuilder.build())
    }

    private fun createNotificationChannel(channelId: String) {
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (notificationManager.getNotificationChannel(channelId) == null) {
            val channel = NotificationChannel(
                channelId,
                "Default Channel",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun sendTokenToServer(token: String) {
        appContainer.storage.utility.setFirebaseToken(token)
        if (appContainer.storage.settings.getCustomServicesState()) {
            serviceScope.launch {
                try {
                    appContainer.itmoWidgets.sendFirebaseToken(token)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to send token", e)
                }
            }
        }
    }

    private fun handleError(e: Exception) {
        try {
            sendNotification("FCM Error", "Ошибка обработки события, посмотрите логи")
            errorLogRepository.logThrowable(e, TAG)
        } catch (innerEx: Exception) {
            Log.e(TAG, "Error handling failed", innerEx)
        }
    }

    companion object {
        private const val TAG = "MyFirebaseMsgService"
    }
}