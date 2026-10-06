package dev.alllexey.itmowidgets.feature.schedule.ui.home.preview

import dev.alllexey.itmowidgets.core.home.HomeCard
import dev.alllexey.itmowidgets.core.home.HomeLessonState
import dev.alllexey.itmowidgets.core.home.HomeScheduleRow
import dev.alllexey.itmowidgets.core.navigation.LessonDetailsArgs
import dev.alllexey.itmowidgets.core.navigation.toDetailsArgs
import dev.alllexey.itmowidgets.core.schedule.LessonSlot
import dev.alllexey.itmowidgets.core.schedule.ScheduleChange
import dev.alllexey.itmowidgets.core.schedule.ScheduleChangeField
import dev.alllexey.itmowidgets.core.schedule.ScheduleChangeKind
import dev.alllexey.itmowidgets.core.sport.PendingSportBooking
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant

/**
 * Synthetic schedule home cards for the previews and host tests, the debug host's `HomeFixture` on Monday
 * 7 September 2026 in Moscow time: the default feed's, the same with long names, and the card's day states. No real
 * person, subject list or pass.
 */
internal object ScheduleHomePreviewSamples {

    val zone: TimeZone = TimeZone.of("Europe/Moscow")
    private val date = LocalDate(2026, 9, 7)

    const val LONG_SUBJECT = "Проектирование и разработка распределённых информационных систем реального времени"

    /** Today's lessons with a pending sport lesson, and three unread changes. */
    fun cards(): List<HomeCard> = listOf(
        HomeCard.Schedule(date = date, tomorrow = false, rows = scheduleRows(), completed = 1),
        HomeCard.ScheduleChanges(unread = 3, latest = change("Математический анализ")),
    )

    /** Long subject names, every badge, and many unread changes. */
    fun longNameCards(): List<HomeCard> = listOf(
        HomeCard.Schedule(
            date = date,
            tomorrow = false,
            rows = listOf(
                HomeScheduleRow.Lesson(lesson(1, "09:30", "11:00", LONG_SUBJECT, typeId = 5), HomeLessonState.CURRENT, progress = 0.2f),
                HomeScheduleRow.Lesson(lesson(2, "11:20", "12:50", LONG_SUBJECT, typeId = 10), HomeLessonState.NEXT),
                HomeScheduleRow.PendingSport(booking(1, 13, prediction = true).toDetailsArgs(zone), predicted = true),
            ),
            completed = 3,
        ),
        HomeCard.ScheduleChanges(unread = 12, latest = change(LONG_SUBJECT)),
    )

    /** Tomorrow's card, an empty day and a day that is over. */
    fun scheduleStates(): List<HomeCard> = listOf(
        HomeCard.Schedule(date.plus(DatePeriod(days = 1)), tomorrow = true, rows = listOf(scheduleRows().first()), completed = 0),
        HomeCard.Schedule(date, tomorrow = false, rows = emptyList(), completed = 0),
        HomeCard.Schedule(date, tomorrow = false, rows = emptyList(), completed = 4),
    )

    private fun scheduleRows() = listOf(
        HomeScheduleRow.Lesson(lesson(1, "09:30", "11:00", "Математический анализ"), HomeLessonState.CURRENT, progress = 0.55f),
        HomeScheduleRow.Lesson(lesson(2, "11:20", "12:50", "Дискретная математика и основы алгоритмов", 3), HomeLessonState.UPCOMING),
        HomeScheduleRow.PendingSport(booking(1, 12).toDetailsArgs(zone), predicted = false),
        HomeScheduleRow.Lesson(lesson(3, "13:30", "15:00", "Физика", 2, room = null), HomeLessonState.UPCOMING),
    )

    private fun lesson(pairId: Long, start: String, end: String, subject: String, typeId: Int = 1, room: String? = "1506") =
        LessonDetailsArgs(
            pairId = pairId, date = date.toString(), subjectName = subject, typeId = typeId, format = "Очный",
            start = start, end = end, teacherFio = "Преподаватель Тестовый", teacherIsu = 300001, room = room,
            building = "Кронверкский проспект, 49", buildingId = 13, mainBuildingId = 13, note = null,
            zoomUrl = null, zoomPassword = null, zoomInfo = null,
        )

    private fun booking(id: Long, hour: Int, prediction: Boolean = false) = PendingSportBooking(
        queueId = id, queueKind = PendingSportBooking.QueueKind.AUTO, lessonId = 100 + id, sectionName = "Плавание",
        start = LocalDateTime(date, LocalTime(hour, 0)).toInstant(zone),
        end = LocalDateTime(date, LocalTime(hour + 1, 30)).toInstant(zone),
        teacherFio = "Тренер Тестовый", roomName = "Бассейн", isPrediction = prediction,
    )

    /** A Tuesday lesson moved to Wednesday 10:00, found that morning. */
    private fun change(subject: String) = ScheduleChange(
        id = "preview-1",
        detectedAt = LocalDateTime(date, LocalTime(9, 0)).toInstant(zone),
        kind = ScheduleChangeKind.UPDATED,
        fields = setOf(ScheduleChangeField.TIME),
        subjectName = subject,
        typeId = 1,
        flowName = "МАТ АН 1.1",
        before = slot(date.plus(DatePeriod(days = 1))),
        after = slot(date.plus(DatePeriod(days = 2))),
        read = false,
        notified = true,
    )

    private fun slot(day: LocalDate) = LessonSlot(
        pairId = 1, date = day, start = LocalTime(10, 0), end = LocalTime(11, 30), room = "1506",
        building = "Кронверкский проспект, 49", formatId = 1, format = "Очный", teacherIsu = 300001,
        teacherName = "Преподаватель Тестовый",
    )
}
