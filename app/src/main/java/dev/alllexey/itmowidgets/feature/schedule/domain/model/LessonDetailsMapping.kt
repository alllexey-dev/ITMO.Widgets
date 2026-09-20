package dev.alllexey.itmowidgets.feature.schedule.domain.model

import dev.alllexey.itmowidgets.core.navigation.LessonDetailsArgs
import java.time.LocalDate

fun Lesson.toDetailsArgs(date: LocalDate) = LessonDetailsArgs(
    pairId = pairId,
    date = date.toString(),
    subjectName = subjectName,
    typeId = typeId.raw,
    format = format,
    start = start.toString(),
    end = end.toString(),
    teacherFio = teacherFio?.takeIf { it.isNotBlank() },
    teacherIsu = teacherIsu,
    room = room?.raw?.takeIf { it.isNotBlank() },
    building = building?.raw?.takeIf { it.isNotBlank() },
    buildingId = buildingId,
    mainBuildingId = mainBuildingId,
    note = note?.takeIf { it.isNotBlank() },
    zoomUrl = zoomUrl?.takeIf { it.isNotBlank() },
    zoomPassword = zoomPassword?.takeIf { it.isNotBlank() },
    zoomInfo = zoomInfo?.takeIf { it.isNotBlank() }
)
