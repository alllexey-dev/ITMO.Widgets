package dev.alllexey.itmowidgets.feature.home

import dev.alllexey.itmowidgets.core.lesson.DayLesson
import dev.alllexey.itmowidgets.core.lesson.LessonChange
import dev.alllexey.itmowidgets.core.lesson.LessonDay
import dev.alllexey.itmowidgets.core.lesson.LessonTarget
import dev.alllexey.itmowidgets.core.lesson.SportMark
import dev.alllexey.itmowidgets.core.navigation.LessonDetailsArgs
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.schedule.DayLessonsGateway
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

/** Cached days in [days]; each refresh answers [result] and is recorded. */
class FakeDayLessonsGateway(vararg initial: LessonDay) : DayLessonsGateway {
    val days = MutableStateFlow(initial.toList())
    var result: AppResult<Unit> = AppResult.Success(Unit)
    val refreshes = mutableListOf<Pair<LocalDate, LocalDate>>()

    override fun observe(from: LocalDate, to: LocalDate): Flow<List<LessonDay>> =
        days.map { all -> all.filter { it.date in from..to } }

    override suspend fun refresh(from: LocalDate, to: LocalDate): AppResult<Unit> {
        refreshes += from to to
        return result
    }
}

fun dayLesson(
    start: String,
    end: String,
    subject: String = "Физика",
    room: String? = "1506",
    online: Boolean = false,
    change: LessonChange? = null,
    sport: SportMark? = null,
) = DayLesson(
    start = LocalTime.parse(start),
    end = LocalTime.parse(end),
    subject = subject,
    typeId = 1,
    teacher = "Смирнова Елена Андреевна",
    room = room,
    building = "Кронверкский пр., 49",
    online = online,
    target = LessonTarget.Lesson(
        LessonDetailsArgs(1, "2026-09-07", subject, 1, "", start, end, null, null, room, null, null, null, null, null, null, null),
    ),
    videoUrl = if (online) "https://example.org/meet" else null,
    change = change,
    sport = sport,
)
