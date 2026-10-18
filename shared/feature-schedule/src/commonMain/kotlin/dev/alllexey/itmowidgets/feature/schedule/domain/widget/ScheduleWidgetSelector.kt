package dev.alllexey.itmowidgets.feature.schedule.domain.widget

import dev.alllexey.itmowidgets.core.lesson.DayLesson
import dev.alllexey.itmowidgets.core.lesson.LessonDay
import dev.alllexey.itmowidgets.core.lesson.LessonTarget
import dev.alllexey.itmowidgets.core.lesson.LessonTimelines
import dev.alllexey.itmowidgets.core.lesson.SportMark
import dev.alllexey.itmowidgets.core.settings.CompactScheduleWidgetSettings
import dev.alllexey.itmowidgets.core.settings.FullScheduleWidgetSettings
import dev.alllexey.itmowidgets.core.text.DateTexts
import dev.alllexey.itmowidgets.core.text.shortPersonName
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.format
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

/**
 * What the next-lesson and the day widget show at an instant, from the own schedule days ([LessonDay], the same days
 * Сегодня and the schedule draw, with their changes and sport queues). Only today and tomorrow of the instant are
 * read. A queue never becomes the lesson to go to; a cancelled lesson stays on the day widget only.
 */
class ScheduleWidgetSelector {

    fun select(
        days: List<LessonDay>,
        now: Instant,
        timeZone: TimeZone,
        preferences: ScheduleWidgetPreferences,
    ): ScheduleWidgetSelection {
        val wall = Wall(now, timeZone)
        val today = wall.lessonsOn(days, wall.today)
        val tomorrow = wall.lessonsOn(days, wall.tomorrow)
        val queued = listOf(wall.today, wall.tomorrow).flatMap { date ->
            wall.lessonsOn(days, date).filter { it.sport == SportMark.QUEUED }.map { wall.instantOf(date, it.start) }
        }
        val snapshot = ScheduleWidgetSnapshot(
            next = next(today, tomorrow, wall, preferences.display.compact),
            day = day(today, tomorrow, wall, preferences.display.full),
            officialFallback = if (queued.isEmpty()) null else select(days.withoutQueues(), now, timeZone, preferences).snapshot,
            pendingValidUntil = queued.minOrNull()?.let { minOf(it, now + PERIODIC_UPDATE_DELAY) }?.toString(),
        ).withTextSizes(preferences.display)
        val next = boundaries(days, now, timeZone).filter { it > now }.minOrNull() ?: (now + PERIODIC_UPDATE_DELAY)
        return ScheduleWidgetSelection(snapshot, (next - now).coerceAtLeast(MINIMUM_UPDATE_DELAY))
    }

    /**
     * [select] at [from] and at every later instant before [until] where its snapshot can change: lesson starts and
     * ends, an hour before a start (the `сейчас` line), the early switch before an end, midnights. Adjacent snapshots that differ only in the `сейчас` minute merge;
     * an entry whose day shows the line carries a [NowTick] every [TICK] for it. [days] cover the dates of [from]
     * through the day after the last instant before [until].
     */
    fun timeline(
        days: List<LessonDay>,
        from: Instant,
        until: Instant,
        timeZone: TimeZone,
        preferences: ScheduleWidgetPreferences,
    ): ScheduleWidgetTimeline {
        require(from < until) { "Timeline ends at $until, not after its start $from" }
        val starts = listOf(from) + boundaries(days, from, timeZone, until).filter { it > from && it < until }.sorted()
        val merged = mutableListOf<ScheduleWidgetTimelineEntry>()
        for (start in starts) {
            val snapshot = select(days, start, timeZone, preferences).snapshot
            if (merged.lastOrNull()?.snapshot?.comparable() != snapshot.comparable()) {
                merged += ScheduleWidgetTimelineEntry(start, snapshot)
            }
        }
        val timeline = ScheduleWidgetTimeline(generatedAt = from, validUntil = until, entries = merged)
        return timeline.copy(entries = merged.mapIndexed { index, entry ->
            val end = timeline.validityEnd(index)
            val snapshot = entry.snapshot
            entry.copy(
                // pendingValidUntil from select() moves with every instant; an entry's pending rows hold to its own end.
                snapshot = if (snapshot.pendingValidUntil == null) snapshot else snapshot.copy(pendingValidUntil = end.toString()),
                nowTicks = if (snapshot.day.now == null) emptyList() else ticks(entry.validFrom, end, timeZone),
            )
        })
    }

