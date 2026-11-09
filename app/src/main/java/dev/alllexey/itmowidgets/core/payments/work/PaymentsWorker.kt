package dev.alllexey.itmowidgets.core.payments.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker.Result
import androidx.work.WorkerParameters
import dev.alllexey.itmowidgets.core.payments.check.PaymentsCadence
import dev.alllexey.itmowidgets.core.payments.check.PaymentsCheck
import dev.alllexey.itmowidgets.core.work.PeriodicCheckSpec
import dev.alllexey.itmowidgets.core.work.workResultOf
import dev.alllexey.itmowidgets.di.KoinStarter
import kotlin.time.Duration.Companion.minutes
import org.koin.core.Koin
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/** The check `Общежитие и оплата` (dormitory and tuition) as WorkManager runs it. */
class PaymentsWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params), KoinComponent {
    private val check: PaymentsCheck by inject()

    /** WorkManager can run a worker before `Application.onCreate()` has started Koin. */
    override fun getKoin(): Koin = KoinStarter.ensureStarted(applicationContext)

    override suspend fun doWork(): Result {
        // CancellationException propagates: WorkManager stopped the run and owns what happens next.
        return workResultOf(check.run(), runAttemptCount)
    }
}

/**
 * One run every three hours with a network; the check itself asks my.itmo less often when nothing is pending
 * (`PaymentsCadence`). The names go to `enqueueUniquePeriodicWork` and `enqueueUniqueWork` and are stable identifiers.
 */
val PAYMENTS_SPEC = PeriodicCheckSpec(
    worker = PaymentsWorker::class,
    periodicWork = "dormitory-payments-check",
    oneOffWork = "dormitory-payments-check-now",
    tag = "dormitory-payments",
    period = PaymentsCadence.PERIOD,
    backoff = 15.minutes
)
