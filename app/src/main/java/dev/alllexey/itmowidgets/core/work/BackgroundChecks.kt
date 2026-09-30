package dev.alllexey.itmowidgets.core.work

import androidx.work.ListenableWorker.Result
import java.time.LocalTime

/*
 * Rules shared by the background checks that run on the device (schedule changes, marks): when they stay
 * silent and how one run's outcome becomes a WorkManager result. Features use these instead of their own copies.
 */

/** Moscow's quiet hours: from midnight up to, not including, [UNTIL]; they never cross midnight. */
object QuietHours {
    val UNTIL: LocalTime = LocalTime.of(6, 0)

    fun isQuiet(time: LocalTime): Boolean = time < UNTIL
}

/** How one background run ended, for the worker to decide on a retry. */
enum class CheckOutcome { SKIPPED, DONE, RETRY }

/** Retries of one failed run; after them the next period tries again. */
const val MAX_RETRIES = 2

/** A failed check is retried [MAX_RETRIES] times with backoff; after that the next period tries again. */
fun workResultOf(outcome: CheckOutcome, attempt: Int): Result =
    if (outcome == CheckOutcome.RETRY && attempt < MAX_RETRIES) Result.retry() else Result.success()
