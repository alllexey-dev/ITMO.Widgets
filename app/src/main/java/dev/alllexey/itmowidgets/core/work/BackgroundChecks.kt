package dev.alllexey.itmowidgets.core.work

import androidx.work.ListenableWorker.Result
import dev.alllexey.itmowidgets.core.result.AppError
import kotlinx.datetime.LocalTime

/*
 * Rules shared by the background checks that run on the device (schedule changes, marks): when they stay
 * silent and how one run's outcome becomes a WorkManager result. Features use these instead of their own copies.
 */

/** Moscow's quiet hours: from midnight up to, not including, [UNTIL]; they never cross midnight. */
object QuietHours {
    val UNTIL: LocalTime = LocalTime(6, 0)

    fun isQuiet(time: LocalTime): Boolean = time < UNTIL
}

/** How one background run ended, for the worker to decide on a retry. */
enum class CheckOutcome { SKIPPED, DONE, RETRY }

/**
 * The outcome of a run whose checks failed with [errors]: [CheckOutcome.RETRY] unless every error is
 * [AppError.Unauthorized], which only a new sign-in fixes. A network failure before any answer (`UnknownHostException`
 * and other `IOException`s; MIUI cuts the network of a backgrounded app although WorkManager's `CONNECTED` constraint
 * holds) is temporary: the run is retried within [MAX_RETRIES], the snapshot stays and no sign-in prompt is shown.
 */
fun outcomeOf(errors: Collection<AppError>): CheckOutcome =
    if (errors.any { it != AppError.Unauthorized }) CheckOutcome.RETRY else CheckOutcome.DONE

/** Retries of one failed run; after them the next period tries again. */
const val MAX_RETRIES = 2

/** A failed check is retried [MAX_RETRIES] times with backoff; after that the next period tries again. */
fun workResultOf(outcome: CheckOutcome, attempt: Int): Result =
    if (outcome == CheckOutcome.RETRY && attempt < MAX_RETRIES) Result.retry() else Result.success()
