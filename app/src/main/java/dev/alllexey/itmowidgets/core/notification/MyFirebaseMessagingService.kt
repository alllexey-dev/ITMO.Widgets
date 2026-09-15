package dev.alllexey.itmowidgets.core.notification

import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MyFirebaseMessagingService : FirebaseMessagingService() {
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
            Log.w("FirebaseMessaging", "FCM enqueue failed: ${error.javaClass.simpleName}")
        }
    }
}
