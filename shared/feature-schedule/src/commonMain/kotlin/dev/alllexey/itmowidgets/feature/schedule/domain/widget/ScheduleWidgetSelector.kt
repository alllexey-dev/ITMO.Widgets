package dev.alllexey.itmowidgets.feature.schedule.domain.widget

import dev.alllexey.itmowidgets.core.sport.PendingSportBooking
import dev.alllexey.itmowidgets.feature.schedule.domain.model.DaySchedule
import dev.alllexey.itmowidgets.feature.schedule.domain.model.Lesson
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

class ScheduleWidgetSelector {

    fun select(
        schedule: List<DaySchedule>,
        now: Instant,
        timeZone: TimeZone,
        preferences: ScheduleWidgetPreferences,
        pendingSport: List<PendingSportBooking> = emptyList(),
    ): ScheduleWidgetSelection {
        val wall = WallNow(now, timeZone)
        val today = wall.today
        val tomorrow = today.plus(1, DateTimeUnit.DAY)
        // Widget-only projection: pending queues never enter the official Lesson/cache model.
        val official = schedule.flatMap { day ->
            day.lessons.map { lesson ->
                TimelineLesson(day.date, lesson.start, lesson.end,
                    lesson.toWidgetLesson(day.date, wall))
            }
        }
        val pending = pendingSport.distinctBy { it.queueKind to it.queueId }
            .filter { it.start > now && it.end > it.start }
            .filter { it.start.toLocalDateTime(timeZone).date in today..tomorrow }
            .map { booking ->
                val start = booking.start.toLocalDateTime(timeZone)
                val end = booking.end.toLocalDateTime(timeZone)
                TimelineLesson(start.date, start.time, end.time,
                    ScheduleWidgetLesson(
                        subject = booking.sectionName.trim(),
                        start = start.time.toString(),
                        end = end.time.toString(),
                        typeId = 11,
                        teacher = booking.teacherFio.trim().takeUnless { it.isEmpty() },
                        room = booking.roomName.trim().takeIf(String::isNotEmpty),
                        building = null,
                        state = ScheduleWidgetLessonState.UPCOMING,
                        pendingStatus = if (booking.isPrediction) ScheduleWidgetPendingStatus.PREDICTED
                            else ScheduleWidgetPendingStatus.WAITING
                    ))
            }
        val days = (official + pending).groupBy(TimelineLesson::date)
        val todayLessons = days[today].orEmpty().sortedBy(TimelineLesson::start)
        val tomorrowLessons = days[tomorrow].orEmpty().sortedBy(TimelineLesson::start)
        val lessonToShow = lessonToShow(todayLessons, wall, preferences.display.compact.showNextLessonEarly)

        val singleLesson = selectSingleLesson(
            lessons = todayLessons,
            lessonToShow = lessonToShow,
            hideTeacher = preferences.display.compact.hideTeacher
        )
        val list = selectLessonList(
            todayLessons = todayLessons,
            tomorrowLessons = tomorrowLessons,
            wall = wall,
            preferences = preferences
        )

        return ScheduleWidgetSelection(
            snapshot = ScheduleWidgetSnapshot(
                singleLesson = singleLesson,
                lessonList = list,
                singleLessonStyle = preferences.singleLessonStyle,
                lessonListStyle = preferences.lessonListStyle,
                officialFallback = if (pending.isEmpty()) null else select(schedule, now, timeZone, preferences).snapshot,
                pendingValidUntil = pending.minOfOrNull {
                    minOf(wall.instantOf(it.date, it.start), now + PERIODIC_UPDATE_DELAY)
                }?.toString(),
                compactTextSize = preferences.display.compact.textSize,
                fullTextSize = preferences.display.full.textSize
            ),
            nextUpdateDelay = nextUpdateDelay(
                lessons = todayLessons,
                lessonToShow = lessonToShow,
                wall = wall,
                preferences = preferences
            )
        )
    }

    /**
     * [select] at [from] and at every later instant before [until] where its snapshot can change: lesson starts and
     * ends, the compact early switch before an end and its stop before midnight, midnights and pending starts.
     * Adjacent equal snapshots merge into one entry. [select] only reads today and tomorrow of its instant, so
     * [schedule] covers the dates of [from] through the day after the last instant before [until].
     */
    fun timeline(
        schedule: List<DaySchedule>,
        from: Instant,
        until: Instant,
        timeZone: TimeZone,
        preferences: ScheduleWidgetPreferences,
        pendingSport: List<PendingSportBooking> = emptyList(),
    ): ScheduleWidgetTimeline {
        require(from < until) { "Timeline ends at $until, not after its start $from" }
        val starts = listOf(from) + boundaries(schedule, from, until, timeZone, pendingSport)
            .filter { it > from && it < until }
            .sorted()
        val merged = mutableListOf<ScheduleWidgetTimelineEntry>()
        for (start in starts) {
            val snapshot = select(schedule, start, timeZone, preferences, pendingSport).snapshot
            if (merged.lastOrNull()?.snapshot?.withoutValidity() != snapshot.withoutValidity()) {
                merged += ScheduleWidgetTimelineEntry(start, snapshot)
            }
        }
        val timeline = ScheduleWidgetTimeline(generatedAt = from, validUntil = until, entries = merged)
        // pendingValidUntil from select() moves with every instant; an entry's pending rows hold to its own end.
        return timeline.copy(entries = merged.mapIndexed { index, entry ->
            val snapshot = entry.snapshot
            if (snapshot.pendingValidUntil == null) entry
            else entry.copy(snapshot = snapshot.copy(pendingValidUntil = timeline.validityEnd(index).toString()))
        })
    }

