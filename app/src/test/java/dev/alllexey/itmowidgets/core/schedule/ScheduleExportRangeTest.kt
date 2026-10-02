package dev.alllexey.itmowidgets.core.schedule

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class ScheduleExportRangeTest {

    @Test
    fun `a week and two weeks start today`() {
        val today = LocalDate.of(2026, 10, 2)

        assertEquals(today..LocalDate.of(2026, 10, 8), ScheduleExportRange.Week.dates(today))
        assertEquals(today..LocalDate.of(2026, 10, 15), ScheduleExportRange.TwoWeeks.dates(today))
    }

    @Test
    fun `the autumn semester ends on 31 January`() {
        assertEquals(
            LocalDate.of(2027, 1, 31),
            ScheduleExportRange.Semester.dates(LocalDate.of(2026, 10, 2)).endInclusive
        )
        assertEquals(
            LocalDate.of(2027, 1, 31),
            ScheduleExportRange.Semester.dates(LocalDate.of(2026, 8, 20)).endInclusive
        )
        assertEquals(
            LocalDate.of(2027, 1, 31),
            ScheduleExportRange.Semester.dates(LocalDate.of(2027, 1, 31)).endInclusive
        )
    }

    @Test
    fun `the spring semester ends on 31 July`() {
        assertEquals(
            LocalDate.of(2027, 2, 1)..LocalDate.of(2027, 7, 31),
            ScheduleExportRange.Semester.dates(LocalDate.of(2027, 2, 1))
        )
        assertEquals(
            LocalDate.of(2027, 7, 31),
            ScheduleExportRange.Semester.dates(LocalDate.of(2027, 7, 31)).endInclusive
        )
    }

    @Test
    fun `custom dates are taken in either order`() {
        val start = LocalDate.of(2026, 9, 1)
        val end = LocalDate.of(2026, 9, 30)

        assertEquals(start..end, ScheduleExportRange.Custom(end, start).dates(LocalDate.of(2026, 10, 2)))
    }
}
