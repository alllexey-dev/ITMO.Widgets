package dev.alllexey.itmowidgets.feature.schedule.data.mapper

import dev.alllexey.itmowidgets.core.model.LessonDto
import dev.alllexey.itmowidgets.feature.schedule.domain.model.Building
import dev.alllexey.itmowidgets.feature.schedule.domain.model.Lesson
import dev.alllexey.itmowidgets.feature.schedule.domain.model.Room
import java.time.LocalDate
import java.time.LocalTime

fun api.myitmo.model.schedule.Lesson.toModel(): Lesson {
    return Lesson(
        pairId = pairId,
        start = LocalTime.parse(timeStart),
        end = LocalTime.parse(timeEnd),
        type = workType,
        typeId = Lesson.TypeId(workTypeId),
        note = note?.trim(),
        subjectName = subject.orEmpty().trim(),
        subjectId = subjectId,
        groupName = group.orEmpty().trim(),
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
        groupName = groupName.orEmpty().trim(),
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

fun Lesson.toDto(date: LocalDate): LessonDto {
    return LessonDto(
        pairId = pairId,
        date = date,
        start = start,
        end = end,
        type = type,
        typeId = typeId.raw,
        note = note,
        subjectName = subjectName,
        subjectId = subjectId,
        groupName = groupName,
        flowId = flowId,
        flowTypeId = flowTypeId,
        teacherIsu = teacherIsu,
        teacherFio = teacherFio,
        room = room?.raw,
        building = building?.raw,
        buildingId = buildingId,
        mainBuildingId = mainBuildingId,
        format = format,
        formatId = formatId
    )
}
