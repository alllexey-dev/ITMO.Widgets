package dev.alllexey.itmowidgets.core.testing

import dev.alllexey.itmowidgets.core.schedule.SubjectLesson
import dev.alllexey.itmowidgets.core.schedule.SubjectLessonsGateway
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

class FakeSubjectLessonsGateway : SubjectLessonsGateway {
    val lessons = MutableStateFlow<List<SubjectLesson>>(emptyList())
    val observedWindows = mutableListOf<Pair<LocalDate, LocalDate>>()
    override fun observeOwnLessons(start: LocalDate, end: LocalDate): Flow<List<SubjectLesson>> {
        observedWindows += start to end
        return lessons.map { list -> list.filter { it.date in start..end } }
    }
}

fun subjectLesson(
    pairId: Long, date: String, subjectId: Long = 1L, name: String = "Тестовый предмет", typeId: Int = 1,
    teacherIsu: Long? = 300001, teacherFio: String? = "Тестовый преподаватель", flowId: Long = 10L, start: String = "09:30"
) = SubjectLesson(
    pairId = pairId, date = LocalDate.parse(date), start = LocalTime.parse(start),
    end = LocalTime.fromSecondOfDay(LocalTime.parse(start).toSecondOfDay() + LESSON_SECONDS),
    typeId = typeId, type = "Лекция", subjectId = subjectId, subjectName = name, flowId = flowId, teacherIsu = teacherIsu,
    teacherFio = teacherFio, room = "1506", building = "Кронверкский проспект, 49", formatId = 1
)

private const val LESSON_SECONDS = 90 * 60
