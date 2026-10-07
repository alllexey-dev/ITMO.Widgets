package dev.alllexey.itmowidgets.core.notification

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dev.alllexey.itmowidgets.core.diagnostics.AppLog
import dev.alllexey.itmowidgets.di.bridge.KoinStarter
import org.koin.core.Koin
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class MyFirebaseMessagingService : FirebaseMessagingService(), KoinComponent {
    private val log: AppLog by inject()

    /** Through the one idempotent starter, as every Android component; see [KoinStarter]. */
    override fun getKoin(): Koin = KoinStarter.ensureStarted(applicationContext)

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
