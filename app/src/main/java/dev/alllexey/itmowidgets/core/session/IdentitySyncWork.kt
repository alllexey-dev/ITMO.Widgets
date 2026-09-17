package dev.alllexey.itmowidgets.core.session

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException

/**
 * A failed identity upload leaves the user nameless for everyone else until the
 * next cold start. Persistent work retries with backoff until Backend accepts it.
 */
object IdentitySyncWork {
    private const val NAME = "backend-identity-sync"
    internal const val MAX_ATTEMPTS = 6

    fun schedule(context: Context) {
        val request = OneTimeWorkRequestBuilder<IdentitySyncWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(NAME, ExistingWorkPolicy.KEEP, request)
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface IdentitySyncEntryPoint {
    fun identitySync(): BackendIdentitySync
}

class IdentitySyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val sync = EntryPointAccessors.fromApplication(applicationContext, IdentitySyncEntryPoint::class.java).identitySync()
        return try {
            when {
                sync.sync(scheduleRetry = false) -> Result.success()
                runAttemptCount + 1 >= IdentitySyncWork.MAX_ATTEMPTS -> Result.failure()
                else -> Result.retry()
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            Result.retry()
        }
    }
}
