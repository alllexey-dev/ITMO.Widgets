package dev.alllexey.itmowidgets.core.schedule

import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate

/**
 * The viewer's academic lessons from the schedule cache, for screens outside the
 * schedule feature. Cache only: refresh a window through [ScheduleRefreshGateway]
 * first when fresh data matters.
 */
interface SubjectLessonsGateway {
    fun observeOwnLessons(start: LocalDate, end: LocalDate): Flow<List<SubjectLesson>>
}
