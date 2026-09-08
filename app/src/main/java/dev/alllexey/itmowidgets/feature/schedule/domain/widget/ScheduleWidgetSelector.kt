package dev.alllexey.itmowidgets.feature.schedule.domain.widget

import dev.alllexey.itmowidgets.core.sport.PendingSportBooking
import dev.alllexey.itmowidgets.feature.schedule.domain.model.DaySchedule
import dev.alllexey.itmowidgets.feature.schedule.domain.model.Lesson
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime
import javax.inject.Inject

class ScheduleWidgetSelector @Inject constructor() {

    fun select(
        schedule: List<DaySchedule>,
        now: OffsetDateTime,
        preferences: ScheduleWidgetPreferences,
        pendingSport: List<PendingSportBooking> = emptyList(),
    ): ScheduleWidgetSelection {
        val today = now.toLocalDate()
        // Widget-only projection: pending queues never enter the official Lesson/cache model.
        val official = schedule.flatMap { day ->
            day.lessons.map { lesson ->
                TimelineLesson(day.date, lesson.start, lesson.end,
                    lesson.toWidgetLesson(day.date, now, preferences.hideTeacher))
            }
        }
        val pending = pendingSport.distinctBy { it.queueKind to it.queueId }
            .filter { it.start.isAfter(now) && it.end.isAfter(it.start) }
            .filter { it.start.withOffsetSameInstant(now.offset).toLocalDate() in today..today.plusDays(1) }
            .map { booking ->
                val start = booking.start.withOffsetSameInstant(now.offset)
                val end = booking.end.withOffsetSameInstant(now.offset)
                TimelineLesson(start.toLocalDate(), start.toLocalTime(), end.toLocalTime(),
                    ScheduleWidgetLesson(
                        subject = booking.sectionName.trim(),
                        start = start.toLocalTime().toString(),
                        end = end.toLocalTime().toString(),
                        typeId = 11,
                        teacher = booking.teacherFio.trim().takeUnless { preferences.hideTeacher || it.isEmpty() },
                        room = booking.roomName.trim().takeIf(String::isNotEmpty),
                        building = null,
                        state = ScheduleWidgetLessonState.UPCOMING,
                        pendingStatus = if (booking.isPrediction) ScheduleWidgetPendingStatus.PREDICTED
                            else ScheduleWidgetPendingStatus.WAITING
                    ))
            }
        val days = (official + pending).groupBy(TimelineLesson::date)
        val todayLessons = days[today].orEmpty().sortedBy(TimelineLesson::start)
        val tomorrowLessons = days[today.plusDays(1)].orEmpty().sortedBy(TimelineLesson::start)
        val lessonToShow = lessonToShow(todayLessons, now, preferences.forwardScheduling)

        val singleLesson = selectSingleLesson(
            lessons = todayLessons,
            lessonToShow = lessonToShow,
        )
        val list = selectLessonList(
            todayLessons = todayLessons,
            tomorrowLessons = tomorrowLessons,
            lessonToShow = lessonToShow,
            now = now,
            preferences = preferences
        )

        return ScheduleWidgetSelection(
            snapshot = ScheduleWidgetSnapshot(
                singleLesson = singleLesson,
                lessonList = list,
                singleLessonStyle = preferences.singleLessonStyle,
                lessonListStyle = preferences.lessonListStyle,
                officialFallback = if (pending.isEmpty()) null else select(schedule, now, preferences).snapshot,
                pendingValidUntil = pending.minOfOrNull {
                    minOf(OffsetDateTime.of(it.date, it.start, now.offset).toInstant(),
                        now.toInstant().plus(PERIODIC_UPDATE_DELAY))
                }?.toString()
            ),
            nextUpdateDelay = nextUpdateDelay(
                lessons = todayLessons,
                lessonToShow = lessonToShow,
                now = now,
                preferences = preferences
            )
        )
    }

