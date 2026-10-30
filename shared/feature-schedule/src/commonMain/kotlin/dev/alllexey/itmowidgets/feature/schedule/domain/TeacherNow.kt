package dev.alllexey.itmowidgets.feature.schedule.domain

import dev.alllexey.itmowidgets.core.schedule.TeacherNowLesson
import dev.alllexey.itmowidgets.feature.schedule.domain.model.Lesson
import kotlinx.datetime.LocalTime

/**
 * The lesson of a teacher's day [lessons] that runs at [at], else the next one to start, else null. Room bookings are
 * not teaching and are left out; lessons sharing time, subject and place merge into one with all their groups.
 */
fun teacherNow(lessons: List<Lesson>, at: LocalTime): TeacherNowLesson? {
    val taught = taughtLessons(lessons)
    val current = taught.firstOrNull { it.first().start <= at && at < it.first().end }
    val chosen = current ?: taught.firstOrNull { it.first().start > at } ?: return null
    val lesson = chosen.first()
    return TeacherNowLesson(
        start = lesson.start,
        end = lesson.end,
        subject = lesson.subjectName,
        typeId = lesson.kindId,
        room = lesson.room?.raw,
        building = lesson.building?.raw,
        online = lesson.formatId == REMOTE_FORMAT,
        groups = chosen.groupNames(),
        isNow = current != null,
    )
}

/**
 * A teacher's day [lessons] as they teach it, by start: room bookings left out, lessons sharing time, subject and
 * place for several groups together.
 */
fun taughtLessons(lessons: List<Lesson>): List<List<Lesson>> = lessons.filter { it.flowTypeId != ROOM_BOOKING_FLOW }
    .groupBy { listOf(it.start, it.end, it.subjectName, it.room, it.building) }
    .values
    .sortedBy { it.first().start }

/** The groups of lessons taught together, each once. */
fun List<Lesson>.groupNames(): List<String> = map { it.groupName }.filter(String::isNotEmpty).distinct()

private const val ROOM_BOOKING_FLOW = 5
private const val REMOTE_FORMAT = 3
