package dev.alllexey.itmowidgets.core.notification

import android.util.Log
import com.google.firebase.messaging.FirebaseMessaging
import dev.alllexey.itmowidgets.core.session.BackendDeviceSession
import dev.alllexey.itmowidgets.core.session.CurrentUserProvider
import dev.alllexey.itmowidgets.core.session.SessionTokenStore
import dev.alllexey.itmowidgets.core.storage.AppSettingsStorage
import dev.alllexey.itmowidgets.core.storage.UtilityStorage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import javax.inject.Inject

fun interface FcmTokenSync {
    suspend fun sync()
}

fun interface FirebaseTokenProvider {
    suspend fun currentToken(): String
}

class DefaultFirebaseTokenProvider @Inject constructor() : FirebaseTokenProvider {
    override suspend fun currentToken(): String = suspendCancellableCoroutine { continuation ->
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (!continuation.isActive) return@addOnCompleteListener
            if (task.isSuccessful) continuation.resume(task.result)
            else continuation.resumeWithException(task.exception ?: IllegalStateException("FCM token unavailable"))
        }
    }
}

class DefaultFcmTokenSync @Inject constructor(
    private val tokens: FirebaseTokenProvider,
    private val utility: UtilityStorage,
    private val settings: AppSettingsStorage,
    private val sessionTokens: SessionTokenStore,
    private val deviceSession: BackendDeviceSession,
    private val currentUser: CurrentUserProvider
) : FcmTokenSync {
    private val mutex = Mutex()

    override suspend fun sync() = mutex.withLock {
        try {
            val token = tokens.currentToken().trim()
            if (token.isEmpty()) return@withLock
            if (utility.getFirebaseToken() != token) utility.setFirebaseToken(token)
            val ownerIsu = currentUser.getCurrentUser()?.isu?.takeIf { it > 0 }
            if (settings.getCustomServicesEnabled() && sessionTokens.hasRefreshToken() && ownerIsu != null &&
                (utility.getRegisteredFirebaseToken() != token || utility.getRegisteredFirebaseOwner() != ownerIsu)
            ) {
                deviceSession.registerCurrentDevice()
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Log.w("FcmTokenSync", "FCM token sync failed: ${error.javaClass.simpleName}")
            throw error
        }
    }
}
