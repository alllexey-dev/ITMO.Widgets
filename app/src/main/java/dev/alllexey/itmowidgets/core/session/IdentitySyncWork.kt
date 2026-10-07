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
import dev.alllexey.itmowidgets.di.bridge.KoinStarter
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import org.koin.core.Koin
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

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

class IdentitySyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params), KoinComponent {
    private val sync: BackendIdentitySync by inject()

    /** WorkManager can run a worker before `Application.onCreate()` has started Koin. */
    override fun getKoin(): Koin = KoinStarter.ensureStarted(applicationContext)

    override suspend fun doWork(): Result {
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
