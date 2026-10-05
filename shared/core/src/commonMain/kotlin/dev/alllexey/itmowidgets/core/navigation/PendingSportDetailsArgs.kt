package dev.alllexey.itmowidgets.core.navigation

import dev.alllexey.itmowidgets.core.sport.PendingSportBooking
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.offsetAt
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.Serializable
import kotlin.time.Instant

/** A queued or predicted sport booking as the schedule shows it; times travel as ISO strings. */
@Serializable
data class PendingSportDetailsArgs(
    val lessonId: Long,
    val sectionName: String,
    val autoSign: Boolean,
    val isPrediction: Boolean,
    val start: String,
    val end: String,
    val teacherFio: String,
    val roomName: String,
    val teacherIsu: Int? = null
)

/** [zone] is the academic zone; the times are written at its offset, as 2.2 wrote them. */
fun PendingSportBooking.toDetailsArgs(zone: TimeZone) = PendingSportDetailsArgs(
    lessonId = lessonId,
    sectionName = sectionName,
    autoSign = queueKind == PendingSportBooking.QueueKind.AUTO,
    isPrediction = isPrediction,
    start = start.offsetDateTimeText(zone),
    end = end.offsetDateTimeText(zone),
    teacherFio = teacherFio,
    roomName = roomName,
    teacherIsu = teacherIsu
)

/**
 * `OffsetDateTime.ofInstant(this, zone).toString()`: "2026-10-06T10:00+03:00", seconds only when not zero. Never
 * `Instant.toString()`, which is UTC and would move the times the sheet shows by the offset.
 */
private fun Instant.offsetDateTimeText(zone: TimeZone): String {
    val local = toLocalDateTime(zone)
    return "${local.date}T${local.time.isoText()}${zone.offsetAt(this)}"
}

/** `java.time.LocalTime.toString()`: seconds and the fraction only when not zero. */
private fun LocalTime.isoText(): String = buildString {
    append(hour.twoDigits()).append(':').append(minute.twoDigits())
    if (second == 0 && nanosecond == 0) return@buildString
    append(':').append(second.twoDigits())
    if (nanosecond == 0) return@buildString
    val digits = when {
        nanosecond % 1_000_000 == 0 -> 3
        nanosecond % 1_000 == 0 -> 6
        else -> 9
    }
    append('.').append(nanosecond.toString().padStart(9, '0').take(digits))
}

private fun Int.twoDigits(): String = toString().padStart(2, '0')
