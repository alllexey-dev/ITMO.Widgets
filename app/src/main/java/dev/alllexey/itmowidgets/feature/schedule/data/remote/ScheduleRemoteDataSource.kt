package dev.alllexey.itmowidgets.feature.schedule.data.remote

import dev.alllexey.itmowidgets.feature.schedule.domain.model.DaySchedule
import java.time.LocalDate

interface ScheduleRemoteDataSource {

    suspend fun getSchedule(
        userIsu: Int?,
        start: LocalDate,
        end: LocalDate
    ): List<DaySchedule>
}
