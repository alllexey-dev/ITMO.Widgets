package dev.alllexey.itmowidgets.feature.schedule.domain.widget

import dev.alllexey.itmowidgets.core.settings.CompactScheduleWidgetSettings
import dev.alllexey.itmowidgets.core.settings.FullScheduleWidgetSettings
import dev.alllexey.itmowidgets.core.settings.LessonStyle
import dev.alllexey.itmowidgets.core.settings.ScheduleWidgetSettings
import dev.alllexey.itmowidgets.core.settings.WidgetTextSize
import dev.alllexey.itmowidgets.core.sport.PendingSportBooking
import dev.alllexey.itmowidgets.feature.schedule.domain.model.DaySchedule
import dev.alllexey.itmowidgets.feature.schedule.domain.model.Lesson
import dev.alllexey.itmowidgets.feature.schedule.domain.model.Room
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.UtcOffset
import kotlinx.datetime.asTimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant

/** Synthetic schedules for the widget timeline tests and the WidgetKit reference fixture. */
internal object ScheduleWidgetTimelineSamples {
    val TODAY: LocalDate = LocalDate(2026, 8, 10)
    val TOMORROW: LocalDate = TODAY.plus(1, DateTimeUnit.DAY)
    val DAY_AFTER: LocalDate = TODAY.plus(2, DateTimeUnit.DAY)
    val ZONE = UtcOffset(hours = 3).asTimeZone()

    /** The end of tomorrow, where the timelines of these tests stop. */
    val END_OF_TOMORROW: Instant = DAY_AFTER.atStartOfDayIn(ZONE)

    /**
     * The reference fixture `fixtures/schedule-widget-timeline-v1.json`: two lessons with a break, a predicted pending
     * sport row and one lesson tomorrow, generated at 10:00:30.25 today.
     */
    fun fixtureTimeline(): ScheduleWidgetTimeline = ScheduleWidgetSelector().timeline(
        schedule = listOf(
            day(
                TODAY,
                lesson(1, "10:00", "11:30", "Математика", teacher = "Тестовый преподаватель", room = "1404"),
                lesson(2, "11:40", "13:10", "Физика")
            ),
            day(TOMORROW, lesson(3, "08:20", "09:50", "Алгоритмы")),
        ),
        from = at(TODAY, "10:00:30.25"),
        until = END_OF_TOMORROW,
        timeZone = ZONE,
        preferences = preferences(forwardScheduling = true, showTomorrowWhenFinished = true),
        pendingSport = listOf(pending(7, TODAY, "15:30", prediction = true)),
    )

    fun preferences(
        forwardScheduling: Boolean = false,
        hideTeacher: Boolean = false,
        hidePreviousLessons: Boolean = false,
        showTomorrowWhenFinished: Boolean = false,
    ) = ScheduleWidgetPreferences(
        smartScheduling = true,
        display = ScheduleWidgetSettings(
            compact = CompactScheduleWidgetSettings(forwardScheduling, hideTeacher, WidgetTextSize.NORMAL),
            full = FullScheduleWidgetSettings(
                hideTeacher, hidePreviousLessons, showTomorrowWhenFinished, WidgetTextSize.LARGE
            )
        ),
        singleLessonStyle = LessonStyle.DOT,
        lessonListStyle = LessonStyle.LINE
    )

    fun at(date: LocalDate, time: String): Instant = LocalDateTime(date, LocalTime.parse(time)).toInstant(ZONE)

    fun day(date: LocalDate, vararg lessons: Lesson) = DaySchedule(
        dayNumber = date.dayOfWeek.isoDayNumber,
        weekNumber = 1,
        date = date,
        note = null,
        lessons = lessons.toList()
    )

    fun pending(id: Long, date: LocalDate, start: String, prediction: Boolean = false): PendingSportBooking {
        val startsAt = at(date, start)
        return PendingSportBooking(
            queueId = id,
            queueKind = if (prediction) PendingSportBooking.QueueKind.AUTO else PendingSportBooking.QueueKind.FREE,
            lessonId = if (prediction) -id else id,
            sectionName = "Секция $id",
            start = startsAt,
            end = startsAt + 90.minutes,
            teacherFio = "Тестовый преподаватель",
            roomName = "Тестовый корпус",
            isPrediction = prediction
        )
    }

    fun lesson(
        id: Long,
        start: String,
        end: String,
        subject: String,
        teacher: String? = null,
        room: String? = null,
    ) = Lesson(
        pairId = id,
        start = LocalTime.parse(start),
        end = LocalTime.parse(end),
        type = "Лекция",
        typeId = Lesson.TypeId(1),
        note = null,
        subjectName = subject,
        subjectId = id,
        groupName = "M0000",
        flowId = id,
        flowTypeId = 2,
        teacherIsu = null,
        teacherFio = teacher,
        room = room?.let(::Room),
        building = null,
        buildingId = null,
        mainBuildingId = null,
        format = "Очно",
        formatId = 1,
        zoomUrl = null,
        zoomPassword = null,
        zoomInfo = null
    )
}
