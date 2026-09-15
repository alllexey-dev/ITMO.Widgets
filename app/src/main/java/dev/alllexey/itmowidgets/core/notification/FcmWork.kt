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
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import dev.alllexey.itmowidgets.core.session.SessionTokenStore
import dev.alllexey.itmowidgets.core.session.CurrentUserProvider
import dev.alllexey.itmowidgets.core.storage.AppSettingsStorage
import kotlinx.coroutines.CancellationException

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

@EntryPoint
@InstallIn(SingletonComponent::class)
interface FcmWorkerEntryPoint {
    fun dispatcher(): FcmPayloadDispatcher
    fun tokenSync(): FcmTokenSync
    fun sessionTokens(): SessionTokenStore
    fun settings(): AppSettingsStorage
    fun currentUser(): CurrentUserProvider
}

class FcmMessageWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val dependencies = EntryPointAccessors.fromApplication(applicationContext, FcmWorkerEntryPoint::class.java)
        if (!FcmDeliveryGuard.canDeliver(
                inputData.getInt(FcmWork.RECIPIENT, 0), dependencies.currentUser().getCurrentUser()?.isu,
                dependencies.sessionTokens().hasRefreshToken(),
                dependencies.settings().getCustomServicesEnabled()
            )
        ) return Result.success()
        val json = inputData.getString(FcmWork.PAYLOAD) ?: return Result.success()
        dependencies.dispatcher().dispatch(json)
        // Booking retries are reserved by Backend, never blindly replay a complete push.
        return Result.success()
    }
}

class FcmTokenWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        return try {
            EntryPointAccessors.fromApplication(applicationContext, FcmWorkerEntryPoint::class.java).tokenSync().sync()
            Result.success()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            Result.retry()
        }
    }
}
