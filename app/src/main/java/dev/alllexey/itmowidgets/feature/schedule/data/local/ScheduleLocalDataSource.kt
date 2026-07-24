package dev.alllexey.itmowidgets.feature.schedule.data.local

import dev.alllexey.itmowidgets.feature.schedule.domain.model.DaySchedule
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

interface ScheduleLocalDataSource {

    fun observeRange(userIsu: Int?, start: LocalDate, end: LocalDate): Flow<List<DaySchedule>>

    suspend fun save(schedule: DaySchedule, userIsu: Int?)

    suspend fun get(userIsu: Int?, date: LocalDate): CacheEntry?

    suspend fun clear()
}
