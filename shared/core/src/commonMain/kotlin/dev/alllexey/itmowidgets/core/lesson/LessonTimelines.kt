package dev.alllexey.itmowidgets.core.lesson

import dev.alllexey.itmowidgets.core.schedule.lessonTypeName
import dev.alllexey.itmowidgets.core.text.DateTexts
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.core.text.buildingShortTitle
import dev.alllexey.itmowidgets.core.text.durationText
import dev.alllexey.itmowidgets.core.text.roomShortTitle
import dev.alllexey.itmowidgets.core.text.shortPersonName
import dev.alllexey.itmowidgets.shared.core.CoreStrings
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.format
import kotlinx.datetime.minus

/**
 * The rules of every lesson list (docs/design.md "Lessons"): a day's lessons become a timeline with the elapsed part,
 * `идёт` and `далее`, `Окно 2 ч 10 мин` rows for gaps of [GAP_MINUTES] or more and change notes; a single lesson
 * becomes a [LessonUi]. Pure: the caller supplies the moment and today's date.
 */
object LessonTimelines {
    const val GAP_MINUTES = 60

    /** [lessons] of one day on the rail, sorted by start; [today] dates the change notes. */
    fun build(lessons: List<DayLesson>, moment: DayMoment, today: LocalDate): List<TimelineItem> {
        val sorted = lessons.sortedBy { it.start }
        val states = states(sorted, moment)
        val now = (moment as? DayMoment.Today)?.time?.minutes()
        val past = moment == DayMoment.Past
        return buildList {
            sorted.forEachIndexed { index, lesson ->
                val start = lesson.start.minutes()
                val end = lesson.end.minutes()
                val state = states[index]
                if (index > 0) {
                    val from = sorted[index - 1].end.minutes()
                    if (start - from >= GAP_MINUTES) {
                        val elapsed = now?.let { share(it, from, start) } ?: if (past) 1f else 0f
                        add(TimelineItem.Gap(UiText.Res(CoreStrings.lesson_gap, listOf(durationText(start - from))), elapsed))
                    }
                }
                // The rail under a dot spans the lesson and the short break after it, up to the next dot.
                val next = sorted.getOrNull(index + 1)?.start?.minutes()
                val until = if (next != null && next >= end && next - end < GAP_MINUTES) next else end
                add(
                    TimelineItem.Lesson(
                        lesson = lesson(lesson, state),
                        progress = now?.let { share(it, start, until) } ?: if (state == LessonState.DONE) 1f else 0f,
                        reached = now?.let { it >= start } ?: (state == LessonState.DONE),
                        changeNote = lesson.change?.let { changeNote(it.detectedAt, today) },
                    ),
                )
            }
        }
    }

    /** One lesson outside a day list in [state]: the hero's neighbour, a sport booking. */
    fun lesson(lesson: DayLesson, state: LessonState): LessonUi {
        val cancelled = lesson.change?.cancelled == true
        val place = place(lesson)
        return LessonUi(
            start = lesson.start.format(DateTexts.TIME),
            end = lesson.end.format(DateTexts.TIME),
            subject = lesson.subject.trim(),
            byline = byline(lesson),
            place = place,
            typeId = lesson.typeId,
            state = state,
            target = lesson.target,
            previousPlace = lesson.change?.previousRoom?.trim()?.takeIf { it.isNotEmpty() && place != null && !cancelled }
                ?.let(::roomShortTitle),
            cancelled = cancelled,
            note = lesson.note?.trim()?.takeIf(String::isNotEmpty),
            pills = pills(lesson, state, cancelled),
        )
    }

    /** `Лекция, Белов П. И.`; the type alone without a teacher. */
    fun byline(lesson: DayLesson): UiText = UiText.Joined(
        listOfNotNull(
            lessonTypeName(lesson.typeId),
            lesson.teacher?.trim()?.takeIf(String::isNotEmpty)?.let { UiText.Dynamic(shortPersonName(it)) },
        ),
        BYLINE_SEPARATOR,
    )

