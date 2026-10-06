package dev.alllexey.itmowidgets.feature.schedule.ui.list.preview

import dev.alllexey.itmowidgets.core.sport.PendingSportBooking
import dev.alllexey.itmowidgets.feature.schedule.domain.model.Building
import dev.alllexey.itmowidgets.feature.schedule.domain.model.DaySchedule
import dev.alllexey.itmowidgets.feature.schedule.domain.model.Lesson
import dev.alllexey.itmowidgets.feature.schedule.domain.model.Room
import dev.alllexey.itmowidgets.feature.schedule.presentation.ScheduleDayUi
import dev.alllexey.itmowidgets.feature.schedule.presentation.ScheduleDisplayDay
import dev.alllexey.itmowidgets.feature.schedule.presentation.SelectedUser
import dev.alllexey.itmowidgets.feature.schedule.presentation.buildScheduleListUi
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant

/**
 * Synthetic schedule for the previews, on the XML references' clock (LS-0, `ScheduleReferenceFixtures`): Monday,
 * 7 September 2026, 12:00 in Moscow. The 8:20 lesson is over, 10:00 is over and changed, 11:40 is in progress, 15:20
 * is next and has a link, two auto-sign rows follow; tomorrow is upcoming.
 */
internal object ScheduleListPreviewData {

    val today: LocalDate = LocalDate(2026, 9, 7)
    val now: LocalDateTime = today.atTime(12, 0)
    val timeZone: TimeZone = TimeZone.of("Europe/Moscow")

    private const val TEACHER = "Тестовый преподаватель с очень длинным именем"
    private const val CHANGED_PAIR_ID = 2L

    val friend = SelectedUser(isu = 300100, name = "Тестовая подруга Константинопольская-Преображенская", avatar = null)

    fun lesson(
        pairId: Long,
        start: LocalTime,
        subject: String,
        typeId: Int,
        teacher: String? = TEACHER,
        room: String? = "1506",
        building: String? = "Кронверкский проспект, 49",
        note: String? = null,
        zoomUrl: String? = null,
    ) = Lesson(
        pairId = pairId, start = start, end = LocalTime.fromSecondOfDay(start.toSecondOfDay() + LESSON_SECONDS),
        type = "", typeId = Lesson.TypeId(typeId), note = note, subjectName = subject, subjectId = pairId,
        groupName = "ФИЗ ПИИКТ 3.2", flowId = 1, flowTypeId = 2, teacherIsu = null, teacherFio = teacher,
        room = room?.let(::Room), building = building?.let(::Building), buildingId = 13, mainBuildingId = 13,
        format = "Очный", formatId = 1, zoomUrl = zoomUrl, zoomPassword = null, zoomInfo = null,
    )

    private fun booking(queueId: Long, kind: PendingSportBooking.QueueKind, start: LocalTime, isPrediction: Boolean) =
        PendingSportBooking(
            queueId = queueId,
            queueKind = kind,
            lessonId = queueId * 100,
            sectionName = "Современные танцы: тестовая секция с длинным названием",
            start = today.atTime(start).toInstant(timeZone),
            end = today.atTime(LocalTime.fromSecondOfDay(start.toSecondOfDay() + LESSON_SECONDS)).toInstant(timeZone),
            teacherFio = TEACHER,
            roomName = "Кронверкский проспект, 49, спортивный зал",
            isPrediction = isPrediction,
        )

    private fun day(date: LocalDate, vararg lessons: Lesson) =
        DaySchedule(dayNumber = date.dayOfWeek.ordinal + 1, weekNumber = 1, date = date, note = null, lessons = lessons.toList())

    private val yesterday = ScheduleDisplayDay(
        today.minus(1, DateTimeUnit.DAY),
        day(today.minus(1, DateTimeUnit.DAY), lesson(10, LocalTime(10, 0), "Физическая культура", 11)),
    )

