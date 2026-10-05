package dev.alllexey.itmowidgets.feature.schedule.data

import dev.alllexey.itmowidgets.core.schedule.SubjectLesson
import dev.alllexey.itmowidgets.core.schedule.SubjectLessonsGateway
import dev.alllexey.itmowidgets.feature.schedule.domain.ScheduleRepository
import dev.alllexey.itmowidgets.feature.schedule.domain.model.DaySchedule
import dev.alllexey.itmowidgets.feature.schedule.domain.model.Lesson
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate
import kotlinx.datetime.toJavaLocalDate
import kotlinx.datetime.toKotlinLocalDate
import kotlinx.datetime.toKotlinLocalTime

class SubjectLessonsGatewayImpl @Inject constructor(
    private val repository: ScheduleRepository
) : SubjectLessonsGateway {

    override fun observeOwnLessons(start: LocalDate, end: LocalDate): Flow<List<SubjectLesson>> =
        repository.observeScheduleForRange(null, start.toJavaLocalDate(), end.toJavaLocalDate()).map { days ->
            days.sortedBy(DaySchedule::date).flatMap { day ->
                day.lessons
                    .filter { it.flowTypeId == ACADEMIC_FLOW }
                    .sortedBy(Lesson::start)
                    .map { it.toSubjectLesson(day.date) }
            }
        }

    private fun Lesson.toSubjectLesson(date: java.time.LocalDate) = SubjectLesson(
        pairId = pairId,
        date = date.toKotlinLocalDate(),
        start = start.toKotlinLocalTime(),
        end = end.toKotlinLocalTime(),
        typeId = typeId.raw,
        type = type,
        subjectId = subjectId,
        subjectName = subjectName,
        flowId = flowId,
        teacherIsu = teacherIsu,
        teacherFio = teacherFio,
        room = room?.raw,
        building = building?.raw,
        formatId = formatId
    )

    private companion object {
        /** MyITMO flow types: 2 academic pairs, 3 sport, 5 room bookings. */
        const val ACADEMIC_FLOW = 2
    }
}
