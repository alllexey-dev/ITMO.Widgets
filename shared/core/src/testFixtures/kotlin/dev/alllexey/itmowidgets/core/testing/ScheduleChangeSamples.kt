package dev.alllexey.itmowidgets.core.testing

import dev.alllexey.itmowidgets.core.schedule.LessonSlot
import dev.alllexey.itmowidgets.core.schedule.ScheduleChange
import dev.alllexey.itmowidgets.core.schedule.ScheduleChangeField
import dev.alllexey.itmowidgets.core.schedule.ScheduleChangeKind
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlin.time.Instant

/** A synthetic change of a lesson moved from Tuesday to Wednesday unless told otherwise. */
fun scheduleChange(
    id: String = "1",
    kind: ScheduleChangeKind = ScheduleChangeKind.UPDATED,
    fields: Set<ScheduleChangeField> = if (kind == ScheduleChangeKind.UPDATED) setOf(ScheduleChangeField.TIME) else emptySet(),
    subject: String = "Математический анализ",
    typeId: Int = 1,
    flowName: String? = "Тестовый поток",
    before: LessonSlot? = if (kind == ScheduleChangeKind.ADDED) null else slot(1, LocalDate(2026, 9, 8)),
    after: LessonSlot? = if (kind == ScheduleChangeKind.CANCELLED) null else slot(1, LocalDate(2026, 9, 9)),
    read: Boolean = false,
    notified: Boolean = false,
    detectedAt: Instant = Instant.parse("2026-09-07T09:00:00Z")
) = ScheduleChange(
    id = id, detectedAt = detectedAt, kind = kind, fields = fields, subjectName = subject, typeId = typeId,
    flowName = flowName, before = before, after = after, read = read, notified = notified
)

fun slot(
    pairId: Long,
    date: LocalDate,
    start: LocalTime = LocalTime(8, 20),
    end: LocalTime = LocalTime.fromSecondOfDay(start.toSecondOfDay() + 90 * 60),
    room: String? = "1506",
    building: String? = "Кронверкский проспект, 49",
    formatId: Int = 1,
    format: String? = "Очный",
    teacherIsu: Long? = 300001,
    teacherName: String? = "Тестовый преподаватель"
) = LessonSlot(
    pairId = pairId, date = date, start = start, end = end, room = room, building = building, formatId = formatId,
    format = format, teacherIsu = teacherIsu, teacherName = teacherName
)
