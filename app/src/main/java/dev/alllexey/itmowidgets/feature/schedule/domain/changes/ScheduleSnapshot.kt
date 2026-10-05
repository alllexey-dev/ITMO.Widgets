package dev.alllexey.itmowidgets.feature.schedule.domain.changes

import dev.alllexey.itmowidgets.core.schedule.LessonSlot
import dev.alllexey.itmowidgets.feature.schedule.domain.model.DaySchedule
import dev.alllexey.itmowidgets.feature.schedule.domain.model.Lesson
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime

/** My ITMO's flow type of study lessons; sport (3) and room bookings (5) are not compared. */
const val ACADEMIC_FLOW = 2

/** One academic lesson as the change check remembers it; empty strings are already `null`. */
data class SnapshotLesson(
    val pairId: Long,
    val date: LocalDate,
    val start: LocalTime,
    val end: LocalTime,
    val subjectId: Long,
    val subjectName: String,
    val typeId: Int,
    val flowId: Long,
    val flowName: String?,
    val teacherIsu: Long?,
    val teacherName: String?,
    val room: String?,
    val building: String?,
    val formatId: Int,
    val format: String?
) {
    val startsAt: LocalDateTime get() = LocalDateTime(date, start)
    val endsAt: LocalDateTime get() = LocalDateTime(date, end)

    fun slot() = LessonSlot(
        pairId = pairId,
        date = date,
        start = start,
        end = end,
        room = room,
        building = building,
        formatId = formatId,
        format = format,
        teacherIsu = teacherIsu,
        teacherName = teacherName
    )
}

/** The academic lessons of the window [start]..[end], both ends included. */
data class ScheduleSnapshot(val start: LocalDate, val end: LocalDate, val lessons: List<SnapshotLesson>)

/**
 * Keeps the academic lessons dated inside [start]..[end]. A `pairId` names one lesson, so a positive one that repeats
 * is kept once, at its earliest slot.
 */
fun List<DaySchedule>.academicSnapshot(start: LocalDate, end: LocalDate): ScheduleSnapshot {
    val lessons = asSequence()
        .filter { day -> day.date in start..end }
        .flatMap { day -> day.lessons.asSequence().filter { it.flowTypeId == ACADEMIC_FLOW }.map { it.toSnapshot(day.date) } }
        .sortedWith(compareBy<SnapshotLesson>({ it.date }, { it.start }, { it.end }, { it.pairId }))
        .toList()
    val seen = mutableSetOf<Long>()
    return ScheduleSnapshot(start, end, lessons.filter { it.pairId <= 0 || seen.add(it.pairId) })
}

private fun Lesson.toSnapshot(date: LocalDate) = SnapshotLesson(
    pairId = pairId,
    date = date,
    start = start,
    end = end,
    subjectId = subjectId,
    subjectName = subjectName.trim(),
    typeId = typeId.raw,
    flowId = flowId,
    flowName = groupName.orNull(),
    teacherIsu = teacherIsu,
    teacherName = teacherFio?.orNull(),
    room = room?.raw?.orNull(),
    building = building?.raw?.orNull(),
    formatId = formatId,
    format = format.orNull()
)

private fun String.orNull(): String? = trim().takeIf(String::isNotEmpty)
