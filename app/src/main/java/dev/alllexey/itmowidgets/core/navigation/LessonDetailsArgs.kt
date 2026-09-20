package dev.alllexey.itmowidgets.core.navigation

import java.io.Serializable

/** Everything the lesson sheet shows without Backend; times and the date travel as ISO strings. */
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
