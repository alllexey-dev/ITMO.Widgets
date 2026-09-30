package dev.alllexey.itmowidgets.feature.schedule.work

import androidx.work.ListenableWorker.Result
import dev.alllexey.itmowidgets.feature.schedule.data.changes.CheckOutcome
import org.junit.Assert.assertEquals
import org.junit.Test

class ScheduleChangesWorkerTest {

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
