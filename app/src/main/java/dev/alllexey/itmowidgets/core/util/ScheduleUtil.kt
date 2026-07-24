package dev.alllexey.itmowidgets.core.util

import java.time.LocalDate

object ScheduleUtil {

    fun generateDates(start: LocalDate, end: LocalDate): List<LocalDate> {
        val list = mutableListOf<LocalDate>()
        var d = start
        while (!d.isAfter(end)) {
            list.add(d)
            d = d.plusDays(1)
        }
        return list
    }
}