    private fun boundaries(
        schedule: List<DaySchedule>,
        from: Instant,
        until: Instant,
        timeZone: TimeZone,
        pendingSport: List<PendingSportBooking>,
    ): Set<Instant> {
        fun instantOf(date: LocalDate, time: LocalTime) = LocalDateTime(date, time).toInstant(timeZone)
        val early = FORWARD_MINUTES.minutes
        val lessons = schedule.flatMap { day ->
            day.lessons.flatMap { lesson ->
                val end = instantOf(day.date, lesson.end)
                listOf(instantOf(day.date, lesson.start), end, end - early)
            }
        }
        val pending = pendingSport.flatMap { booking ->
            val start = booking.start.toLocalDateTime(timeZone)
            val end = instantOf(start.date, booking.end.toLocalDateTime(timeZone).time)
            listOf(booking.start, end, end - early)
        }
        val lastMidnight = until.toLocalDateTime(timeZone).date.plus(1, DateTimeUnit.DAY)
        val midnights = generateSequence(from.toLocalDateTime(timeZone).date.plus(1, DateTimeUnit.DAY)) {
            it.plus(1, DateTimeUnit.DAY)
        }.takeWhile { it <= lastMidnight }
            .flatMap { date -> date.atStartOfDayIn(timeZone).let { sequenceOf(it, it - early) } }
        return (lessons + pending + midnights).toSet()
    }

    private fun ScheduleWidgetSnapshot.withoutValidity() = copy(pendingValidUntil = null)

    private fun selectSingleLesson(
        lessons: List<TimelineLesson>,
        lessonToShow: TimelineLesson?,
        hideTeacher: Boolean,
    ): SingleLessonWidgetContent {
        if (lessons.isEmpty()) {
            return SingleLessonWidgetContent(SingleLessonWidgetKind.EMPTY_TODAY)
        }
        if (lessonToShow == null) {
            return SingleLessonWidgetContent(SingleLessonWidgetKind.NO_MORE_TODAY)
        }

        val index = lessons.indexOf(lessonToShow)
        return SingleLessonWidgetContent(
            kind = SingleLessonWidgetKind.LESSON,
            lesson = lessonToShow.display.withTeacherHidden(hideTeacher),
            remainingLessons = (lessons.lastIndex - index).coerceAtLeast(0)
        )
    }

    private fun selectLessonList(
        todayLessons: List<TimelineLesson>,
        tomorrowLessons: List<TimelineLesson>,
        wall: WallNow,
        preferences: ScheduleWidgetPreferences,
    ): List<ScheduleListWidgetItem> {
        val full = preferences.display.full
        val remainingToday = todayLessons.filter { it.end > wall.time }
        val showTomorrow = full.showTomorrowWhenTodayIsOver && remainingToday.isEmpty()
        val selectedDate = if (showTomorrow) wall.today.plus(1, DateTimeUnit.DAY) else wall.today
        val selectedLessons = if (showTomorrow) {
            tomorrowLessons
        } else if (full.hidePastLessons) {
            remainingToday
        } else {
            todayLessons
        }

        if (selectedLessons.isEmpty()) {
            val kind = when {
                showTomorrow -> ScheduleListWidgetItemKind.EMPTY_TODAY_AND_TOMORROW
                todayLessons.isEmpty() -> ScheduleListWidgetItemKind.EMPTY_TODAY
                else -> ScheduleListWidgetItemKind.NO_MORE_TODAY
            }
            return listOf(ScheduleListWidgetItem(kind))
        }

        val items = mutableListOf<ScheduleListWidgetItem>()
        if (full.showTomorrowWhenTodayIsOver) {
            items += ScheduleListWidgetItem(
                kind = ScheduleListWidgetItemKind.HEADER,
                dateIso = selectedDate.toString(),
                tomorrow = showTomorrow
            )
        }
        items += selectedLessons.map { lesson ->
            ScheduleListWidgetItem(
                kind = ScheduleListWidgetItemKind.LESSON,
                lesson = lesson.display.withTeacherHidden(full.hideTeacher)
            )
        }
        items += ScheduleListWidgetItem(
            kind = ScheduleListWidgetItemKind.END,
            tomorrow = showTomorrow
        )
        return items
    }

