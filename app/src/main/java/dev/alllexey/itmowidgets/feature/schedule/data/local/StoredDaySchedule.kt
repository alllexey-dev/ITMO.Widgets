package dev.alllexey.itmowidgets.feature.schedule.data.local

import dev.alllexey.itmowidgets.feature.schedule.domain.model.Building
import dev.alllexey.itmowidgets.feature.schedule.domain.model.DaySchedule
import dev.alllexey.itmowidgets.feature.schedule.domain.model.Lesson
import dev.alllexey.itmowidgets.feature.schedule.domain.model.Room
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.serialization.Serializable

/**
 * One cached day on disk, `schedule_cache/<isu|default>_<date>.json` gzipped, as 2.2 wrote it: [data] is a second JSON
 * document, a [StoredDaySchedule]; [userIsu] is absent for the signed-in account. No format field: a file that does
 * not decode is a cache miss.
 */
@Serializable
internal data class StoredCacheEntry(
    val userIsu: Int? = null,
    val date: String,
    val timestamp: Long,
    val data: String
)

/** A [DaySchedule] with ISO dates; the fields 2.2 wrote by reflecting the domain class. */
@Serializable
internal data class StoredDaySchedule(
    val dayNumber: Int,
    val weekNumber: Int,
    val date: String,
    val note: String? = null,
    val lessons: List<StoredCachedLesson>
)

/** A [Lesson] with ISO times; [typeId], [room] and [building] are the unwrapped values. */
@Serializable
internal data class StoredCachedLesson(
    val pairId: Long,
    val start: String,
    val end: String,
    val type: String,
    val typeId: Int,
    val note: String? = null,
    val subjectName: String,
    val subjectId: Long,
    val groupName: String,
    val flowId: Long,
    val flowTypeId: Int,
    val teacherIsu: Long? = null,
    val teacherFio: String? = null,
    val room: String? = null,
    val building: String? = null,
    val buildingId: Int? = null,
    val mainBuildingId: Int? = null,
    val format: String,
    val formatId: Int,
    val zoomUrl: String? = null,
    val zoomPassword: String? = null,
    val zoomInfo: String? = null
)

internal fun StoredCacheEntry.toEntry() = CacheEntry(userIsu, LocalDate.parse(date), timestamp, data)

internal fun CacheEntry.toStored() = StoredCacheEntry(userIsu, date.toString(), timestamp, data)

internal fun StoredDaySchedule.toModel() = DaySchedule(
    dayNumber = dayNumber,
    weekNumber = weekNumber,
    date = LocalDate.parse(date),
    note = note,
    lessons = lessons.map { it.toModel() }
)

internal fun DaySchedule.toStored() = StoredDaySchedule(
    dayNumber = dayNumber,
    weekNumber = weekNumber,
    date = date.toString(),
    note = note,
    lessons = lessons.map { it.toStored() }
)

internal fun StoredCachedLesson.toModel() = Lesson(
    pairId = pairId,
    start = LocalTime.parse(start),
    end = LocalTime.parse(end),
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
    room = room?.let(::Room),
    building = building?.let(::Building),
    buildingId = buildingId,
    mainBuildingId = mainBuildingId,
    format = format,
    formatId = formatId,
    zoomUrl = zoomUrl,
    zoomPassword = zoomPassword,
    zoomInfo = zoomInfo
)

internal fun Lesson.toStored() = StoredCachedLesson(
    pairId = pairId,
    start = start.toString(),
    end = end.toString(),
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
    formatId = formatId,
    zoomUrl = zoomUrl,
    zoomPassword = zoomPassword,
    zoomInfo = zoomInfo
)
