package me.alllexey123.itmowidgets.data.local

import api.myitmo.model.Schedule
import java.time.LocalDate

interface ScheduleLocalDataSource {

    fun getSchedule(date: LocalDate): Pair<Schedule, Long>?

    fun saveSchedule(schedule: Schedule)

    fun isExpired(timestamp: Long): Boolean

    fun clearCache()

    // all schedules found for given range
    fun getSchedulesForRange(startDate: LocalDate, endDate: LocalDate): List<Schedule>
}