    private fun ticks(from: Instant, until: Instant, timeZone: TimeZone): List<NowTick> {
        val step = TICK.inWholeMilliseconds
        val first = (from.toEpochMilliseconds() / step + 1) * step
        return generateSequence(Instant.fromEpochMilliseconds(first)) { it + TICK }
            .takeWhile { it < until }
            .map { NowTick(it, it.toLocalDateTime(timeZone).time.minutes()) }
            .toList()
    }

    private fun ScheduleWidgetSnapshot.comparable(): ScheduleWidgetSnapshot = copy(
        day = day.copy(now = day.now?.let { 0 }),
        pendingValidUntil = null,
        officialFallback = officialFallback?.comparable(),
    )

    /** Every instant of [days] where a widget can change, plus the midnights after [from] up to the day after [until]. */
    private fun boundaries(days: List<LessonDay>, from: Instant, timeZone: TimeZone, until: Instant = from): Set<Instant> {
        fun instantOf(date: LocalDate, time: LocalTime) = LocalDateTime(date, time).toInstant(timeZone)
        val early = EARLY_SWITCH
        val lessons = days.flatMap { day ->
            day.lessons.flatMap { lesson ->
                val start = instantOf(day.date, lesson.start)
                val end = instantOf(day.date, lesson.end)
                // The `сейчас` line appears an hour before a first lesson.
                listOf(start, start - NOW_LEAD_MINUTES.minutes, end, end - early)
            }
        }
        val lastMidnight = until.toLocalDateTime(timeZone).date.plus(1, DateTimeUnit.DAY)
        val midnights = generateSequence(from.toLocalDateTime(timeZone).date.plus(1, DateTimeUnit.DAY)) {
            it.plus(1, DateTimeUnit.DAY)
        }.takeWhile { it <= lastMidnight }.map { it.atStartOfDayIn(timeZone) }
        return (lessons + midnights).toSet()
    }

    private fun next(
        today: List<DayLesson>,
        tomorrow: List<DayLesson>,
        wall: Wall,
        settings: CompactScheduleWidgetSettings,
    ): NextLessonWidgetContent {
        val t = wall.time
        val main = today.filter(::canBeMain)
        val current = main.firstOrNull { it.start <= t && it.end > t }
        val upcoming = main.firstOrNull { it.start > t }
        val switchEarly = settings.showNextLessonEarly && current != null && upcoming != null &&
            current.end.minutes() - t.minutes() <= EARLY_SWITCH.inWholeMinutes
        val lesson = if (current != null && !switchEarly) current else upcoming
        if (lesson == null) {
            val first = tomorrow.firstOrNull(::canBeMain)
                ?: return NextLessonWidgetContent(WidgetContentKind.EMPTY, todayHadLessons = main.isNotEmpty())
            return NextLessonWidgetContent(
                kind = WidgetContentKind.CONTENT,
                moment = NextLessonMoment.TOMORROW,
                lesson = first.toWidget(wall, wall.tomorrow, showTeacher = false),
                rest = rest(tomorrow, first, wall, wall.tomorrow),
                lastEnd = tomorrow.lastEndText(),
                todayHadLessons = main.isNotEmpty(),
            )
        }
        val now = lesson === current
        val previousEnd = main.filter { it.end <= lesson.start && it !== lesson }.maxOfOrNull { it.end }
        val moment = when {
            now -> NextLessonMoment.NOW
            previousEnd == null && main.none { it.start < lesson.start } -> NextLessonMoment.FIRST
            else -> NextLessonMoment.NEXT
        }
        return NextLessonWidgetContent(
            kind = WidgetContentKind.CONTENT,
            moment = moment,
            lesson = lesson.toWidget(wall, wall.today, showTeacher = false),
            until = wall.instantOf(wall.today, if (now) lesson.end else lesson.start).toString(),
            since = if (now) wall.instantOf(wall.today, lesson.start).toString() else null,
            window = !now && current == null && previousEnd != null &&
                lesson.start.minutes() - previousEnd.minutes() >= LessonTimelines.GAP_MINUTES,
            rest = rest(today, lesson, wall, wall.today),
            lastEnd = today.lastEndText(),
            todayHadLessons = true,
        )
    }

