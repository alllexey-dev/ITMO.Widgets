package dev.alllexey.itmowidgets.core.testing

import dev.alllexey.itmowidgets.core.lesson.LessonDay
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.schedule.DayLessonsGateway
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate

/** The cached days of one test in [days]; [refresh] counts its calls and changes nothing. */
class FakeDayLessonsGateway(days: List<LessonDay> = emptyList()) : DayLessonsGateway {
    val days = MutableStateFlow(days)
    var refreshes = 0

    override fun observe(from: LocalDate, to: LocalDate): Flow<List<LessonDay>> =
        days.map { list -> list.filter { it.date in from..to } }

    override suspend fun refresh(from: LocalDate, to: LocalDate): AppResult<Unit> {
        refreshes++
        return AppResult.Success(Unit)
    }
}
