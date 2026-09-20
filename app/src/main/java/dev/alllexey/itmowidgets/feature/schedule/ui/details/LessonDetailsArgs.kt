package dev.alllexey.itmowidgets.feature.schedule.ui.details

import dev.alllexey.itmowidgets.feature.schedule.domain.model.Lesson
import java.io.Serializable
import java.time.LocalDate

/** Everything the sheet shows without Backend; times and the date travel as ISO strings. */
data class LessonDetailsArgs(
    val pairId: Long,
    val date: String,
    val subjectName: String,
    val typeId: Int,
    val format: String,
    val start: String,
    val end: String,
    val teacherFio: String?,
    val teacherIsu: Long?,
    val room: String?,
    val building: String?,
    val buildingId: Int?,
    val mainBuildingId: Int?,
    val note: String?,
    val zoomUrl: String?,
    val zoomPassword: String?,
    val zoomInfo: String?
) : Serializable

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
