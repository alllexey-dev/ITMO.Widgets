package dev.alllexey.itmowidgets.core.notification

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.AndroidEntryPoint
import dev.alllexey.itmowidgets.core.diagnostics.AppLog
import javax.inject.Inject

@AndroidEntryPoint
class MyFirebaseMessagingService : FirebaseMessagingService() {
    @Inject lateinit var log: AppLog

    override fun onNewToken(token: String) {
        // Fetch the current SDK token in persistent work rather than persisting a stale callback.
        FcmWork.syncToken(this)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        try {
            val json = message.data["data"] ?: return
            val recipientIsu = message.data["recipient_isu"]?.toIntOrNull()?.takeIf { it > 0 } ?: return
            FcmWork.receive(this, json, recipientIsu)
        } catch (error: Exception) {
            log.warn("FirebaseMessaging", "FCM enqueue failed: ${error.javaClass.simpleName}")
        }
    }
}