    /** `1506, Кронва`; null online or without a room and a building. */
    fun place(lesson: DayLesson): UiText? {
        if (lesson.online) return null
        val parts = listOfNotNull(
            lesson.room?.trim()?.takeIf(String::isNotEmpty)?.let(::roomShortTitle),
            lesson.building?.trim()?.takeIf(String::isNotEmpty)?.let { buildingShortTitle(it) },
        )
        return if (parts.isEmpty()) null else UiText.Joined(parts, BYLINE_SEPARATOR)
    }

    /** `Изменено сегодня в 20:14`, `вчера`, or the date. */
    fun changeNote(at: LocalDateTime, today: LocalDate): UiText {
        val time = at.time.format(DateTexts.TIME)
        return when (at.date) {
            today -> UiText.Res(CoreStrings.lesson_changed_today, listOf(time))
            today.minus(1, DateTimeUnit.DAY) -> UiText.Res(CoreStrings.lesson_changed_yesterday, listOf(time))
            else -> UiText.Res(
                CoreStrings.lesson_changed_on,
                listOf(at.date.format(DateTexts.SHORT_WEEKDAY_DAY_SHORT_MONTH), time),
            )
        }
    }

    /** A cancelled or queued lesson is never `идёт` or `далее`; [DayMoment.Future] marks only the first that can be. */
    private fun states(lessons: List<DayLesson>, moment: DayMoment): List<LessonState> = when (moment) {
        DayMoment.Past -> lessons.map { LessonState.DONE }
        is DayMoment.Future -> {
            val next = if (moment.firstIsNext) lessons.indexOfFirst(::canBeCurrent) else -1
            lessons.indices.map { if (it == next) LessonState.NEXT else LessonState.UPCOMING }
        }
        is DayMoment.Today -> {
            val time = moment.time
            val next = lessons.indexOfFirst { canBeCurrent(it) && it.start > time }
            lessons.mapIndexed { index, lesson ->
                when {
                    lesson.end <= time -> LessonState.DONE
                    !canBeCurrent(lesson) -> LessonState.UPCOMING
                    lesson.start <= time -> LessonState.NOW
                    index == next -> LessonState.NEXT
                    else -> LessonState.UPCOMING
                }
            }
        }
    }

    private fun canBeCurrent(lesson: DayLesson) = lesson.change?.cancelled != true && lesson.sport != SportMark.QUEUED

    private fun pills(lesson: DayLesson, state: LessonState, cancelled: Boolean): List<LessonPill> = buildList {
        if (state == LessonState.NOW) add(pill(LessonPillKind.NOW, CoreStrings.lesson_pill_now))
        if (state == LessonState.NEXT) add(pill(LessonPillKind.NEXT, CoreStrings.lesson_pill_next))
        when {
            cancelled -> add(pill(LessonPillKind.CANCELLED, CoreStrings.lesson_pill_cancelled))
            lesson.change != null -> add(pill(LessonPillKind.CHANGED, CoreStrings.lesson_pill_changed))
        }
        when (lesson.sport) {
            SportMark.QUEUED -> add(pill(LessonPillKind.QUEUE, CoreStrings.lesson_pill_queue))
            SportMark.BOOKED -> add(pill(LessonPillKind.BOOKED, CoreStrings.lesson_pill_booked))
            null -> Unit
        }
        if (lesson.online) add(pill(LessonPillKind.ONLINE, CoreStrings.lesson_pill_online))
    }

    private fun pill(kind: LessonPillKind, key: String) = LessonPill(kind, UiText.Res(key))

    /** Where [time] is inside [from, to), 0..1. */
    private fun share(time: Int, from: Int, to: Int): Float = if (to <= from) {
        if (time >= to) 1f else 0f
    } else {
        ((time - from).toFloat() / (to - from)).coerceIn(0f, 1f)
    }

    private fun LocalTime.minutes(): Int = hour * MINUTES_PER_HOUR + minute

    private const val MINUTES_PER_HOUR = 60
    private const val BYLINE_SEPARATOR = ", "
}
