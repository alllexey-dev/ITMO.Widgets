package dev.alllexey.itmowidgets.feature.home.presentation

import dev.alllexey.itmowidgets.core.lesson.DayLesson
import dev.alllexey.itmowidgets.core.lesson.DayMoment
import dev.alllexey.itmowidgets.core.lesson.LessonDay
import dev.alllexey.itmowidgets.core.lesson.LessonTimelines
import dev.alllexey.itmowidgets.core.lesson.SportMark
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.text.DateTexts
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.core.text.durationText
import dev.alllexey.itmowidgets.core.text.roomShortTitle
import dev.alllexey.itmowidgets.core.today.TodayTodo
import dev.alllexey.itmowidgets.shared.core.CorePlurals
import dev.alllexey.itmowidgets.shared.feature.home.HomeStrings
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

/** How the last refresh of Сегодня ended. */
sealed interface TodayRefresh {
    /** None has ended yet. */
    data object Pending : TodayRefresh

    data object Done : TodayRefresh

    data class Failed(val error: AppError) : TodayRefresh
}

/**
 * What Сегодня shows at [now] (docs/features/home.md): while today has a lesson that is not over, the hero is the
 * current one or the next one and the day is today's whole timeline; once today is over, tomorrow's first lesson and
 * tomorrow. Cancelled lessons and sport queues are never the hero. Pure: the caller supplies the moment.
 */
object TodayPresenter {

    fun state(
        now: LocalDateTime,
        days: List<LessonDay>,
        todos: List<TodayTodo>,
        refresh: TodayRefresh,
        refreshing: Boolean,
        zone: TimeZone,
    ): TodayUiState {
        val today = days.firstOrNull { it.date == now.date }
        val tomorrow = days.firstOrNull { it.date == now.date.plus(1, DateTimeUnit.DAY) }
        if (today == null && tomorrow == null) {
            when (refresh) {
                TodayRefresh.Pending -> return TodayUiState.Loading
                is TodayRefresh.Failed -> return TodayUiState.Failed(refresh.error, retrying = refreshing)
                TodayRefresh.Done -> Unit
            }
        }
        val (hero, day) = heroAndDay(now, today?.lessons.orEmpty(), tomorrow?.lessons.orEmpty())
        val stale = (refresh as? TodayRefresh.Failed)?.let {
            listOfNotNull(today, tomorrow).mapNotNull { it.cachedAt }.minOrNull()?.let { at -> staleLabel(at.toLocalDateTime(zone), now) }
        }
        return TodayUiState.Content(hero, day, todos, stale, refreshing)
    }

    private fun heroAndDay(now: LocalDateTime, today: List<DayLesson>, tomorrow: List<DayLesson>): Pair<TodayHero?, TodayDay?> {
        val time = now.time
        val ahead = today.filter { canLead(it) && it.end > time }
        if (ahead.isNotEmpty()) {
            val current = ahead.firstOrNull { it.start <= time }
            val lesson = current ?: ahead.first()
            val (label, countdown) = when {
                current != null -> HomeStrings.today_hero_now to left(time, lesson.end)
                today.none { canLead(it) && it.end <= time } -> HomeStrings.today_hero_first to inTime(time, lesson.start)
                else -> HomeStrings.today_hero_next to inTime(time, lesson.start)
            }
            val progress = current?.let { share(time, it.start, it.end) }
            val day = day(HomeStrings.today_day_title, today, DayMoment.Today(time), now)
            return hero(lesson, UiText.Res(label), countdown, progress) to day
        }
        val first = tomorrow.firstOrNull(::canLead)
        if (first != null) {
            val countdown = UiText.Res(HomeStrings.today_at, listOf(first.start.format(DateTexts.TIME)))
            val day = day(HomeStrings.today_tomorrow_title, tomorrow, DayMoment.Future(firstIsNext = true), now)
            return hero(first, UiText.Res(HomeStrings.today_hero_tomorrow), countdown, progress = null) to day
        }
        // Today is over and tomorrow is free: today's finished day stays, without a hero.
        val day = today.takeIf { it.isNotEmpty() }?.let { day(HomeStrings.today_day_title, it, DayMoment.Today(time), now) }
        return null to day
    }

    private fun day(title: String, lessons: List<DayLesson>, moment: DayMoment, now: LocalDateTime) = TodayDay(
        title = UiText.Res(title),
        count = lessons.count(::canLead).let { UiText.Plural(CorePlurals.schedule_lesson_count, it, listOf(it)) },
        items = LessonTimelines.build(lessons, moment, now.date),
        showNow = moment is DayMoment.Today,
    )

    private fun hero(lesson: DayLesson, label: UiText, countdown: UiText, progress: Float?) = TodayHero(
        label = label,
        countdown = countdown,
        subject = lesson.subject.trim(),
        byline = LessonTimelines.byline(lesson),
        room = lesson.room?.trim()?.takeIf { it.isNotEmpty() && !lesson.online }?.let(::roomShortTitle),
        address = lesson.building?.trim()?.takeIf { it.isNotEmpty() && !lesson.online },
        online = lesson.online,
        videoUrl = lesson.videoUrl,
        start = lesson.start.format(DateTexts.TIME),
        end = lesson.end.format(DateTexts.TIME),
        progress = progress,
        note = lesson.note?.trim()?.takeIf(String::isNotEmpty),
        target = lesson.target,
    )

    /** A lesson one goes to: not cancelled and not waiting in a sport queue. */
    private fun canLead(lesson: DayLesson) = lesson.change?.cancelled != true && lesson.sport != SportMark.QUEUED

    private fun left(now: LocalTime, end: LocalTime) = UiText.Res(HomeStrings.today_left, listOf(durationText(minutesUntil(now, end))))

    private fun inTime(now: LocalTime, start: LocalTime) =
        UiText.Res(HomeStrings.today_in, listOf(durationText(minutesUntil(now, start))))

    /** Whole minutes from [from] to [to], rounded up, at least one. */
    private fun minutesUntil(from: LocalTime, to: LocalTime): Int {
        val seconds = to.toSecondOfDay() - from.toSecondOfDay()
        return ((seconds + SECONDS_PER_MINUTE - 1) / SECONDS_PER_MINUTE).coerceAtLeast(1)
    }

    private fun share(now: LocalTime, start: LocalTime, end: LocalTime): Float {
        val total = end.toSecondOfDay() - start.toSecondOfDay()
        if (total <= 0) return 1f
        return ((now.toSecondOfDay() - start.toSecondOfDay()).toFloat() / total).coerceIn(0f, 1f)
    }

    /** `Данные на 11:44` today, `Данные на 7 окт., 11:44` before. */
    private fun staleLabel(at: LocalDateTime, now: LocalDateTime): UiText {
        val text = if (at.date == now.date) at.time.format(DateTexts.TIME) else at.format(DateTexts.DAY_SHORT_MONTH_TIME)
        return UiText.Res(HomeStrings.today_stale, listOf(text))
    }

    private const val SECONDS_PER_MINUTE = 60
}
