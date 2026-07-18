package dev.alllexey.itmowidgets.data.remote

import dev.alllexey.itmowidgets.domain.model.schedule.DaySchedule
import java.time.LocalDate

interface ScheduleRemoteDataSource {

    suspend fun getSchedule(
        userIsu: Int?,
        start: LocalDate,
        end: LocalDate
    ): List<DaySchedule>
}
