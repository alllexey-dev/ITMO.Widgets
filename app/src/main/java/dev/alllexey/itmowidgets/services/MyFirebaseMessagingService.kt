package dev.alllexey.itmowidgets.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import api.myitmo.utils.ApiException
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.google.gson.JsonElement
import dev.alllexey.itmowidgets.ItmoWidgetsApp
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.model.BasicSportLessonData
import dev.alllexey.itmowidgets.core.model.fcm.FcmJsonWrapper
import dev.alllexey.itmowidgets.core.model.fcm.impl.SportAutoSignLessonsPayload
import dev.alllexey.itmowidgets.core.model.fcm.impl.SportFreeSignLessonsPayload
import dev.alllexey.itmowidgets.ui.main.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlin.random.Random

class MyFirebaseMessagingService : FirebaseMessagingService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val appContainer by lazy { (applicationContext as ItmoWidgetsApp).appContainer }
    private val gson by lazy { appContainer.gson }
    private val errorLogRepository by lazy { appContainer.errorLogRepository }
    private val myItmoApi by lazy { appContainer.myItmo.api }
    private val widgetsApi by lazy { appContainer.itmoWidgets.api }

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
                SportFreeSignLessonsPayload.TYPE -> handleFreeSign(wrapper.payload)
                SportAutoSignLessonsPayload.TYPE -> handleAutoSign(wrapper.payload)
                else -> Log.w(TAG, "Unknown payload type: ${wrapper.type}")
            }
        } catch (e: Exception) {
            handleError(e)
        }
    }

    private fun handleFreeSign(payloadJson: JsonElement) {
        val data = gson.fromJson(payloadJson, SportFreeSignLessonsPayload::class.java)

        serviceScope.launch {
            processLessonSignUp(
                lessons = data.sportLessons,
                notificationTitle = "Автозапись (при освобождении)",
                markSatisfiedByLesson = { lessonId -> widgetsApi.markSportFreeSignEntrySatisfiedByLesson(lessonId) },
                cancelByLesson = { lessonId -> widgetsApi.cancelSportFreeSignEntryByLesson(lessonId)}
            )
        }
    }

    private fun handleAutoSign(payloadJson: JsonElement) {
        val data = gson.fromJson(payloadJson, SportFreeSignLessonsPayload::class.java)

        serviceScope.launch {
            processLessonSignUp(
                lessons = data.sportLessons,
                notificationTitle = "Автозапись (на прогнозируемое занятие)",
                markSatisfiedByLesson = { lessonId -> widgetsApi.markSportAutoSignEntrySatisfiedByLesson(lessonId) },
                cancelByLesson = { lessonId -> widgetsApi.cancelSportAutoSignEntryByLesson(lessonId)}
            )
        }
    }

    private suspend fun processLessonSignUp(
        lessons: List<BasicSportLessonData>,
        notificationTitle: String,
        markSatisfiedByLesson: suspend (Long) -> Unit,
        cancelByLesson: suspend (Long) -> Unit
    ) {
        val formatter = DateTimeFormatter
            .ofLocalizedDateTime(FormatStyle.SHORT)
            .withLocale(Locale.getDefault())

        lessons.forEach { lesson ->
            val localDateTime = lesson.dateStart
                .atZoneSameInstant(ZoneId.systemDefault())
            val dateFormat = localDateTime.format(formatter)

            try {
                val body = appContainer.myItmo.execute(myItmoApi.signInLessons(listOf(lesson.id)))

                if (body?.result != null) {
                    sendNotification(notificationTitle, "Вы успешно записаны на занятие!\n${lesson.sectionName}, $dateFormat")
                    markSatisfiedByLesson(lesson.id)
                } else {
                    throw RuntimeException("Could not sign in sport lesson: ${body?.errorMessage}")
                }
            } catch (e: ApiException) {
                val limitsErrMsg = "нельзя записать студента: нет свободных мест на занятии"
                errorLogRepository.logThrowable(e, TAG)

                val dueToLimits = e.errorMessage.contains(limitsErrMsg)
                        && e.errorMessage.replace(limitsErrMsg, "").length > 20
                if (!dueToLimits && e.cause == null) {
                    sendNotification(notificationTitle, "Невозможно записать на занятие!\n${lesson.sectionName}, $dateFormat")
                    cancelByLesson(lesson.id)
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
            .setSmallIcon(R.drawable.ic_exercise)
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