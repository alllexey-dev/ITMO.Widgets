package dev.alllexey.itmowidgets.feature.schedule.presentation.feed

import dev.alllexey.itmowidgets.core.lesson.TimelineItem
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.text.UiText
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

/** Another user whose schedule is shown: [group] is the one to name beside them (`M3204`). */
data class SelectedUser(val isu: Int, val name: String, val avatar: String?, val group: String? = null)

/**
 * The schedule feed (docs/features/schedule.md): the root header ([title] `Сентябрь` with [subtitle]
 * `2-я неделя`, or `Расписание` over a friend's name), the week strip ([weeks], [selectedWeek] and the
 * [selected] day, the one at the top of the feed) and the [body]. [monthMenu]: the title opens the month sheet;
 * [canPickFriend]: the friends button; [friend]: the tab shows that friend's schedule, `Вернуться к своему`;
 * [offWeek]: the feed is off the current week, `Сегодня`. [jump] asks the view to scroll once to a day; while
 * [loading] its day may still arrive.
 */
data class ScheduleFeedUiState(
    val title: UiText,
    val subtitle: UiText?,
    val monthMenu: Boolean,
    val canPickFriend: Boolean,
    val friend: SelectedUser?,
    val weeks: List<StripWeek>,
    val selectedWeek: Int,
    val selected: LocalDate,
    val offWeek: Boolean,
    val body: ScheduleFeedBody,
    val refreshing: Boolean,
    val jump: FeedJump?,
    val loading: Boolean = false,
)

/** One page of the week strip, Monday to Sunday. */
data class StripWeek(val monday: LocalDate, val days: List<StripDay>)

/**
 * A day of the strip: [weekday] `пн`, [number] `7`, a dot per lesson up to [lessons], [changed] a change on one of
 * them; [today] and [past] colour it.
 */
data class StripDay(
    val date: LocalDate,
    val weekday: String,
    val number: String,
    val lessons: Int,
    val changed: Boolean,
    val today: Boolean,
    val past: Boolean,
)

/** Scroll to [date]; [id] tells two jumps to the same day apart. */
data class FeedJump(val date: LocalDate, val id: Int)

sealed interface ScheduleFeedBody {
    /** Nothing cached and the first refresh still runs. */
    data object Loading : ScheduleFeedBody

    /** Nothing to show: the refresh failed, or another user's schedule is closed to the viewer. */
    data class Failed(val error: AppError) : ScheduleFeedBody

    /** The feed's rows in date order. */
    data class Days(val rows: List<FeedRow>) : ScheduleFeedBody {
        /** The row that shows [date], else the first after it, else the last; -1 without rows. */
        fun indexOf(date: LocalDate): Int {
            val exact = rows.indexOfFirst { date in it.dates }
            if (exact >= 0) return exact
            val after = rows.indexOfFirst { it.date > date }
            return if (after >= 0) after else rows.lastIndex
        }
    }
}

/** One row of the feed; [date] is the day it belongs to, [key] stays the same across updates. */
sealed interface FeedRow {
    val date: LocalDate
    val key: String

    /** The days this row stands for. */
    val dates: ClosedRange<LocalDate> get() = date..date

    /** `2-я неделя` (MyITMO's week number) before a week; [date] is its Monday. */
    data class WeekLabel(override val date: LocalDate, val text: UiText) : FeedRow {
        override val key get() = "week-$date"
    }

    /**
     * The sticky header of a day with lessons: [title] `Понедельник, 7 сентября`, `сегодня`, [count] `5 пар`; [past]
     * before the academic today, dimmed as the strip's past days.
     */
    data class DayHeader(
        override val date: LocalDate,
        val title: String,
        val today: Boolean,
        val past: Boolean,
        val count: UiText,
    ) : FeedRow {
        override val key get() = "head-$date"
    }

    /** A day's lessons on the timeline; [showNow] for today. */
    data class Lessons(override val date: LocalDate, val items: List<TimelineItem>, val showNow: Boolean) : FeedRow {
        override val key get() = "day-$date"
    }

    /** A weekday without lessons: `Вт, 15 сентября — пар нет`. */
    data class NoLessons(override val date: LocalDate, val text: UiText) : FeedRow {
        override val key get() = "free-$date"
    }

    /** A Saturday and Sunday without lessons in one row: `Выходные, 12–13 сентября`; [date] is the Saturday. */
    data class Weekend(override val date: LocalDate, val text: UiText) : FeedRow {
        override val key get() = "weekend-$date"
        override val dates get() = date..date.plus(1, DateTimeUnit.DAY)
    }
}

/** One-shot effects of the feed. */
sealed interface ScheduleFeedEvent {
    /** A refresh failed behind content: a snackbar with `Повторить`. */
    data class ShowError(val error: AppError) : ScheduleFeedEvent
}
