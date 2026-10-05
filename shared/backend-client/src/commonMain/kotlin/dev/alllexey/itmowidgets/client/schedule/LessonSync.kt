package dev.alllexey.itmowidgets.client.schedule

import dev.alllexey.itmowidgets.client.json.IsoLocalDateSerializer
import dev.alllexey.itmowidgets.client.json.IsoLocalTimeSerializer
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.serialization.Serializable

/** The viewer's lessons between [from] and [to] inclusive; every lesson must lie inside the range. */
@Serializable
data class LessonSyncRequest(
    val lessons: List<LessonDto>,
    @Serializable(with = IsoLocalDateSerializer::class) val from: LocalDate,
    @Serializable(with = IsoLocalDateSerializer::class) val to: LocalDate,
)

/**
 * One MyITMO lesson occurrence as the app uploads and Backend returns it; the numeric IDs are MyITMO's and the
 * strings are MyITMO's Russian text. Times are written as `08:20` (seconds only when not zero); Backend answers
 * `08:20:00`, which reads the same. The mapping from the app's MyITMO lesson, with its fallbacks, lives in the app.
 */
@Serializable
data class LessonDto(
    val pairId: Long,
    @Serializable(with = IsoLocalDateSerializer::class) val date: LocalDate,
    @Serializable(with = IsoLocalTimeSerializer::class) val start: LocalTime,
    @Serializable(with = IsoLocalTimeSerializer::class) val end: LocalTime,
    /** MyITMO `workType`. */
    val type: String,
    /** MyITMO `workTypeId`: 1 lecture, 2 lab, 3 practice, 5 exam, 6 credit, 10 consultation, 11 sport. */
    val typeId: Int,
    val note: String?,
    val subjectName: String,
    val subjectId: Long,
    val groupName: String,
    val flowId: Long,
    /** 2 lessons, 3 sport, 5 room booking. */
    val flowTypeId: Int,
    val teacherIsu: Long?,
    val teacherFio: String?,
    val room: String?,
    val building: String?,
    val buildingId: Int?,
    /** 13 Kronverksky, 273 Lomonosova, 5 Vyazemsky, 319 virtual rooms. */
    val mainBuildingId: Int?,
    val format: String,
    /** 1 in person, 2 hybrid, 3 remote. */
    val formatId: Int,
)
