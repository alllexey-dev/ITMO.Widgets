package dev.alllexey.itmowidgets.feature.schedule.domain.widget

import dev.alllexey.itmowidgets.core.settings.CompactScheduleWidgetSettings
import dev.alllexey.itmowidgets.core.settings.FullScheduleWidgetSettings
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
import kotlinx.datetime.toLocalDateTime

/** Synthetic schedules for the widget and schedule-days tests and the WidgetKit reference fixture. */
internal object ScheduleWidgetTimelineSamples {
    val TODAY: LocalDate = LocalDate(2026, 8, 10)
    val TOMORROW: LocalDate = TODAY.plus(1, DateTimeUnit.DAY)
    val DAY_AFTER: LocalDate = TODAY.plus(2, DateTimeUnit.DAY)
    val ZONE = UtcOffset(hours = 3).asTimeZone()

    /** The end of tomorrow, where the timelines of these tests stop. */
    val END_OF_TOMORROW: Instant = DAY_AFTER.atStartOfDayIn(ZONE)

    val LABELS = SchedulePreviewLabels(
        math = "Математический анализ",
        physics = "Физика",
        programming = "Программирование",
        english = "Английский язык",
        swimming = "Плавание",
        algorithms = "Алгоритмы и структуры данных",
        history = "История",
        teacher = "Иванов Иван Иванович",
    )

    val scenario = SchedulePreviewScenario(ScheduleWidgetSelector())

    /**
     * The reference fixture `fixtures/schedule-widget-timeline-v2.json`: the sample Monday from 12:22 to the end of
     * Tuesday, with a moved room, an online lesson, sport and a queue tomorrow.
     */
    fun fixtureTimeline(): ScheduleWidgetTimeline {
        val from = scenario.instant(SchedulePreviewMoment.LESSON)
        val until = from.toLocalDateTime(ZONE).date.plus(2, DateTimeUnit.DAY).atStartOfDayIn(ZONE)
        return ScheduleWidgetSelector().timeline(scenario.days(LABELS), from, until, ZONE, preferences())
    }

    fun preferences(
        showNextLessonEarly: Boolean = false,
        hideTeacher: Boolean = false,
        hidePastLessons: Boolean = false,
        showTomorrow: Boolean = true,
    ) = ScheduleWidgetPreferences(
        ScheduleWidgetSettings(
            compact = CompactScheduleWidgetSettings(showNextLessonEarly, textSize = WidgetTextSize.NORMAL),
            full = FullScheduleWidgetSettings(hideTeacher, hidePastLessons, showTomorrow, WidgetTextSize.LARGE),
        )
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
