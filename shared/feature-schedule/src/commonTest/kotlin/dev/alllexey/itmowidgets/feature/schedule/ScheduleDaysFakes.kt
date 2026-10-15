package dev.alllexey.itmowidgets.feature.schedule

import dev.alllexey.itmowidgets.core.lesson.DayLesson
import dev.alllexey.itmowidgets.core.lesson.LessonDay
import dev.alllexey.itmowidgets.core.lesson.LessonTarget
import dev.alllexey.itmowidgets.core.navigation.LessonDetailsArgs
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.feature.schedule.domain.ScheduleDays
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

/** One refresh of [FakeScheduleDays]: whose and which days. */
internal data class DaysRefresh(val userIsu: Int?, val from: LocalDate, val to: LocalDate)

/**
 * Days per user (null: the own) that observers read in their range; refreshes are recorded in [refreshes] and answer
 * [result] for the own schedule, [results] for another user's.
 */
internal class FakeScheduleDays(vararg own: LessonDay) : ScheduleDays {
    private val byUser = mutableMapOf<Int?, MutableStateFlow<List<LessonDay>>>()
    val refreshes = mutableListOf<DaysRefresh>()
    var onRefresh: suspend (DaysRefresh) -> Unit = {}
    var result: AppResult<Unit> = AppResult.Success(Unit)
    val results = mutableMapOf<Int, AppResult<Unit>>()

    init {
        days(null).value = own.toList()
    }

    fun days(userIsu: Int?): MutableStateFlow<List<LessonDay>> = byUser.getOrPut(userIsu) { MutableStateFlow(emptyList()) }

    override fun observe(userIsu: Int?, from: LocalDate, to: LocalDate): Flow<List<LessonDay>> =
        days(userIsu).map { days -> days.filter { it.date in from..to }.sortedBy { it.date } }

    override suspend fun refresh(userIsu: Int?, from: LocalDate, to: LocalDate): AppResult<Unit> {
        val request = DaysRefresh(userIsu, from, to)
        refreshes += request
        onRefresh(request)
        return userIsu?.let { results[it] } ?: result
    }
}

/** A lesson of the feed's fixtures: [start] and [end] as `10:00`, a timetable target. */
internal fun feedLesson(
    start: String,
    end: String,
    subject: String = "Физика",
    typeId: Int = 3,
    room: String? = "1506",
    building: String? = "Кронверкский пр., 49",
) = DayLesson(
    start = LocalTime.parse(start),
    end = LocalTime.parse(end),
    subject = subject,
    typeId = typeId,
    teacher = "Смирнова Ольга Петровна",
    room = room,
    building = building,
    online = false,
    target = LessonTarget.Lesson(
        LessonDetailsArgs(1, "2026-09-07", subject, typeId, "", start, end, null, null, room, building, null, null, null, null, null, null),
    ),
)