    private fun rest(day: List<DayLesson>, after: DayLesson, wall: Wall, date: LocalDate): List<WidgetLesson> =
        day.drop(day.indexOfFirst { it === after } + 1)
            .filter { !it.cancelled }
            .take(REST)
            .map { it.toWidget(wall, date, showTeacher = false) }

    private fun day(
        today: List<DayLesson>,
        tomorrow: List<DayLesson>,
        wall: Wall,
        settings: FullScheduleWidgetSettings,
    ): DayWidgetContent {
        val t = wall.time
        val todayHeld = today.filter { !it.cancelled }
        val over = todayHeld.none { it.end > t }
        val showTomorrow = settings.showTomorrowWhenTodayIsOver && over
        val date = if (showTomorrow) wall.tomorrow else wall.today
        val all = if (showTomorrow) tomorrow else today
        val shown = if (!showTomorrow && settings.hidePastLessons) all.filter { it.end > t } else all
        if (shown.isEmpty()) {
            return DayWidgetContent(
                kind = WidgetContentKind.EMPTY,
                date = date.toString(),
                tomorrow = showTomorrow,
                summary = DaySummary(if (todayHeld.isEmpty()) DaySummaryKind.NONE_TODAY else DaySummaryKind.OVER),
            )
        }
        val lessons = shown.map { it.toWidget(wall, date, showTeacher = !settings.hideTeacher) }
        val start = shown.minOf { it.start.minutes() }
        val end = shown.maxOf { it.end.minutes() }
        val now = t.minutes().takeIf { !showTomorrow && it >= start - NOW_LEAD_MINUTES && it <= end }
        return DayWidgetContent(
            kind = WidgetContentKind.CONTENT,
            date = date.toString(),
            tomorrow = showTomorrow,
            summary = summary(todayHeld, showTomorrow, over, t),
            lessons = lessons,
            gaps = gaps(shown),
            axisStart = minOf(start, now ?: start) / MINUTES_PER_HOUR * MINUTES_PER_HOUR,
            axisEnd = end + AXIS_TAIL_MINUTES,
            now = now,
        )
    }

    private fun summary(
        today: List<DayLesson>,
        showTomorrow: Boolean,
        over: Boolean,
        t: LocalTime,
    ): DaySummary {
        if (showTomorrow) return DaySummary(if (today.isEmpty()) DaySummaryKind.NONE_TODAY else DaySummaryKind.OVER)
        if (over) return DaySummary(DaySummaryKind.OVER)
        val held = today
        val planned = held.first().start > t
        val counted = if (planned) held else held.filter { it.end > t }
        return DaySummary(
            kind = if (planned) DaySummaryKind.PLANNED else DaySummaryKind.REMAINING,
            lessons = counted.count { !it.isSport() },
            sport = counted.any { it.isSport() },
            from = if (planned) held.first().start.format(DateTexts.TIME) else null,
            to = held.maxOf { it.end }.format(DateTexts.TIME),
        )
    }

    /** Gaps of an hour or more between held lessons, as the timeline rows of Сегодня find them. */
    private fun gaps(lessons: List<DayLesson>): List<WidgetGap> {
        val held = lessons.filter { !it.cancelled }.sortedBy { it.start }
        var latestEnd = held.firstOrNull()?.end?.minutes() ?: return emptyList()
        return buildList {
            held.drop(1).forEach { lesson ->
                val start = lesson.start.minutes()
                if (start - latestEnd >= LessonTimelines.GAP_MINUTES) add(WidgetGap(latestEnd, start))
                latestEnd = maxOf(latestEnd, lesson.end.minutes())
            }
        }
    }

