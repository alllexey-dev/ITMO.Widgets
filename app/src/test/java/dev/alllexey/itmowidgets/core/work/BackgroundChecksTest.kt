package dev.alllexey.itmowidgets.core.work

import androidx.work.ListenableWorker.Result
import dev.alllexey.itmowidgets.core.result.AppError
import kotlinx.datetime.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackgroundChecksTest {

    @Test
    fun `a failed check is retried twice, then waits for the next period`() {
        assertEquals(Result.retry(), workResultOf(CheckOutcome.RETRY, 0))
        assertEquals(Result.retry(), workResultOf(CheckOutcome.RETRY, 1))
        assertEquals(Result.success(), workResultOf(CheckOutcome.RETRY, 2))
    }

    @Test
    fun `any error but an ended session is retried`() {
        assertEquals(CheckOutcome.DONE, outcomeOf(emptyList()))
        assertEquals(CheckOutcome.RETRY, outcomeOf(listOf(AppError.Network)))
        assertEquals(CheckOutcome.RETRY, outcomeOf(listOf(AppError.Unknown())))
        assertEquals(CheckOutcome.RETRY, outcomeOf(listOf(AppError.Unauthorized, AppError.Network)))
        assertEquals(CheckOutcome.DONE, outcomeOf(listOf(AppError.Unauthorized)))
    }

    @Test
    fun `a finished or skipped check succeeds`() {
        assertEquals(Result.success(), workResultOf(CheckOutcome.DONE, 0))
        assertEquals(Result.success(), workResultOf(CheckOutcome.SKIPPED, 0))
    }

    @Test
    fun `quiet hours run from midnight up to six`() {
        assertTrue(QuietHours.isQuiet(LocalTime(0, 0)))
        assertTrue(QuietHours.isQuiet(LocalTime(5, 59, 59)))
        assertFalse(QuietHours.isQuiet(LocalTime(6, 0)))
        assertFalse(QuietHours.isQuiet(LocalTime(23, 59)))
    }
}
