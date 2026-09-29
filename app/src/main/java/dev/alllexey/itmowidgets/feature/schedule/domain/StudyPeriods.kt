package dev.alllexey.itmowidgets.feature.schedule.domain

import java.time.LocalDate
import java.time.Month

/** Study periods run from 1 September to 31 January and from 1 February to 31 August. */
object StudyPeriods {

    /** The [count] most recent periods, current first; the current one ends at [today]. */
    fun recent(today: LocalDate, count: Int): List<ClosedRange<LocalDate>> {
        var start = periodStart(today)
        val periods = mutableListOf<ClosedRange<LocalDate>>(start..today)
        while (periods.size < count) {
            val end = start.minusDays(1)
            start = periodStart(end)
            periods += start..end
        }
        return periods.take(count)
    }

    private fun periodStart(date: LocalDate): LocalDate = when {
        date.month >= Month.SEPTEMBER -> LocalDate.of(date.year, Month.SEPTEMBER, 1)
        date.month == Month.JANUARY -> LocalDate.of(date.year - 1, Month.SEPTEMBER, 1)
        else -> LocalDate.of(date.year, Month.FEBRUARY, 1)
    }
}
