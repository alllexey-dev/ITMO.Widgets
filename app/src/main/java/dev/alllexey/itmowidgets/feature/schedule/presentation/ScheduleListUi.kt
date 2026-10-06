package dev.alllexey.itmowidgets.feature.schedule.presentation

import dev.alllexey.itmowidgets.core.sport.PendingSportBooking
import dev.alllexey.itmowidgets.feature.schedule.domain.model.Lesson
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

/**
 * One day card of the schedule list at one academic instant. Typed values only: the UI turns them into texts and
 * colours, so a time tick that changes nothing visible yields an equal value.
 */
data class ScheduleDayUi(
    val date: LocalDate,
    val title: ScheduleDayTitle,
    val summary: ScheduleDaySummary,
    val isToday: Boolean,
    /** Past days keep the established fade (0.72) on the whole card. */
    val isPast: Boolean,
    /** Ordered by start; never empty: a day without rows shows [ScheduleRowUi.NoLessons]. */
    val rows: List<ScheduleRowUi>
)

/** The card names the weekday and shows the date under it. */
data class ScheduleDayTitle(val weekday: DayOfWeek, val date: LocalDate)

/** The pill next to the title. Pending sport rows never count as lessons. */
sealed interface ScheduleDaySummary {
    data class Lessons(val count: Int) : ScheduleDaySummary

    /** No official lessons, only pending sport auto-sign rows. */
    data object AutoSignOnly : ScheduleDaySummary

    data object NoLessons : ScheduleDaySummary
}

sealed interface ScheduleRowUi {
    /** [changed]: a schedule change of the last 30 days touches this lesson. */
    data class LessonRow(
        val lesson: Lesson,
        val state: ScheduleLessonState,
        val isLast: Boolean,
        val changed: Boolean
    ) : ScheduleRowUi

    /** [start] and [end] are the booking's academic wall-clock times. */
    data class PendingSportRow(
        val booking: PendingSportBooking,
        val start: LocalTime,
        val end: LocalTime,
        val status: PendingSportStatus,
        val isLast: Boolean
    ) : ScheduleRowUi

    /** A gap of more than an hour between official lessons that no pending sport row fills. */
    data class BreakRow(val from: LocalTime, val to: LocalTime) : ScheduleRowUi {
        val minutes: Int get() = (to.toSecondOfDay() - from.toSecondOfDay()) / 60
    }

    data object NoLessons : ScheduleRowUi
}

enum class ScheduleLessonState {
    UPCOMING,
    NEXT,
    CURRENT,
    COMPLETED
}

/** The status line of an auto-sign row: a predicted lesson, or one waiting for a free place. */
enum class PendingSportStatus {
    PREDICTION,
    WAITING
}
