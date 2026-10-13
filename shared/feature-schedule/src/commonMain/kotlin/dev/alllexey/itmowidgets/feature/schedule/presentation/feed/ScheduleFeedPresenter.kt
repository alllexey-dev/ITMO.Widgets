package dev.alllexey.itmowidgets.feature.schedule.presentation.feed

import dev.alllexey.itmowidgets.core.lesson.DayLesson
import dev.alllexey.itmowidgets.core.lesson.DayMoment
import dev.alllexey.itmowidgets.core.lesson.LessonDay
import dev.alllexey.itmowidgets.core.lesson.LessonTimelines
import dev.alllexey.itmowidgets.core.lesson.SportMark
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.text.DateTexts
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.shared.core.CorePlurals
import dev.alllexey.itmowidgets.shared.core.CoreStrings
import dev.alllexey.itmowidgets.shared.feature.schedule.ScheduleStrings
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.format
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/** Whose schedule the feed shows: the own one in the tab, a friend's in the tab, or a user's on a pushed screen. */
sealed interface FeedOwner {
    val user: SelectedUser?

    data object Own : FeedOwner {
        override val user: SelectedUser? get() = null
    }

    data class Friend(override val user: SelectedUser) : FeedOwner

    data class Other(override val user: SelectedUser) : FeedOwner
}

/**
 * What the feed knows at one moment: the [days] the cache has in the [window] (Monday to Sunday), the [known] dates
 * whose absence means no lessons (a refresh answered for them), the [visible] day at the top, how the refreshes went.
 * [denied] is another user's schedule closed to the viewer.
 */
data class FeedInput(
    val now: LocalDateTime,
    val owner: FeedOwner,
    val window: ClosedRange<LocalDate>,
    val days: List<LessonDay>,
    val known: Set<LocalDate>,
    val visible: LocalDate,
    val loading: Boolean,
    val error: AppError?,
    val denied: AppError?,
    val refreshing: Boolean,
    val jump: FeedJump?,
)

/**
 * The schedule feed at one moment (docs/features/schedule.md): every known day of the window in order, a day with
 * lessons as a sticky header over the shared timeline (`LessonTimelines`, the rules of Сегодня), a weekday without
 * them as one short row, a free weekend as one row; the week strip with a dot per lesson; the header of the visible
 * week. Pure.
 */
object ScheduleFeedPresenter {

    fun state(input: FeedInput): ScheduleFeedUiState {
        val today = input.now.date
        val byDate = input.days.associateBy { it.date }
        val weeks = mondays(input.window).map { monday -> strip(monday, byDate, today) }
        val selected = input.visible.coerceIn(input.window.start, input.window.endInclusive)
        val selectedWeek = weeks.indexOfFirst { selected in it.monday..it.monday.plus(SUNDAY_OFFSET, DateTimeUnit.DAY) }
        val hasContent = input.days.isNotEmpty() || input.known.isNotEmpty()
        val body = when {
            input.denied != null -> ScheduleFeedBody.Failed(input.denied)
            hasContent -> ScheduleFeedBody.Days(rows(input, byDate))
            input.loading -> ScheduleFeedBody.Loading
            input.error != null -> ScheduleFeedBody.Failed(input.error)
            else -> ScheduleFeedBody.Days(emptyList())
        }
        val user = input.owner.user
        return ScheduleFeedUiState(
            title = if (user == null) UiText.Dynamic(monthTitle(selected)) else UiText.Res(CoreStrings.tab_schedule),
            subtitle = if (user == null) weekOf(selected, byDate)?.let(::weekLabel) else userLine(user),
            monthMenu = input.owner == FeedOwner.Own,
            canPickFriend = input.owner !is FeedOwner.Other,
            friend = (input.owner as? FeedOwner.Friend)?.user,
            weeks = weeks,
            selectedWeek = selectedWeek.coerceAtLeast(0),
            selected = selected,
            offWeek = monday(selected) != monday(today),
            body = body,
            refreshing = input.refreshing,
            jump = input.jump,
            loading = input.loading,
        )
    }

    /** The Monday of [date]'s week. */
    fun monday(date: LocalDate): LocalDate = date.minus(date.dayOfWeek.isoDayNumber - 1, DateTimeUnit.DAY)

