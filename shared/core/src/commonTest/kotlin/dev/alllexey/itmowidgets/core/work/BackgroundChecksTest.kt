package dev.alllexey.itmowidgets.core.work

import dev.alllexey.itmowidgets.core.result.AppError
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BackgroundChecksTest {

    @Test
    fun anyErrorButAnEndedSessionIsRetried() {
        assertEquals(CheckOutcome.DONE, outcomeOf(emptyList()))
        assertEquals(CheckOutcome.RETRY, outcomeOf(listOf(AppError.Network)))
        assertEquals(CheckOutcome.RETRY, outcomeOf(listOf(AppError.Unknown())))
        assertEquals(CheckOutcome.RETRY, outcomeOf(listOf(AppError.Unauthorized, AppError.Network)))
        assertEquals(CheckOutcome.DONE, outcomeOf(listOf(AppError.Unauthorized)))
    }

    @Test
    fun quietHoursRunFromMidnightUpToSix() {
        assertTrue(QuietHours.isQuiet(LocalTime(0, 0)))
        assertTrue(QuietHours.isQuiet(LocalTime(5, 59, 59)))
        assertFalse(QuietHours.isQuiet(LocalTime(6, 0)))
        assertFalse(QuietHours.isQuiet(LocalTime(23, 59)))
    }
}
