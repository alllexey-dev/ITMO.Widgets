package dev.alllexey.itmowidgets.feature.schedule.domain

import dev.alllexey.itmowidgets.core.lesson.LessonDay
import dev.alllexey.itmowidgets.core.result.AppResult
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate

/**
 * The days of a schedule as every lesson list shows them: the own one ([userIsu] null) with its found changes and
 * sport queues, or another user's with `Вы` on the lessons the viewer attends too.
 */
interface ScheduleDays {
    /** Each day of [from]..[to] the cache has, in date order; emits again on every cache, change or queue update. */
    fun observe(userIsu: Int?, from: LocalDate, to: LocalDate): Flow<List<LessonDay>>

    /** Fetches [userIsu]'s schedule of [from]..[to]; for the own one the sport queues too. */
    suspend fun refresh(userIsu: Int?, from: LocalDate, to: LocalDate): AppResult<Unit>
}

/** The days of a teacher's MyITMO timetable, [userIsu] being the teacher's: the feed of `AppRoutes.TeacherSchedule`. */
interface TeacherScheduleDays : ScheduleDays