    private val todayDay = ScheduleDisplayDay(
        today,
        day(
            today,
            lesson(1, LocalTime(8, 20), "Математический анализ (продвинутый уровень)", 1,
                note = "Организационная информация о занятии"),
            lesson(CHANGED_PAIR_ID, LocalTime(10, 0), "Физика", 3, room = "2202", building = "ул. Ломоносова, 9"),
            lesson(3, LocalTime(11, 40), "Программирование", 2),
            lesson(4, LocalTime(15, 20), "Английский язык", 3, teacher = null, room = null, building = null,
                zoomUrl = "https://example.invalid/meeting"),
        ),
        pendingSport = listOf(
            booking(1, PendingSportBooking.QueueKind.FREE, LocalTime(17, 0), isPrediction = false),
            booking(2, PendingSportBooking.QueueKind.AUTO, LocalTime(18, 40), isPrediction = true),
        ),
        changedPairIds = setOf(CHANGED_PAIR_ID),
    )

    private val tomorrow = ScheduleDisplayDay(
        today.plus(1, DateTimeUnit.DAY),
        day(
            today.plus(1, DateTimeUnit.DAY),
            lesson(5, LocalTime(8, 20), "Дискретная математика", 1),
            lesson(6, LocalTime(10, 0), "Базы данных", 2),
            lesson(7, LocalTime(11, 40), "Экзамен по базам данных", 5, room = "Актовый зал", building = "ул. Чайковского, 11"),
        ),
    )

    /** Only auto-sign rows: the pill says so and the rows count no lessons. */
    private val autoSignOnly = ScheduleDisplayDay(
        today.plus(2, DateTimeUnit.DAY),
        officialDay = null,
        pendingSport = listOf(
            booking(3, PendingSportBooking.QueueKind.FREE, LocalTime(10, 0), isPrediction = false).let {
                it.copy(
                    start = today.plus(2, DateTimeUnit.DAY).atTime(10, 0).toInstant(timeZone),
                    end = today.plus(2, DateTimeUnit.DAY).atTime(11, 30).toInstant(timeZone),
                    teacherFio = "",
                    roomName = "",
                )
            },
        ),
    )

    private val emptyDay = ScheduleDisplayDay(today.plus(3, DateTimeUnit.DAY), day(today.plus(3, DateTimeUnit.DAY)))

    /** A blank subject, an unknown building spelled out in full and a consultation without a teacher. */
    private val longDay = ScheduleDisplayDay(
        today.plus(4, DateTimeUnit.DAY),
        day(
            today.plus(4, DateTimeUnit.DAY),
            lesson(8, LocalTime(9, 0), "", 10, teacher = null, building = "Биржевая линия, 14-16"),
            lesson(9, LocalTime(13, 30), "Иностранный язык в профессиональной деятельности (английский язык)", 6,
                room = "Аудитория 2311/1", building = "Новое здание на очень длинной улице"),
        ),
    )

    val ownDays: List<ScheduleDisplayDay> = listOf(yesterday, todayDay, tomorrow)

    val friendDays: List<ScheduleDisplayDay> = listOf(
        ScheduleDisplayDay(
            today,
            day(
                today,
                lesson(21, LocalTime(10, 0), "История России", 1),
                lesson(22, LocalTime(11, 40), "Иностранный язык в профессиональной деятельности", 3),
                lesson(23, LocalTime(13, 30), "Алгоритмы и структуры данных", 2),
            ),
        ),
        ScheduleDisplayDay(
            today.plus(1, DateTimeUnit.DAY),
            day(
                today.plus(1, DateTimeUnit.DAY),
                lesson(24, LocalTime(8, 20), "Линейная алгебра", 1),
                lesson(25, LocalTime(10, 0), "Операционные системы", 2),
            ),
        ),
    )

    private val all = listOf(yesterday, todayDay, tomorrow, autoSignOnly, emptyDay, longDay)

    fun days(display: List<ScheduleDisplayDay>): List<ScheduleDayUi> = buildScheduleListUi(display, now, timeZone)

    /** One day resolved among all preview days, so NEXT lands where it does in the list. */
    fun dayOf(date: LocalDate): ScheduleDayUi = days(all).single { it.date == date }

    private const val LESSON_SECONDS = 90 * 60
}
