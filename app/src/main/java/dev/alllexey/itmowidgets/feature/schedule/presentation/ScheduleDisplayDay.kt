package dev.alllexey.itmowidgets.feature.schedule.presentation

import dev.alllexey.itmowidgets.core.schedule.LessonOccurrence
import dev.alllexey.itmowidgets.core.sport.PendingSportBooking
import dev.alllexey.itmowidgets.feature.schedule.domain.model.DaySchedule
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
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
    timeZone: TimeZone,
    now: Instant,
    changed: Set<LessonOccurrence> = emptySet()
): List<ScheduleDisplayDay> {
    val pendingByDate = pending.distinctBy { it.queueKind to it.queueId }
        .filter { it.start > now }
        .groupBy { it.start.toLocalDateTime(timeZone).date }
        .filterKeys { it in start..end }
    val officialByDate = official.associateBy { it.date }
    val changedByDate = changed.groupBy({ it.date }) { it.pairId }
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
