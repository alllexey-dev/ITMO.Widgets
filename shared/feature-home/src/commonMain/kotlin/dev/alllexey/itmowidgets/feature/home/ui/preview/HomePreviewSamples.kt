package dev.alllexey.itmowidgets.feature.home.ui.preview

import dev.alllexey.itmowidgets.core.home.HomeCard
import dev.alllexey.itmowidgets.core.home.HomeHint
import dev.alllexey.itmowidgets.core.home.HomeLessonState
import dev.alllexey.itmowidgets.core.home.HomeScheduleRow
import dev.alllexey.itmowidgets.core.model.UserGroup
import dev.alllexey.itmowidgets.core.model.UserSharing
import dev.alllexey.itmowidgets.core.model.UserSummary
import dev.alllexey.itmowidgets.core.navigation.LessonDetailsArgs
import dev.alllexey.itmowidgets.core.navigation.toDetailsArgs
import dev.alllexey.itmowidgets.core.schedule.LessonSlot
import dev.alllexey.itmowidgets.core.schedule.ScheduleChange
import dev.alllexey.itmowidgets.core.schedule.ScheduleChangeField
import dev.alllexey.itmowidgets.core.schedule.ScheduleChangeKind
import dev.alllexey.itmowidgets.core.sport.PendingSportBooking
import dev.alllexey.itmowidgets.core.sport.SportScoreSummary
import dev.alllexey.itmowidgets.feature.home.presentation.HomeCardFormatter
import dev.alllexey.itmowidgets.feature.home.presentation.HomeCardUi
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.toInstant

/**
 * Synthetic feeds for the previews and host tests, the debug host's `HomeFixture` on Monday 7 September 2026 in
 * Moscow time: every card kind, and the same with long names. They go through [HomeCardFormatter] as the
 * ViewModel's cards do. No real person, subject list or pass.
 */
internal object HomePreviewSamples {

    val zone: TimeZone = TimeZone.of("Europe/Moscow")
    private val date = LocalDate(2026, 9, 7)
    private val formatter = HomeCardFormatter(zone)

    const val LONG_SUBJECT = "Проектирование и разработка распределённых информационных систем реального времени"

    /** The feed in feed order: schedule, changes, marks, sport, friend requests, the widgets hint. */
    fun cards(): List<HomeCardUi> = listOf(
        schedule(),
        HomeCard.ScheduleChanges(unread = 3, latest = change("Математический анализ")),
        HomeCard.Marks(listOf("Базы данных", "Дискретная математика", "Алгоритмы и структуры данных")),
        HomeCard.Sport(SportScoreSummary(attendances = 50, bonus = 22), listOf(booking(1, 16), booking(2, 18, prediction = true))),
        HomeCard.FriendRequests(listOf(user(300001, "Александра Константинопольская"), user(300002, "Иван Петров"))),
        HomeCard.Hint(HomeHint.WIDGETS),
    ).map(formatter::format)

    /** The same kinds with long subject, section and person names, more rows than a card shows, every badge. */
    fun longNameCards(): List<HomeCardUi> = listOf(
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
        HomeCard.Marks(listOf(LONG_SUBJECT, "Теория вероятностей и математическая статистика", "Физика", "Химия")),
        HomeCard.Sport(
            SportScoreSummary(attendances = 30, bonus = 5),
            listOf(
                booking(1, 10).copy(sectionName = "Оздоровительная физическая культура для начинающих"),
                booking(2, 12, prediction = true),
                booking(3, 14),
                booking(4, 16),
                booking(5, 18),
            ),
        ),
        HomeCard.FriendRequests(
            listOf(
                user(300001, "Александра-Виктория Константинопольская-Преображенская"),
                user(300002, "Иван Петров"),
                user(300003, "Мария Иванова"),
                user(300004, "Пётр Сидоров"),
            ),
        ),
        HomeCard.Hint(HomeHint.NOTIFICATIONS),
    ).map(formatter::format)

    /** Tomorrow's card, an empty day and a day that is over. */
    fun scheduleStates(): List<HomeCardUi> = listOf(
        HomeCard.Schedule(date.plus(DatePeriod(days = 1)), tomorrow = true, rows = listOf(scheduleRows().first()), completed = 0),
        HomeCard.Schedule(date, tomorrow = false, rows = emptyList(), completed = 0),
        HomeCard.Schedule(date, tomorrow = false, rows = emptyList(), completed = 4),
    ).map(formatter::format)

    /** Every hint, and a sport card without a score. */
    fun hintsAndQueue(): List<HomeCardUi> = listOf(
        HomeCard.Sport(score = null, queue = listOf(booking(1, 16, prediction = true))),
        HomeCard.Hint(HomeHint.WIDGETS),
        HomeCard.Hint(HomeHint.NOTIFICATIONS),
        HomeCard.Hint(HomeHint.SERVICES),
    ).map(formatter::format)

    private fun schedule() = HomeCard.Schedule(date = date, tomorrow = false, rows = scheduleRows(), completed = 1)

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

    private fun user(isu: Int, name: String) = UserSummary(
        isu = isu, name = name, pictureUrl = null,
        groups = listOf(UserGroup("M3100", 1, "ФИТиП")), sharing = UserSharing(sport = true, schedule = true),
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
