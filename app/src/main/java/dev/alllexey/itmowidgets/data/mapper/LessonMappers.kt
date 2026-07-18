package dev.alllexey.itmowidgets.data.mapper

import dev.alllexey.itmowidgets.core.model.LessonDto
import dev.alllexey.itmowidgets.domain.model.schedule.Building
import dev.alllexey.itmowidgets.domain.model.schedule.Lesson
import dev.alllexey.itmowidgets.domain.model.schedule.Room
import java.time.LocalDate
import java.time.LocalTime

fun api.myitmo.model.schedule.Lesson.toModel(): Lesson {
    return Lesson(
        pairId = pairId,
        start = LocalTime.parse(timeStart),
        end = LocalTime.parse(timeEnd),
        type = workType,
        typeId = Lesson.TypeId(workTypeId),
        note = note,
        subjectName = subject ?: "Неизвестный предмет",
        subjectId = subjectId,
        groupName = group,
        flowId = flowId.toLong(),
        flowTypeId = flowTypeId,
        teacherIsu = teacherId,
        teacherFio = teacherName,
        room = room?.let { Room(it) },
        building = building?.let { Building(it) },
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
        note = note,
        subjectName = subjectName,
        subjectId = subjectId,
        groupName = groupName,
        flowId = flowId,
        flowTypeId = flowTypeId,
        teacherIsu = teacherIsu,
        teacherFio = teacherFio,
        room = room?.let { Room(it) },
        building = building?.let { Building(it) },
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