    private fun selectSingleLesson(
        lessons: List<TimelineLesson>,
        lessonToShow: TimelineLesson?,
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
            lesson = lessonToShow.display,
            remainingLessons = (lessons.lastIndex - index).coerceAtLeast(0)
        )
    }

    private fun selectLessonList(
        todayLessons: List<TimelineLesson>,
        tomorrowLessons: List<TimelineLesson>,
        lessonToShow: TimelineLesson?,
        now: OffsetDateTime,
        preferences: ScheduleWidgetPreferences,
    ): List<ScheduleListWidgetItem> {
        val showTomorrow = preferences.showTomorrowWhenFinished &&
            (todayLessons.isEmpty() || lessonToShow == null)
        val selectedDate = if (showTomorrow) now.toLocalDate().plusDays(1) else now.toLocalDate()
        val selectedLessons = if (showTomorrow) {
            tomorrowLessons
        } else if (preferences.hidePreviousLessons) {
            lessonToShow?.let { lesson ->
                todayLessons.drop(todayLessons.indexOf(lesson))
            }.orEmpty()
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
        if (preferences.showTomorrowWhenFinished) {
            items += ScheduleListWidgetItem(
                kind = ScheduleListWidgetItemKind.HEADER,
                dateIso = selectedDate.toString(),
                tomorrow = showTomorrow
            )
        }
        items += selectedLessons.map { lesson ->
            ScheduleListWidgetItem(
                kind = ScheduleListWidgetItemKind.LESSON,
                lesson = lesson.display
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
        now: OffsetDateTime,
        preferences: ScheduleWidgetPreferences,
    ): Duration {
        if (!preferences.smartScheduling) return PERIODIC_UPDATE_DELAY

        val target = if (lessonToShow == null) {
            now.toLocalDate()
                .plusDays(1)
                .atStartOfDay()
                .atOffset(now.offset)
        } else {
            val isLast = lessonToShow == lessons.lastOrNull()
            val targetTime = if (preferences.forwardScheduling && !isLast) {
                lessonToShow.end.minusMinutes(FORWARD_MINUTES)
            } else {
                lessonToShow.end
            }
            OffsetDateTime.of(now.toLocalDate(), targetTime, now.offset)
        }

        val pendingStart = lessons.filter { it.display.pendingStatus != null && it.start > now.toLocalTime() }
            .minOfOrNull { OffsetDateTime.of(it.date, it.start, now.offset) }
        val nextTarget = if (pendingStart != null && pendingStart < target) pendingStart else target
        val delay = Duration.between(now, nextTarget)
        return if (delay < MINIMUM_UPDATE_DELAY) MINIMUM_UPDATE_DELAY else delay
    }

    private fun lessonToShow(
        lessons: List<TimelineLesson>,
        now: OffsetDateTime,
        forwardScheduling: Boolean,
    ): TimelineLesson? {
        val regular = lessons.firstOrNull { lesson -> lesson.end > now.toLocalTime() }
        if (!forwardScheduling || regular == null) return regular

        val forwardedNow = now.plusMinutes(FORWARD_MINUTES)
        if (forwardedNow.toLocalDate() != now.toLocalDate()) return regular
        return lessons.firstOrNull { lesson -> lesson.end > forwardedNow.toLocalTime() } ?: regular
    }

    private fun Lesson.toWidgetLesson(
        date: LocalDate,
        now: OffsetDateTime,
        hideTeacher: Boolean,
    ): ScheduleWidgetLesson {
        return ScheduleWidgetLesson(
            subject = subjectName.trim(),
            start = start.toString(),
            end = end.toString(),
            typeId = typeId.raw,
            teacher = teacherFio?.trim()?.takeUnless { hideTeacher || it.isEmpty() },
            room = room?.raw?.trim()?.takeIf(String::isNotEmpty),
            building = building?.raw?.trim()?.takeIf(String::isNotEmpty),
            state = stateAt(date, start, end, now)
        )
    }

    private fun stateAt(
        date: LocalDate,
        start: LocalTime,
        end: LocalTime,
        now: OffsetDateTime,
    ): ScheduleWidgetLessonState {
        if (date < now.toLocalDate() || (date == now.toLocalDate() && end <= now.toLocalTime())) {
            return ScheduleWidgetLessonState.COMPLETED
        }
        if (date == now.toLocalDate() && start <= now.toLocalTime() && end > now.toLocalTime()) {
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

    companion object {
        const val FORWARD_MINUTES = 15L
        val PERIODIC_UPDATE_DELAY: Duration = Duration.ofMinutes(7)
        private val MINIMUM_UPDATE_DELAY: Duration = Duration.ofSeconds(5)
    }
}
