package dev.alllexey.itmowidgets.client.sport.model

import dev.alllexey.itmowidgets.client.json.WireInstantSerializer
import kotlinx.serialization.Serializable
import kotlin.time.Instant

/**
 * A sport lesson as Backend stores it from the official sport schedule. [start] and [end] are instants: convert
 * them with the academic zone, never the device zone. Codes are raw ISU values, not app categories.
 */
@Serializable
data class SportLessonDto(
    val id: Long,
    val sectionId: Long,
    val sectionName: String,
    /** 1 free attendance, 2 sections with selection. */
    val sectionLevel: Long,
    /** 1 free attendance, 2 section (training), 3 section (intermediate), 4 section (team). */
    val level: Long,
    /** For level 1: 1 open lesson, 2 free attendance, 5 debt, 6 standards, 7 external, 8 additional. */
    val typeId: Long,
    /** Raw venue ID, not a filter category; null for online lessons. */
    val buildingId: Long?,
    val roomName: String,
    @Serializable(with = WireInstantSerializer::class) val start: Instant,
    @Serializable(with = WireInstantSerializer::class) val end: Instant,
    val timeSlotId: Long,
    val teacherIsu: Long,
    val teacherFio: String,
)
