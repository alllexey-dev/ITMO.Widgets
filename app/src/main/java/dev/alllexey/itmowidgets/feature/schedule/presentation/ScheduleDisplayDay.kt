package dev.alllexey.itmowidgets.feature.schedule.presentation

import dev.alllexey.itmowidgets.core.sport.PendingSportBooking
import dev.alllexey.itmowidgets.feature.schedule.domain.model.DaySchedule
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId

/** Screen projection; widgets have their own projection, and neither changes the official cache. */
data class ScheduleDisplayDay(
    val date: LocalDate,
    val officialDay: DaySchedule?,
    val pendingSport: List<PendingSportBooking> = emptyList()
)

fun buildScheduleDisplayDays(
    official: List<DaySchedule>,
    pending: List<PendingSportBooking>,
    start: LocalDate,
    end: LocalDate,
    zoneId: ZoneId,
    now: OffsetDateTime
): List<ScheduleDisplayDay> {
    val pendingByDate = pending.distinctBy { it.queueKind to it.queueId }
        .map { it.copy(
            start = it.start.atZoneSameInstant(zoneId).toOffsetDateTime(),
            end = it.end.atZoneSameInstant(zoneId).toOffsetDateTime()
        ) }
        .filter { it.start.isAfter(now) && !it.start.toLocalDate().isBefore(start) && !it.start.toLocalDate().isAfter(end) }
        .groupBy { it.start.toLocalDate() }
    val officialByDate = official.associateBy { it.date }
    return (officialByDate.keys + pendingByDate.keys).sorted().map { date ->
        ScheduleDisplayDay(date, officialByDate[date], pendingByDate[date].orEmpty().sortedWith(
            compareBy<PendingSportBooking> { it.start }.thenBy { it.queueKind }.thenBy { it.queueId }
        ))
    }
}
