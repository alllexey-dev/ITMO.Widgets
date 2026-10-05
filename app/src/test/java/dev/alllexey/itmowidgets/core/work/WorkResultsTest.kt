package dev.alllexey.itmowidgets.core.work

import androidx.work.ListenableWorker.Result
import org.junit.Assert.assertEquals
import org.junit.Test

class WorkResultsTest {

    @Test
    fun `a failed check is retried twice, then waits for the next period`() {
        assertEquals(Result.retry(), workResultOf(CheckOutcome.RETRY, 0))
        assertEquals(Result.retry(), workResultOf(CheckOutcome.RETRY, 1))
        assertEquals(Result.success(), workResultOf(CheckOutcome.RETRY, 2))
    }

    @Test
    fun `a finished or skipped check succeeds`() {
        assertEquals(Result.success(), workResultOf(CheckOutcome.DONE, 0))
        assertEquals(Result.success(), workResultOf(CheckOutcome.SKIPPED, 0))
    }
}