    private fun DayLesson.toWidget(wall: Wall, date: LocalDate, showTeacher: Boolean) = WidgetLesson(
        subject = subject.trim(),
        start = start.format(DateTexts.TIME),
        end = end.format(DateTexts.TIME),
        startMinute = start.minutes(),
        endMinute = end.minutes(),
        typeId = typeId,
        state = when {
            date < wall.today || (date == wall.today && end <= wall.time) -> WidgetLessonState.DONE
            date == wall.today && start <= wall.time && canBeMain(this) -> WidgetLessonState.NOW
            else -> WidgetLessonState.UPCOMING
        },
        room = room?.trim()?.takeIf { it.isNotEmpty() && !isOnline },
        building = building?.trim()?.takeIf { it.isNotEmpty() && !isOnline },
        teacher = teacher?.trim()?.takeIf { showTeacher && it.isNotEmpty() }?.let(::shortPersonName),
        online = isOnline,
        changed = change != null && !cancelled,
        cancelled = cancelled,
        queued = sport == SportMark.QUEUED,
        queuePosition = queue?.position ?: 0,
        queueTotal = queue?.total ?: 0,
        lesson = (target as? LessonTarget.Lesson)?.args,
        sport = (target as? LessonTarget.PendingSport)?.args,
    )

    private fun List<DayLesson>.lastEndText(): String? =
        filter { !it.cancelled }.maxOfOrNull { it.end }?.format(DateTexts.TIME)

    private fun List<LessonDay>.withoutQueues() = map { day -> day.copy(lessons = day.lessons.filter { it.sport != SportMark.QUEUED }) }

    private fun canBeMain(lesson: DayLesson) = !lesson.cancelled && lesson.sport != SportMark.QUEUED

    private val DayLesson.cancelled: Boolean get() = change?.cancelled == true

    private fun DayLesson.isSport() = typeId == SPORT_TYPE_ID || sport != null

    /** [now] read once on the wall of [timeZone]. */
    private class Wall(private val now: Instant, private val timeZone: TimeZone) {
        private val local: LocalDateTime = now.toLocalDateTime(timeZone)
        val today: LocalDate = local.date
        val tomorrow: LocalDate = today.plus(1, DateTimeUnit.DAY)
        val time: LocalTime = local.time

        fun instantOf(date: LocalDate, time: LocalTime): Instant = LocalDateTime(date, time).toInstant(timeZone)

        /** A queue whose lesson has started is no longer pending and leaves the widgets. */
        fun lessonsOn(days: List<LessonDay>, date: LocalDate): List<DayLesson> =
            days.filter { it.date == date }
                .flatMap { it.lessons }
                .filter { it.sport != SportMark.QUEUED || instantOf(date, it.start) > now }
                .sortedBy { it.start }
    }

    companion object {
        /** The next-lesson widget moves on this long before the current lesson ends (`showNextLessonEarly`). */
        val EARLY_SWITCH: Duration = 15.minutes
        /** Android's refresh while sport queues show: their state can change remotely at any time. */
        val PERIODIC_UPDATE_DELAY: Duration = 7.minutes
        /** The step of the `сейчас` line in the iOS timeline; Android draws it every minute. */
        val TICK: Duration = 5.minutes
        private val MINIMUM_UPDATE_DELAY: Duration = 5.seconds
        private const val REST = 2
        private const val MINUTES_PER_HOUR = 60
        /** The line shows from an hour before the first lesson, so the axis does not start at dawn. */
        private const val NOW_LEAD_MINUTES = 60
        private const val AXIS_TAIL_MINUTES = 5
        private const val SPORT_TYPE_ID = 11
    }
}

/** Minutes since midnight. */
internal fun LocalTime.minutes(): Int = hour * 60 + minute
