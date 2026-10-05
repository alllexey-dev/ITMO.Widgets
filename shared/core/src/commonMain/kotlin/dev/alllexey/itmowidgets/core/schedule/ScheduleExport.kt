package dev.alllexey.itmowidgets.core.schedule

import dev.alllexey.itmowidgets.core.result.AppResult
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import kotlinx.datetime.plus

/** Days of the own schedule a `.ics` file covers, counted from today. */
sealed interface ScheduleExportRange {
    /** Today and the next 6 days. */
    data object Week : ScheduleExportRange

    /** Today and the next 13 days. */
    data object TwoWeeks : ScheduleExportRange

    /** Today to the end of the half-year: 31 January for August–January, 31 July for February–July. */
    data object Semester : ScheduleExportRange

    data class Custom(val start: LocalDate, val end: LocalDate) : ScheduleExportRange

    fun dates(today: LocalDate): ClosedRange<LocalDate> = when (this) {
        Week -> today..today.plus(6, DateTimeUnit.DAY)
        TwoWeeks -> today..today.plus(13, DateTimeUnit.DAY)
        Semester -> today..semesterEnd(today)
        is Custom -> minOf(start, end)..maxOf(start, end)
    }

    private fun semesterEnd(today: LocalDate): LocalDate = when {
        today.month == Month.JANUARY -> LocalDate(today.year, Month.JANUARY, 31)
        today.month >= Month.AUGUST -> LocalDate(today.year + 1, Month.JANUARY, 31)
        else -> LocalDate(today.year, Month.JULY, 31)
    }
}

/** A written `.ics` file: a `content://` address other apps may read, and the number of lessons in it. */
data class IcsFile(val uri: String, val name: String, val lessons: Int)

/** Writes the own schedule into a `.ics` file; implemented by the schedule feature. */
interface ScheduleIcsExport {
    /** Reads My ITMO for [range]; `null` when the range has no lessons, and then nothing is written. */
    suspend fun export(range: ScheduleExportRange): AppResult<IcsFile?>
}
