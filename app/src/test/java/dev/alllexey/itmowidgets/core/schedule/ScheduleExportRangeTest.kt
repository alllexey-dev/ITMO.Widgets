package dev.alllexey.itmowidgets.core.schedule

import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class ScheduleExportRangeTest {

    @Test
    fun `a week and two weeks start today`() {
        val today = LocalDate(2026, 10, 2)

        assertEquals(today..LocalDate(2026, 10, 8), ScheduleExportRange.Week.dates(today))
        assertEquals(today..LocalDate(2026, 10, 15), ScheduleExportRange.TwoWeeks.dates(today))
    }

    @Test
    fun `the autumn semester ends on 31 January`() {
        assertEquals(
            LocalDate(2027, 1, 31),
            ScheduleExportRange.Semester.dates(LocalDate(2026, 10, 2)).endInclusive
        )
        assertEquals(
            LocalDate(2027, 1, 31),
            ScheduleExportRange.Semester.dates(LocalDate(2026, 8, 20)).endInclusive
        )
        assertEquals(
            LocalDate(2027, 1, 31),
            ScheduleExportRange.Semester.dates(LocalDate(2027, 1, 31)).endInclusive
        )
    }

    @Test
    fun `the spring semester ends on 31 July`() {
        assertEquals(
            LocalDate(2027, 2, 1)..LocalDate(2027, 7, 31),
            ScheduleExportRange.Semester.dates(LocalDate(2027, 2, 1))
        )
        assertEquals(
            LocalDate(2027, 7, 31),
            ScheduleExportRange.Semester.dates(LocalDate(2027, 7, 31)).endInclusive
        )
    }

    @Test
    fun `custom dates are taken in either order`() {
        val start = LocalDate(2026, 9, 1)
        val end = LocalDate(2026, 9, 30)

        assertEquals(start..end, ScheduleExportRange.Custom(end, start).dates(LocalDate(2026, 10, 2)))
    }
}
