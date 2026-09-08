package dev.alllexey.itmowidgets.feature.schedule.data.local

import dev.alllexey.itmowidgets.feature.schedule.domain.model.DaySchedule
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

interface ScheduleLocalDataSource {

    fun observeRange(userIsu: Int?, start: LocalDate, end: LocalDate): Flow<List<DaySchedule>>

    suspend fun save(schedule: DaySchedule, userIsu: Int?)

    /**
     * Replaces the inclusive range for one user in a single observer snapshot.
     * Missing dates are removed; other dates/users are retained. Out-of-range input
     * is ignored. The on-disk cache remains best-effort, not a durable transaction.
     */
    suspend fun replaceRange(userIsu: Int?, start: LocalDate, end: LocalDate, schedules: List<DaySchedule>)

    suspend fun get(userIsu: Int?, date: LocalDate): CacheEntry?

    suspend fun clear()
}