    private fun nextUpdateDelay(
        lessons: List<TimelineLesson>,
        lessonToShow: TimelineLesson?,
        wall: WallNow,
        preferences: ScheduleWidgetPreferences,
    ): Duration {
        if (!preferences.smartScheduling) return PERIODIC_UPDATE_DELAY

        val target = if (lessonToShow == null) {
            wall.today.plus(1, DateTimeUnit.DAY).atStartOfDayIn(wall.timeZone)
        } else {
            val isLast = lessonToShow == lessons.lastOrNull()
            val targetTime = if (preferences.display.compact.showNextLessonEarly && !isLast) {
                lessonToShow.end.minusMinutes(FORWARD_MINUTES)
            } else {
                lessonToShow.end
            }
            wall.instantOf(wall.today, targetTime)
        }

        val pendingStart = lessons.filter { it.display.pendingStatus != null && it.start > wall.time }
            .minOfOrNull { wall.instantOf(it.date, it.start) }
        // A compact early switch does not advance the full widget. Its current/past
        // states still change at actual starts/ends, so shared work takes the earliest boundary.
        val fullBoundary = lessons.flatMap { listOf(it.start, it.end) }
            .filter { it > wall.time }
            .minOrNull()?.let { wall.instantOf(wall.today, it) }
        val nextTarget = listOfNotNull(target, pendingStart, fullBoundary).min()
        val delay = nextTarget - wall.now
        return if (delay < MINIMUM_UPDATE_DELAY) MINIMUM_UPDATE_DELAY else delay
    }

    private fun lessonToShow(
        lessons: List<TimelineLesson>,
        wall: WallNow,
        forwardScheduling: Boolean,
    ): TimelineLesson? {
        val regular = lessons.firstOrNull { lesson -> lesson.end > wall.time }
        if (!forwardScheduling || regular == null) return regular

        val forwardedNow = (wall.now + FORWARD_MINUTES.minutes).toLocalDateTime(wall.timeZone)
        if (forwardedNow.date != wall.today) return regular
        return lessons.firstOrNull { lesson -> lesson.end > forwardedNow.time } ?: regular
    }

    private fun Lesson.toWidgetLesson(
        date: LocalDate,
        wall: WallNow,
    ): ScheduleWidgetLesson {
        return ScheduleWidgetLesson(
            subject = subjectName.trim(),
            start = start.toString(),
            end = end.toString(),
            typeId = typeId.raw,
            teacher = teacherFio?.trim()?.takeIf(String::isNotEmpty),
            room = room?.raw?.trim()?.takeIf(String::isNotEmpty),
            building = building?.raw?.trim()?.takeIf(String::isNotEmpty),
            state = stateAt(date, start, end, wall)
        )
    }

    private fun ScheduleWidgetLesson.withTeacherHidden(hidden: Boolean) =
        if (hidden) copy(teacher = null) else this

    private fun stateAt(
        date: LocalDate,
        start: LocalTime,
        end: LocalTime,
        wall: WallNow,
    ): ScheduleWidgetLessonState {
        if (date < wall.today || (date == wall.today && end <= wall.time)) {
            return ScheduleWidgetLessonState.COMPLETED
        }
        if (date == wall.today && start <= wall.time && end > wall.time) {
            return ScheduleWidgetLessonState.CURRENT
        }
        return ScheduleWidgetLessonState.UPCOMING
    }

    private data class TimelineLesson(
        val date: LocalDate,
        val start: LocalTime,
        val end: LocalTime,
        val display: ScheduleWidgetLesson,
    )

    /** [now] read once on the wall of [timeZone]. */
    private class WallNow(val now: Instant, val timeZone: TimeZone) {
        private val local: LocalDateTime = now.toLocalDateTime(timeZone)
        val today: LocalDate get() = local.date
        val time: LocalTime get() = local.time

        fun instantOf(date: LocalDate, time: LocalTime): Instant = LocalDateTime(date, time).toInstant(timeZone)
    }

    /** [minutes] earlier on the 24-hour dial: 00:05 minus 15 is 23:50. */
    private fun LocalTime.minusMinutes(minutes: Long): LocalTime = LocalTime.fromNanosecondOfDay(
        (toNanosecondOfDay() - minutes.minutes.inWholeNanoseconds).mod(1.days.inWholeNanoseconds)
    )

    companion object {
        const val FORWARD_MINUTES = 15L
        val PERIODIC_UPDATE_DELAY: Duration = 7.minutes
        private val MINIMUM_UPDATE_DELAY: Duration = 5.seconds
    }
}
