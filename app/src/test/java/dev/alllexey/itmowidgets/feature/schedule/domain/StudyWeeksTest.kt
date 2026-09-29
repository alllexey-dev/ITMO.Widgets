package dev.alllexey.itmowidgets.feature.schedule.domain

import java.time.DayOfWeek
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StudyWeeksTest {

    @Test
    fun `mid October has the current week, the September week and four weeks of each of three previous years`() {
        assertEquals(listOf(
            week(2026, 10, 12),
            week(2026, 9, 21),
            week(2026, 3, 2), week(2026, 2, 23), week(2025, 12, 1), week(2025, 9, 22),
            week(2025, 3, 3), week(2025, 2, 24), week(2024, 12, 2), week(2024, 9, 23),
            week(2024, 3, 4), week(2024, 2, 19), week(2023, 11, 27), week(2023, 9, 25),
        ), StudyWeeks.sampled(date(2026, 10, 15)))
    }

    @Test
    fun `before 25 September only the current week is from the new academic year`() {
        val weeks = StudyWeeks.sampled(date(2026, 9, 10))

        assertEquals(week(2026, 9, 7), weeks.first())
        assertEquals(week(2026, 3, 2), weeks[1])
        assertEquals(week(2023, 9, 25), weeks.last())
        assertEquals(13, weeks.size)
    }

    @Test
    fun `in January the autumn weeks of the current year are there and its spring weeks are not yet`() {
        val weeks = StudyWeeks.sampled(date(2027, 1, 15))

        assertEquals(listOf(week(2027, 1, 11), week(2026, 11, 30), week(2026, 9, 21), week(2026, 3, 2)), weeks.take(4))
        assertEquals(15, weeks.size)
    }

    @Test
    fun `in a leap year the 5 March week starts in February and is the current week only once`() {
        val weeks = StudyWeeks.sampled(date(2028, 3, 1))

        assertEquals(listOf(week(2028, 2, 28), week(2028, 2, 21), week(2027, 11, 29), week(2027, 9, 20)), weeks.take(4))
        assertEquals(date(2028, 2, 28)..date(2028, 3, 5), weeks.first())
        assertEquals(16, weeks.size)
    }

    @Test
    fun `a sampled week starting today is included`() {
        assertEquals(week(2025, 2, 24), StudyWeeks.sampled(date(2025, 2, 24)).first())
        assertEquals(week(2025, 12, 1), StudyWeeks.sampled(date(2025, 12, 1))[0])
    }

    @Test
    fun `late in the academic year there are seventeen distinct Monday to Sunday weeks, newest first`() {
        val weeks = StudyWeeks.sampled(date(2027, 6, 1))

        assertEquals(17, weeks.size)
        assertEquals(week(2027, 5, 31), weeks.first())
        assertEquals(week(2023, 9, 25), weeks.last())
        assertEquals(weeks.sortedByDescending { it.start }, weeks)
        assertTrue(weeks.all { it.start.dayOfWeek == DayOfWeek.MONDAY && it.endInclusive == it.start.plusDays(6) })
        assertEquals(weeks.size, weeks.distinct().size)
    }

    private fun week(year: Int, month: Int, monday: Int): ClosedRange<LocalDate> = date(year, month, monday).let { it..it.plusDays(6) }

    private fun date(year: Int, month: Int, day: Int): LocalDate = LocalDate.of(year, month, day)
}
