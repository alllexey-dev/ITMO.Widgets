package dev.alllexey.itmowidgets.feature.schedule.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.Month
import java.time.temporal.TemporalAdjusters

/**
 * Monday–Sunday weeks that sample the schedule history. In the current academic year (1 September – 31 August) and
 * the 3 previous ones they are the weeks containing 25 September and 3 December (autumn) and 24 February and 5 March
 * (spring); weeks that start after today are left out. The current week is always there, so a first-year student in
 * early September has one too.
 */
object StudyWeeks {

    /** At most 17 weeks, newest first, each once. */
    fun sampled(today: LocalDate): List<ClosedRange<LocalDate>> {
        val currentYear = academicYear(today)
        val sampled = (0 until ACADEMIC_YEARS)
            .flatMap { back -> anchors(currentYear - back) }
            .map(::monday)
            .filter { !it.isAfter(today) }
        return (sampled + monday(today)).distinct().sortedDescending().map { it..it.plusDays(6) }
    }

    private fun anchors(year: Int): List<LocalDate> = listOf(
        LocalDate.of(year, Month.SEPTEMBER, 25),
        LocalDate.of(year, Month.DECEMBER, 3),
        LocalDate.of(year + 1, Month.FEBRUARY, 24),
        LocalDate.of(year + 1, Month.MARCH, 5),
    )

    private fun academicYear(date: LocalDate): Int = if (date.month >= Month.SEPTEMBER) date.year else date.year - 1

    private fun monday(date: LocalDate): LocalDate = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    private const val ACADEMIC_YEARS = 4
}
