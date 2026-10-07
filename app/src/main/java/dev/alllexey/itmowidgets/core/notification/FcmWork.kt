package dev.alllexey.itmowidgets.core.notification

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dev.alllexey.itmowidgets.core.services.BackendGate
import dev.alllexey.itmowidgets.core.session.CurrentUserProvider
import dev.alllexey.itmowidgets.core.session.SessionTokenStore
import dev.alllexey.itmowidgets.di.bridge.KoinStarter
import kotlinx.coroutines.CancellationException
import org.koin.core.Koin
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/** Persistent work owns network processing beyond Firebase's short callback lifetime. */
object FcmWork {
    private const val TAG = "fcm-processing"
    internal const val PAYLOAD = "payload"
    internal const val RECIPIENT = "recipient_isu"

    fun syncToken(context: Context) {
        WorkManager.getInstance(context).enqueueUniqueWork("fcm-token-sync", ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<FcmTokenWorker>().build())
    }

    fun receive(context: Context, json: String, recipientIsu: Int) {
        if (json.toByteArray(Charsets.UTF_8).size > FcmPayloadDispatcher.MAX_PAYLOAD_BYTES) return
        val request = OneTimeWorkRequestBuilder<FcmMessageWorker>()
            .setInputData(Data.Builder().putString(PAYLOAD, json).putInt(RECIPIENT, recipientIsu).build())
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .addTag(TAG).build()
        // Serialize messages to prevent parallel attempts to book the same lesson.
        WorkManager.getInstance(context).enqueueUniqueWork(
            "fcm-messages", ExistingWorkPolicy.APPEND_OR_REPLACE, request
        )
    }

    fun cancelMessages(context: Context) {
        WorkManager.getInstance(context).cancelAllWorkByTag(TAG)
    }
}

class FcmMessageWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params), KoinComponent {
    private val currentUser: CurrentUserProvider by inject()
    private val sessionTokens: SessionTokenStore by inject()
    private val backendGate: BackendGate by inject()
    private val dispatcher: FcmPayloadDispatcher by inject()

    /** WorkManager can run a worker before `Application.onCreate()` has started Koin. */
    override fun getKoin(): Koin = KoinStarter.ensureStarted(applicationContext)

    override suspend fun doWork(): Result {
        if (!FcmDeliveryGuard.canDeliver(
                inputData.getInt(FcmWork.RECIPIENT, 0), currentUser.getCurrentUser()?.isu,
                sessionTokens.hasRefreshToken(),
                backendGate.isOptedIn()
            )
        ) return Result.success()
        val json = inputData.getString(FcmWork.PAYLOAD) ?: return Result.success()
        dispatcher.dispatch(json)
        // Booking retries are reserved by Backend, never blindly replay a complete push.
        return Result.success()
    }
}

class FcmTokenWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params), KoinComponent {
    private val tokenSync: FcmTokenSync by inject()

    /** WorkManager can run a worker before `Application.onCreate()` has started Koin. */
    override fun getKoin(): Koin = KoinStarter.ensureStarted(applicationContext)

    override suspend fun doWork(): Result {
        return try {
            tokenSync.sync()
            Result.success()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            Result.retry()
        }
    }
}
