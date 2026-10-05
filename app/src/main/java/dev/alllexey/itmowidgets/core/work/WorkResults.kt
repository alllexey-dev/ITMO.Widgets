package dev.alllexey.itmowidgets.core.work

import androidx.work.ListenableWorker.Result

/** A failed check is retried [MAX_RETRIES] times with backoff; after that the next period tries again. */
fun workResultOf(outcome: CheckOutcome, attempt: Int): Result =
    if (outcome == CheckOutcome.RETRY && attempt < MAX_RETRIES) Result.retry() else Result.success()
