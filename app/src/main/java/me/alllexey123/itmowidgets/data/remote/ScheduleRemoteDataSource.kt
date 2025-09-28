package me.alllexey123.itmowidgets.data.remote

import api.myitmo.model.Schedule
import java.time.LocalDate

interface ScheduleRemoteDataSource {

    suspend fun getSchedule(startDate: LocalDate, endDate: LocalDate): List<Schedule>?

    suspend fun getSchedule(date: LocalDate): Schedule?
}