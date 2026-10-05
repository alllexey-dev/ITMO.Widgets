package dev.alllexey.itmowidgets.core.schedule

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

object ScheduleUtil {

    fun generateDates(start: LocalDate, end: LocalDate): List<LocalDate> {
        val list = mutableListOf<LocalDate>()
        var d = start
        while (d <= end) {
            list.add(d)
            d = d.plus(1, DateTimeUnit.DAY)
        }
        return list
    }
}
