package dev.alllexey.itmowidgets.feature.schedule.data

import dev.alllexey.itmowidgets.core.schedule.SubjectLesson
import dev.alllexey.itmowidgets.core.schedule.SubjectLessonsGateway
import dev.alllexey.itmowidgets.feature.schedule.domain.ScheduleRepository
import dev.alllexey.itmowidgets.feature.schedule.domain.model.DaySchedule
import dev.alllexey.itmowidgets.feature.schedule.domain.model.Lesson
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class SubjectLessonsGatewayImpl @Inject constructor(
    private val repository: ScheduleRepository
) : SubjectLessonsGateway {

    override fun observeOwnLessons(start: LocalDate, end: LocalDate): Flow<List<SubjectLesson>> =
        repository.observeScheduleForRange(userIsu = null, startDate = start, endDate = end).map { days ->
            days.sortedBy(DaySchedule::date).flatMap { day ->
                day.lessons
                    .filter { it.flowTypeId == ACADEMIC_FLOW }
                    .sortedBy(Lesson::start)
                    .map { it.toSubjectLesson(day.date) }
            }
        }

    private fun Lesson.toSubjectLesson(date: LocalDate) = SubjectLesson(
        pairId = pairId,
        date = date,
        start = start,
        end = end,
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
