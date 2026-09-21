package dev.alllexey.itmowidgets.feature.schedule.data.local

import dev.alllexey.itmowidgets.feature.schedule.domain.model.DaySchedule
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

interface ScheduleLocalDataSource {

    fun observeRange(userIsu: Int?, start: LocalDate, end: LocalDate): Flow<List<DaySchedule>>
    /**
     * The range from memory only, without touching the disk: what a screen can
     * show in its first frame. Null until [observeRange] has hydrated every date.
     */
    fun peekRange(userIsu: Int?, start: LocalDate, end: LocalDate): List<DaySchedule>? = null

    suspend fun save(schedule: DaySchedule, userIsu: Int?)

    /**
     * Replaces the inclusive range for one user in a single observer snapshot.
     * Missing dates are removed; other dates/users are retained. Out-of-range input
     * is ignored. The on-disk cache remains best-effort, not a durable transaction.
     */
    suspend fun replaceRange(userIsu: Int?, start: LocalDate, end: LocalDate, schedules: List<DaySchedule>)

    suspend fun get(userIsu: Int?, date: LocalDate): CacheEntry?

    /** Removes every cached date for a denied foreign user, without touching own data. */
    suspend fun clearUser(userIsu: Int)

    suspend fun clear()
}
