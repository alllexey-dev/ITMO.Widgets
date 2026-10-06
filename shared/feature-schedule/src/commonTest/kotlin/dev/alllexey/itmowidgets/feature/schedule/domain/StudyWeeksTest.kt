package dev.alllexey.itmowidgets.feature.schedule.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

class StudyWeeksTest {

    @Test
    fun midOctoberHasTheCurrentWeekTheSeptemberWeekAndFourWeeksOfEachOfThreePreviousYears() {
        assertEquals(listOf(
            week(2026, 10, 12),
            week(2026, 9, 21),
            week(2026, 3, 2), week(2026, 2, 23), week(2025, 12, 1), week(2025, 9, 22),
            week(2025, 3, 3), week(2025, 2, 24), week(2024, 12, 2), week(2024, 9, 23),
            week(2024, 3, 4), week(2024, 2, 19), week(2023, 11, 27), week(2023, 9, 25),
        ), StudyWeeks.sampled(date(2026, 10, 15)))
    }

    @Test
    fun before25SeptemberOnlyTheCurrentWeekIsFromTheNewAcademicYear() {
        val weeks = StudyWeeks.sampled(date(2026, 9, 10))

        assertEquals(week(2026, 9, 7), weeks.first())
        assertEquals(week(2026, 3, 2), weeks[1])
        assertEquals(week(2023, 9, 25), weeks.last())
        assertEquals(13, weeks.size)
    }

    @Test
    fun inJanuaryTheAutumnWeeksOfTheCurrentYearAreThereAndItsSpringWeeksAreNotYet() {
        val weeks = StudyWeeks.sampled(date(2027, 1, 15))

        assertEquals(listOf(week(2027, 1, 11), week(2026, 11, 30), week(2026, 9, 21), week(2026, 3, 2)), weeks.take(4))
        assertEquals(15, weeks.size)
    }

    @Test
    fun inALeapYearThe5MarchWeekStartsInFebruaryAndIsTheCurrentWeekOnlyOnce() {
        val weeks = StudyWeeks.sampled(date(2028, 3, 1))

        assertEquals(listOf(week(2028, 2, 28), week(2028, 2, 21), week(2027, 11, 29), week(2027, 9, 20)), weeks.take(4))
        assertEquals(date(2028, 2, 28)..date(2028, 3, 5), weeks.first())
        assertEquals(16, weeks.size)
    }

    @Test
    fun aSampledWeekStartingTodayIsIncluded() {
        assertEquals(week(2025, 2, 24), StudyWeeks.sampled(date(2025, 2, 24)).first())
        assertEquals(week(2025, 12, 1), StudyWeeks.sampled(date(2025, 12, 1))[0])
    }

    @Test
    fun lateInTheAcademicYearThereAreSeventeenDistinctMondayToSundayWeeksNewestFirst() {
        val weeks = StudyWeeks.sampled(date(2027, 6, 1))

        assertEquals(17, weeks.size)
        assertEquals(week(2027, 5, 31), weeks.first())
        assertEquals(week(2023, 9, 25), weeks.last())
        assertEquals(weeks.sortedByDescending { it.start }, weeks)
        assertTrue(weeks.all { it.start.dayOfWeek == DayOfWeek.MONDAY && it.endInclusive == it.start.plus(6, DateTimeUnit.DAY) })
        assertEquals(weeks.size, weeks.distinct().size)
    }

    private fun week(year: Int, month: Int, monday: Int): ClosedRange<LocalDate> = date(year, month, monday).let { it..it.plus(6, DateTimeUnit.DAY) }

    private fun date(year: Int, month: Int, day: Int): LocalDate = LocalDate(year, month, day)
}
