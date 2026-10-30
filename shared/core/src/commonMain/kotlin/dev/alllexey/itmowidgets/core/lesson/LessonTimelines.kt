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

    /**
     * [lessons] of one day on the rail, sorted by start; [today] dates the change notes. The rail is one clock: each row
     * covers a stretch of the day that starts where the row above ended (a lesson from its start, or from the end of an
     * overlapping one above; a short break belongs to the lesson before it), so the elapsed part grows monotonically
     * and only the stretch with the moment in it is part-way, which puts the one `сейчас` marker there. Overlapping
     * lessons stay in start order without a window between them; the later one says `одновременно с …`.
     */
    fun build(lessons: List<DayLesson>, moment: DayMoment, today: LocalDate): List<TimelineItem> {
        val sorted = lessons.sortedBy { it.start }
        val states = states(sorted, moment)
        val now = (moment as? DayMoment.Today)?.time?.minutes()
        val past = moment == DayMoment.Past
        fun elapsed(from: Int, to: Int): Float = now?.let { share(it, from, to) } ?: if (past) 1f else 0f
        return buildList {
            var railEnd = sorted.firstOrNull()?.start?.minutes() ?: 0
            var latestEnd = railEnd
            sorted.forEachIndexed { index, lesson ->
                val start = lesson.start.minutes()
                if (index > 0 && start - latestEnd >= GAP_MINUTES) {
                    add(TimelineItem.Gap(UiText.Res(CoreStrings.lesson_gap, listOf(durationText(start - latestEnd))), elapsed(latestEnd, start)))
                    railEnd = start
                }
                val from = maxOf(start, railEnd)
                latestEnd = maxOf(latestEnd, lesson.end.minutes())
                // The rail under a dot runs to the latest end so far, and on over a short break up to the next dot.
                val next = sorted.getOrNull(index + 1)?.start?.minutes()
                val until = if (next != null && next > latestEnd && next - latestEnd < GAP_MINUTES) next else latestEnd
                railEnd = maxOf(until, from)
                add(
                    TimelineItem.Lesson(
                        lesson = lesson(lesson, states[index]),
                        progress = elapsed(from, railEnd),
                        reached = now?.let { it >= from } ?: (past || states[index] == LessonState.DONE),
                        changeNote = lesson.change?.let { changeNote(it.detectedAt, today) },
                        overlapNote = sorted.take(index).firstOrNull { it.overlaps(lesson) }
                            ?.let { UiText.Res(CoreStrings.lesson_overlap, listOf(it.subject.trim())) },
                    ),
                )
            }
        }
    }

    /** Both take place and share some minutes. */
    private fun DayLesson.overlaps(other: DayLesson): Boolean =
        change?.cancelled != true && other.change?.cancelled != true && other.start < end && start < other.end

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
            note = lesson.noteText,
            pills = pills(lesson, state, cancelled),
            withYou = lesson.withYou,
        )
    }

    /** `Лекция, Белов П. И.`, in a teacher's timetable `Лекция, M3206, M3207`; the type alone without either. */
    fun byline(lesson: DayLesson): UiText = UiText.Joined(
        listOfNotNull(
            lessonTypeName(lesson.typeId),
            lesson.teacher?.trim()?.takeIf(String::isNotEmpty)?.let { UiText.Dynamic(shortPersonName(it)) },
            lesson.groups.takeIf { it.isNotEmpty() }?.let { UiText.Dynamic(it.joinToString(BYLINE_SEPARATOR)) },
        ),
        BYLINE_SEPARATOR,
    )

    /** `1506, Кронва`; null online or without a room and a building. */
    fun place(lesson: DayLesson): UiText? {
        if (lesson.isOnline) return null
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
            SportMark.QUEUED -> add(LessonPill(LessonPillKind.QUEUE, queueText(lesson.queue)))
            SportMark.BOOKED -> add(pill(LessonPillKind.BOOKED, CoreStrings.lesson_pill_booked))
            null -> Unit
        }
        if (lesson.isOnline) add(pill(LessonPillKind.ONLINE, CoreStrings.lesson_pill_online))
    }

    private fun pill(kind: LessonPillKind, key: String) = LessonPill(kind, UiText.Res(key))

    /** `очередь 2 из 5`; `очередь` while the place is unknown. */
    private fun queueText(place: QueuePlace?): UiText =
        place?.let { UiText.Res(CoreStrings.lesson_pill_queue_position, listOf(it.position, it.total)) }
            ?: UiText.Res(CoreStrings.lesson_pill_queue)

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
