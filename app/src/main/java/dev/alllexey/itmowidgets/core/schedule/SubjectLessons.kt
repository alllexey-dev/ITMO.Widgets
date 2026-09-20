package dev.alllexey.itmowidgets.core.schedule

import java.time.LocalDate
import java.time.LocalTime

/** One academic lesson of the viewer, as the study screens see it; no sport, no room bookings. */
data class SubjectLesson(
    val pairId: Long,
    val date: LocalDate,
    val start: LocalTime,
    val end: LocalTime,
    val typeId: Int,
    val type: String,
    val subjectId: Long,
    val subjectName: String,
    val flowId: Long,
    val teacherIsu: Long?,
    val teacherFio: String?,
    val room: String?,
    val building: String?,
    val formatId: Int
)

/** A subject as the schedule knows it: the id the recordbook may share, and its flows. */
data class ScheduleSubject(
    val subjectId: Long,
    val name: String,
    val flowIds: Set<Long>
)

/** Groups lessons by subject; the first occurrence names the subject. */
fun subjectsIn(lessons: List<SubjectLesson>): List<ScheduleSubject> =
    lessons.groupBy { it.subjectId }.map { (subjectId, group) ->
        ScheduleSubject(subjectId, group.first().subjectName, group.map { it.flowId }.toSet())
    }
