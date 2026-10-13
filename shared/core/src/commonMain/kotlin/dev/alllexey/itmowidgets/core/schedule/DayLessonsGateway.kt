package dev.alllexey.itmowidgets.core.schedule

import dev.alllexey.itmowidgets.core.lesson.LessonDay
import dev.alllexey.itmowidgets.core.result.AppResult
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate

/**
 * The own lessons of a few days with their changes and sport queues, for Сегодня; implemented by the schedule
 * feature over its cache.
 */
interface DayLessonsGateway {
    /** Each day of [from]..[to] the cache has, in date order; emits again on every cache, change or queue update. */
    fun observe(from: LocalDate, to: LocalDate): Flow<List<LessonDay>>

    /** Fetches the own schedule of [from]..[to] and the sport queues. */
    suspend fun refresh(from: LocalDate, to: LocalDate): AppResult<Unit>
}
