package dev.alllexey.itmowidgets.feature.schedule.domain.widget

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
    ): ScheduleWidgetSelection {
        val today = now.toLocalDate()
        val days = schedule.associateBy(DaySchedule::date)
        val todayLessons = days[today]?.lessons.orEmpty().sortedBy(Lesson::start)
        val tomorrowLessons = days[today.plusDays(1)]
            ?.lessons
            .orEmpty()
            .sortedBy(Lesson::start)
        val lessonToShow = lessonToShow(todayLessons, now, preferences.forwardScheduling)

        val singleLesson = selectSingleLesson(
            lessons = todayLessons,
            lessonToShow = lessonToShow,
            now = now,
            hideTeacher = preferences.hideTeacher
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
                lessonListStyle = preferences.lessonListStyle
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
        lessons: List<Lesson>,
        lessonToShow: Lesson?,
        now: OffsetDateTime,
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
            lesson = lessonToShow.toWidgetLesson(
                date = now.toLocalDate(),
                now = now,
                hideTeacher = hideTeacher
            ),
            remainingLessons = (lessons.lastIndex - index).coerceAtLeast(0)
        )
    }

    private fun selectLessonList(
        todayLessons: List<Lesson>,
        tomorrowLessons: List<Lesson>,
        lessonToShow: Lesson?,
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
                lesson = lesson.toWidgetLesson(
                    date = selectedDate,
                    now = now,
                    hideTeacher = preferences.hideTeacher
                )
            )
        }
        items += ScheduleListWidgetItem(
            kind = ScheduleListWidgetItemKind.END,
            tomorrow = showTomorrow
        )
        return items
    }

    private fun nextUpdateDelay(
        lessons: List<Lesson>,
        lessonToShow: Lesson?,
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

        val delay = Duration.between(now, target)
        return if (delay < MINIMUM_UPDATE_DELAY) MINIMUM_UPDATE_DELAY else delay
    }

    private fun lessonToShow(
        lessons: List<Lesson>,
        now: OffsetDateTime,
        forwardScheduling: Boolean,
    ): Lesson? {
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

    companion object {
        const val FORWARD_MINUTES = 15L
        val PERIODIC_UPDATE_DELAY: Duration = Duration.ofMinutes(7)
        private val MINIMUM_UPDATE_DELAY: Duration = Duration.ofSeconds(5)
    }
}
