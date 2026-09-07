package dev.alllexey.itmowidgets.feature.schedule.domain.widget

import dev.alllexey.itmowidgets.core.settings.LessonStyle
import dev.alllexey.itmowidgets.core.settings.ScheduleWidgetSettings
import dev.alllexey.itmowidgets.feature.schedule.domain.model.Building
import dev.alllexey.itmowidgets.feature.schedule.domain.model.DaySchedule
import dev.alllexey.itmowidgets.feature.schedule.domain.model.Lesson
import dev.alllexey.itmowidgets.feature.schedule.domain.model.Room
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import javax.inject.Inject

data class SchedulePreviewLabels(
    val history: String,
    val math: String,
    val programming: String,
    val physics: String,
    val teacher: String
)

/** Explicit, deterministic examples. Never a user's schedule or the current academic date. */
class SchedulePreviewScenario @Inject constructor(private val selector: ScheduleWidgetSelector) {
    fun snapshot(settings: ScheduleWidgetSettings, evening: Boolean, labels: SchedulePreviewLabels): ScheduleWidgetSnapshot {
        val today = day(
            DATE,
            lesson(1, "09:30", "11:00", labels.history, labels.teacher),
            lesson(2, "11:30", "13:00", labels.math, labels.teacher),
            lesson(3, "13:30", "15:00", labels.programming, labels.teacher)
        )
        val tomorrow = day(
            DATE.plusDays(1),
            lesson(4, "10:00", "11:30", labels.physics, labels.teacher),
            lesson(5, "11:40", "13:10", labels.programming, labels.teacher)
        )
        return selector.select(
            schedule = listOf(today, tomorrow),
            now = OffsetDateTime.of(DATE, LocalTime.of(if (evening) 18 else 12, if (evening) 0 else 50), ZoneOffset.ofHours(3)),
            preferences = ScheduleWidgetPreferences(
                smartScheduling = true,
                forwardScheduling = settings.showNextLessonEarly,
                hideTeacher = settings.hideTeacher,
                hidePreviousLessons = settings.hidePastLessons,
                showTomorrowWhenFinished = settings.showTomorrowWhenTodayIsOver,
                singleLessonStyle = LessonStyle.DOT,
                lessonListStyle = LessonStyle.DOT
            )
        ).snapshot
    }

    private fun day(date: LocalDate, vararg lessons: Lesson) =
        DaySchedule(date.dayOfWeek.value, 1, date, null, lessons.toList())

    private fun lesson(id: Long, start: String, end: String, title: String, teacher: String) = Lesson(
        pairId = id,
        start = LocalTime.parse(start),
        end = LocalTime.parse(end),
        type = "",
        typeId = Lesson.TypeId(if (id == 3L) 2 else 1),
        note = null,
        subjectName = title,
        subjectId = id,
        groupName = "",
        flowId = id,
        flowTypeId = 2,
        teacherIsu = null,
        teacherFio = teacher,
        room = Room("1506"),
        building = Building("Кронверкский проспект, 49"),
        buildingId = null,
        mainBuildingId = null,
        format = "",
        formatId = 1,
        zoomUrl = null,
        zoomPassword = null,
        zoomInfo = null
    )

    private companion object {
        val DATE: LocalDate = LocalDate.of(2026, 9, 7)
    }
}
