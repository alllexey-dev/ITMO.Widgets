package dev.alllexey.itmowidgets.feature.schedule.presentation

import dev.alllexey.itmowidgets.core.schedule.LessonOccurrence
import dev.alllexey.itmowidgets.core.sport.PendingSportBooking
import dev.alllexey.itmowidgets.feature.schedule.domain.model.DaySchedule
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import kotlin.time.toKotlinInstant
import kotlinx.datetime.toJavaLocalDate
import kotlinx.datetime.toKotlinTimeZone
import kotlinx.datetime.toLocalDateTime

/** Screen projection; widgets have their own projection, and neither changes the official cache. */
data class ScheduleDisplayDay(
    val date: LocalDate,
    val officialDay: DaySchedule?,
    val pendingSport: List<PendingSportBooking> = emptyList(),
    /** Lessons of this day with a schedule change of the last 30 days, by `pair_id`. */
    val changedPairIds: Set<Long> = emptySet()
)

fun buildScheduleDisplayDays(
    official: List<DaySchedule>,
    pending: List<PendingSportBooking>,
    start: LocalDate,
    end: LocalDate,
    zoneId: ZoneId,
    now: OffsetDateTime,
    changed: Set<LessonOccurrence> = emptySet()
): List<ScheduleDisplayDay> {
    val zone = zoneId.toKotlinTimeZone()
    val upcomingFrom = now.toInstant().toKotlinInstant()
    val pendingByDate = pending.distinctBy { it.queueKind to it.queueId }
        .filter { it.start > upcomingFrom }
        .groupBy { it.start.toLocalDateTime(zone).date.toJavaLocalDate() }
        .filterKeys { !it.isBefore(start) && !it.isAfter(end) }
    val officialByDate = official.associateBy { it.date }
    val changedByDate = changed.groupBy({ it.date.toJavaLocalDate() }) { it.pairId }
    return (officialByDate.keys + pendingByDate.keys).sorted().map { date ->
        ScheduleDisplayDay(
            date,
            officialByDate[date],
            pendingByDate[date].orEmpty().sortedWith(
                compareBy<PendingSportBooking> { it.start }.thenBy { it.queueKind }.thenBy { it.queueId }
            ),
            changedByDate[date].orEmpty().toSet()
        )
    }
}
