package dev.alllexey.itmowidgets.feature.schedule.ui.widget

import android.content.Context
import android.text.SpannableString
import android.text.Spanned
import android.text.style.RelativeSizeSpan
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.schedule.lessonTypeName
import dev.alllexey.itmowidgets.core.text.DateTexts
import dev.alllexey.itmowidgets.core.text.buildingShortTitle
import dev.alllexey.itmowidgets.core.text.durationText
import dev.alllexey.itmowidgets.core.text.roomShortTitle
import dev.alllexey.itmowidgets.core.ui.resolve
import dev.alllexey.itmowidgets.feature.schedule.domain.widget.DaySummary
import dev.alllexey.itmowidgets.feature.schedule.domain.widget.DaySummaryKind
import dev.alllexey.itmowidgets.feature.schedule.domain.widget.NextLessonMoment
import dev.alllexey.itmowidgets.feature.schedule.domain.widget.NextLessonWidgetContent
import dev.alllexey.itmowidgets.feature.schedule.domain.widget.WidgetContentKind
import dev.alllexey.itmowidgets.feature.schedule.domain.widget.WidgetLesson
import kotlin.math.ceil
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.format

/** The words of the schedule widgets, the catalog's `widget_next_*` and `widget_day_*` (shared with iOS). */
internal class ScheduleWidgetTexts(private val context: Context) {

    fun headline(content: NextLessonWidgetContent): String {
        val lesson = checkNotNull(content.lesson)
        return when (content.moment) {
            NextLessonMoment.NOW -> context.getString(R.string.widget_next_now, lesson.end)
            NextLessonMoment.FIRST -> context.getString(R.string.widget_next_first, lesson.start)
            NextLessonMoment.TOMORROW -> context.getString(R.string.widget_next_tomorrow, lesson.start)
            NextLessonMoment.NEXT, null -> context.getString(R.string.widget_next_next, lesson.start)
        }
    }

    /** `ещё 48 мин`, `через 15 мин`, `окно ещё 1 ч 40 мин`, `на сегодня всё`, at [now]. */
    fun footer(content: NextLessonWidgetContent, now: Instant): String {
        val until = content.until?.let(Instant::parse)
            ?: return context.getString(if (content.todayHadLessons) R.string.widget_next_over else R.string.widget_next_none_today)
        val left = durationText(minutesUntil(until, now)).resolve(context)
        return context.getString(
            when {
                content.moment == NextLessonMoment.NOW -> R.string.widget_next_left
                content.window -> R.string.widget_next_window
                else -> R.string.widget_next_in
            },
            left
        )
    }

    /** The elapsed share of the current lesson at [now], 0..1; null when nothing is current. */
    fun progress(content: NextLessonWidgetContent, now: Instant): Float? {
        val since = content.since?.let(Instant::parse) ?: return null
        val until = content.until?.let(Instant::parse) ?: return null
        val total = (until - since).inWholeMilliseconds.takeIf { it > 0 } ?: return null
        return ((now - since).inWholeMilliseconds.toFloat() / total).coerceIn(0f, 1f)
    }

    fun restLabel(moment: NextLessonMoment?): String = context.getString(
        when (moment) {
            NextLessonMoment.TOMORROW -> R.string.widget_next_rest_tomorrow
            NextLessonMoment.NEXT -> R.string.widget_next_rest_then
            else -> R.string.widget_next_rest_next
        }
    )

    fun type(lesson: WidgetLesson): String = lessonTypeName(lesson.typeId).resolve(context)

    fun room(lesson: WidgetLesson): String? = lesson.room?.let { roomShortTitle(it).resolve(context) }

    fun building(lesson: WidgetLesson): String? = lesson.building?.let { buildingShortTitle(it, BUILDING_MAX_LENGTH).resolve(context) }

    fun online(): String = context.getString(R.string.lesson_pill_online)

    /** `1506 Кронва` with the building at half the room's size; `онлайн`; or the room or building alone. */
    fun bigPlace(lesson: WidgetLesson): CharSequence {
        if (lesson.online) return online()
        val room = room(lesson)
        val building = building(lesson)
        if (room == null || building == null) return room ?: building.orEmpty()
        return SpannableString("$room $building").apply {
            setSpan(RelativeSizeSpan(BUILDING_SCALE), room.length, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }

    /** `1506, Кронва`, `онлайн` or the queue. */
    fun shortPlace(lesson: WidgetLesson): String = when {
        lesson.queued -> context.getString(R.string.lesson_pill_queue)
        lesson.online -> online()
        else -> listOfNotNull(room(lesson), building(lesson)).joinToString(", ")
    }

    fun time(lesson: WidgetLesson): String = "${lesson.start}–${lesson.end}"

    fun message(kind: WidgetContentKind, emptyToday: Boolean = false): String = context.getString(
        when (kind) {
            WidgetContentKind.SIGNED_OUT -> R.string.schedule_widget_signed_out
            WidgetContentKind.LOADING -> R.string.schedule_widget_loading
            WidgetContentKind.ERROR -> R.string.schedule_widget_error
            WidgetContentKind.EMPTY -> if (emptyToday) R.string.widget_day_empty else R.string.schedule_widget_empty_today_and_tomorrow
            WidgetContentKind.CONTENT -> R.string.schedule_widget_loading
        }
    )

    /** `Понедельник, 7 сентября` or `Завтра, 8 сентября`. */
    fun dayTitle(date: LocalDate, tomorrow: Boolean): String =
        if (tomorrow) context.getString(R.string.widget_day_tomorrow, date.format(DateTexts.DAY_MONTH))
        else date.format(DateTexts.WEEKDAY_DAY_MONTH).replaceFirstChar(Char::uppercaseChar)

    fun summary(summary: DaySummary): String = when (summary.kind) {
        DaySummaryKind.OVER -> context.getString(R.string.widget_next_over)
        DaySummaryKind.NONE_TODAY -> context.getString(R.string.widget_next_none_today)
        DaySummaryKind.PLANNED -> context.getString(R.string.widget_day_planned, lessons(summary), summary.from, summary.to)
        DaySummaryKind.REMAINING -> context.getString(R.string.widget_day_remaining, lessons(summary), summary.to)
    }

    fun gap(minutes: Int): String = context.getString(R.string.widget_day_window, durationText(minutes).resolve(context))

    /** `2 пары и спорт`, `2 пары`, `спорт`. */
    private fun lessons(summary: DaySummary): String {
        val count = context.resources.getQuantityString(R.plurals.widget_day_lessons, summary.lessons, summary.lessons)
        return when {
            summary.lessons == 0 -> context.getString(R.string.widget_day_sport_only)
            summary.sport -> context.getString(R.string.widget_day_with_sport, count)
            else -> count
        }
    }

    private fun minutesUntil(until: Instant, now: Instant): Int =
        ceil((until - now).inWholeSeconds.coerceAtLeast(0) / SECONDS_PER_MINUTE).toInt()

    private companion object {
        const val BUILDING_MAX_LENGTH = 14
        const val BUILDING_SCALE = 0.5f
        const val SECONDS_PER_MINUTE = 60.0
    }
}
