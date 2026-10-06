package dev.alllexey.itmowidgets.feature.schedule.data.mapper

import dev.alllexey.itmoapi.myitmo.schedule.Lesson as MyItmoLesson
import dev.alllexey.itmoapi.myitmo.schedule.Schedule as MyItmoDay
import dev.alllexey.itmowidgets.client.schedule.LessonDto
import dev.alllexey.itmowidgets.feature.schedule.domain.model.Building
import dev.alllexey.itmowidgets.feature.schedule.domain.model.DaySchedule
import dev.alllexey.itmowidgets.feature.schedule.domain.model.Lesson
import dev.alllexey.itmowidgets.feature.schedule.domain.model.Room
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

/**
 * The subject name uploaded for a MyITMO lesson without one. A wire value Backend stores and shows to friends, not
 * app text: byte-identical to Core 1.x `Lesson.toDto` (`utils/Extensions.kt`), so it is not a string resource.
 */
private const val UNKNOWN_SUBJECT_NAME = "Неизвестный предмет"

fun MyItmoDay.toModel(): DaySchedule = DaySchedule(
    dayNumber = dayNumber,
    weekNumber = weekNumber,
    date = date,
    note = note,
    lessons = lessons.map { it.toModel() }
)

fun MyItmoLesson.toModel(): Lesson {
    return Lesson(
        pairId = pairId,
        start = LocalTime.parse(timeStart),
        end = LocalTime.parse(timeEnd),
        type = workType,
        typeId = Lesson.TypeId(workTypeId),
        note = note?.trim(),
        subjectName = subject.orEmpty().trim(),
        subjectId = subjectId,
        groupName = group.trim(),
        flowId = flowId.toLong(),
        flowTypeId = flowTypeId,
        teacherIsu = teacherId,
        teacherFio = teacherName?.trim(),
        room = room?.trim()?.takeIf(String::isNotEmpty)?.let(::Room),
        building = building?.trim()?.takeIf(String::isNotEmpty)?.let(::Building),
        buildingId = bldId,
        mainBuildingId = mainBldId,
        format = format,
        formatId = formatId,
        zoomInfo = zoomInfo,
        zoomUrl = zoomUrl,
        zoomPassword = zoomPassword
    )
}

/** The lesson as uploaded to Backend: MyITMO's values untrimmed, as Core 1.x sent them. */
fun MyItmoLesson.toSyncDto(date: LocalDate): LessonDto {
    return LessonDto(
        pairId = pairId,
        date = date,
        start = LocalTime.parse(timeStart),
        end = LocalTime.parse(timeEnd),
        type = workType,
        typeId = workTypeId,
        note = note,
        subjectName = subject ?: UNKNOWN_SUBJECT_NAME,
        subjectId = subjectId,
        groupName = group,
        flowId = flowId.toLong(),
        flowTypeId = flowTypeId,
        teacherIsu = teacherId,
        teacherFio = teacherName,
        room = room,
        building = building,
        buildingId = bldId,
        mainBuildingId = mainBldId,
        format = format,
        formatId = formatId
    )
}

fun LessonDto.toModel(): Lesson {
    return Lesson(
        pairId = pairId,
        start = start,
        end = end,
        type = type,
        typeId = Lesson.TypeId(typeId),
        note = note?.trim(),
        subjectName = subjectName.trim(),
        subjectId = subjectId,
        groupName = groupName.trim(),
        flowId = flowId,
        flowTypeId = flowTypeId,
        teacherIsu = teacherIsu,
        teacherFio = teacherFio?.trim(),
        room = room?.trim()?.takeIf(String::isNotEmpty)?.let(::Room),
        building = building?.trim()?.takeIf(String::isNotEmpty)?.let(::Building),
        buildingId = buildingId,
        mainBuildingId = mainBuildingId,
        format = format,
        formatId = formatId,
        zoomUrl = null,
        zoomPassword = null,
        zoomInfo = null
    )
}