    private fun rows(input: FeedInput, byDate: Map<LocalDate, LessonDay>): List<FeedRow> {
        val today = input.now.date
        val nextDay = nextDayWithLessons(input.days, input.now)
        fun known(date: LocalDate) = date in input.known || date in byDate
        fun lessons(date: LocalDate) = byDate[date]?.lessons.orEmpty()
        return mondays(input.window).flatMap { monday ->
            val dates = (0..SUNDAY_OFFSET).map { monday.plus(it, DateTimeUnit.DAY) }
            val week = buildList {
                var index = 0
                while (index < dates.size) {
                    val date = dates[index]
                    val sunday = dates.getOrNull(index + 1)
                    index++
                    if (!known(date)) continue
                    val day = lessons(date)
                    when {
                        day.isNotEmpty() -> {
                            add(FeedRow.DayHeader(date, dayTitle(date), date == today, count(day)))
                            add(FeedRow.Lessons(date, timeline(day, date, input.now, nextDay), date == today))
                        }
                        date.dayOfWeek == DayOfWeek.SATURDAY && sunday != null && known(sunday) && lessons(sunday).isEmpty() -> {
                            add(FeedRow.Weekend(date, UiText.Res(ScheduleStrings.schedule_weekend, listOf(weekendRange(date, sunday)))))
                            index++
                        }
                        else -> add(FeedRow.NoLessons(date, UiText.Res(ScheduleStrings.schedule_day_free, listOf(shortDay(date)))))
                    }
                }
            }
            val label = weekOf(monday, byDate)?.let { FeedRow.WeekLabel(monday, weekLabel(it)) }
            if (week.isEmpty()) emptyList() else listOfNotNull(label) + week
        }
    }

    private fun timeline(lessons: List<DayLesson>, date: LocalDate, now: LocalDateTime, nextDay: LocalDate?) =
        LessonTimelines.build(
            lessons,
            when {
                date < now.date -> DayMoment.Past
                date == now.date -> DayMoment.Today(now.time)
                else -> DayMoment.Future(firstIsNext = date == nextDay)
            },
            now.date,
        )

    /** The first day after today with a lesson that can be `далее`, once today has none left. */
    private fun nextDayWithLessons(days: List<LessonDay>, now: LocalDateTime): LocalDate? {
        val todayLeft = days.firstOrNull { it.date == now.date }?.lessons.orEmpty().any { it.counts() && it.end > now.time }
        if (todayLeft) return null
        return days.filter { it.date > now.date }.sortedBy { it.date }.firstOrNull { day -> day.lessons.any { it.counts() } }?.date
    }

    private fun strip(monday: LocalDate, byDate: Map<LocalDate, LessonDay>, today: LocalDate) = StripWeek(
        monday,
        (0..SUNDAY_OFFSET).map { offset ->
            val date = monday.plus(offset, DateTimeUnit.DAY)
            val lessons = byDate[date]?.lessons.orEmpty()
            StripDay(
                date = date,
                weekday = DateTexts.shortWeekday(date.dayOfWeek),
                number = date.format(DateTexts.DAY),
                lessons = lessons.count { it.counts() },
                changed = lessons.any { it.change != null },
                today = date == today,
                past = date < today,
            )
        },
    )

    private fun mondays(window: ClosedRange<LocalDate>): List<LocalDate> =
        generateSequence(monday(window.start)) { it.plus(1, DateTimeUnit.WEEK) }.takeWhile { it <= window.endInclusive }.toList()

    /** MyITMO's number of [date]'s week, from any cached day of it. */
    private fun weekOf(date: LocalDate, byDate: Map<LocalDate, LessonDay>): Int? {
        val monday = monday(date)
        return (0..SUNDAY_OFFSET).firstNotNullOfOrNull { byDate[monday.plus(it, DateTimeUnit.DAY)]?.week }
    }

    /** A lesson that takes place: not cancelled, not only a queue. */
    private fun DayLesson.counts() = change?.cancelled != true && sport != SportMark.QUEUED

    private fun count(lessons: List<DayLesson>): UiText {
        val count = lessons.count { it.counts() }
        return UiText.Plural(CorePlurals.schedule_lesson_count, count, listOf(count))
    }

    private fun weekLabel(week: Int) = UiText.Res(
        if (week % 2 == 0) ScheduleStrings.schedule_week_even else ScheduleStrings.schedule_week_odd,
        listOf(week),
    )

    private fun userLine(user: SelectedUser) =
        UiText.Dynamic(listOfNotNull(user.name, user.group?.takeIf(String::isNotBlank)).joinToString(", "))

    private fun monthTitle(date: LocalDate) = DateTexts.standaloneMonth(date.month).capitalized()

    private fun dayTitle(date: LocalDate) = date.format(DateTexts.WEEKDAY_DAY_MONTH).capitalized()

    private fun shortDay(date: LocalDate) = date.format(DateTexts.SHORT_WEEKDAY_DAY_MONTH).capitalized()

    /** `12–13 сентября`, or `31 октября – 1 ноября` across months. */
    private fun weekendRange(saturday: LocalDate, sunday: LocalDate): String =
        if (saturday.month == sunday.month) {
            "${saturday.format(DateTexts.DAY)}–${sunday.format(DateTexts.DAY_MONTH)}"
        } else {
            "${saturday.format(DateTexts.DAY_MONTH)} – ${sunday.format(DateTexts.DAY_MONTH)}"
        }

    private fun String.capitalized() = replaceFirstChar { it.uppercaseChar() }

    private const val SUNDAY_OFFSET = 6
}
