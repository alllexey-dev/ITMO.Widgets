package dev.alllexey.itmowidgets.feature.home.ui.preview

import dev.alllexey.itmowidgets.core.bell.BellItem
import dev.alllexey.itmowidgets.core.bell.BellKind
import dev.alllexey.itmowidgets.core.lesson.DayLesson
import dev.alllexey.itmowidgets.core.lesson.LessonChange
import dev.alllexey.itmowidgets.core.lesson.LessonDay
import dev.alllexey.itmowidgets.core.lesson.LessonTarget
import dev.alllexey.itmowidgets.core.lesson.SportMark
import dev.alllexey.itmowidgets.core.navigation.AppTab
import dev.alllexey.itmowidgets.core.navigation.EntryRoute
import dev.alllexey.itmowidgets.core.navigation.LessonDetailsArgs
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.text.AppIcon
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.core.today.TodayTodo
import dev.alllexey.itmowidgets.designsystem.preview.PreviewFixtures
import dev.alllexey.itmowidgets.feature.home.presentation.BellRow
import dev.alllexey.itmowidgets.feature.home.presentation.BellUiState
import dev.alllexey.itmowidgets.feature.home.presentation.TodayPresenter
import dev.alllexey.itmowidgets.feature.home.presentation.TodayRefresh
import dev.alllexey.itmowidgets.feature.home.presentation.TodayUiState
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlin.time.Instant

/** Synthetic days for Сегодня's previews: Monday 7 September 2026 with five lessons, Tuesday with three. */
internal object TodaySamples {
    private val Monday = LocalDate(2026, 9, 7)
    private val Tuesday = LocalDate(2026, 9, 8)
    private val Zone = TimeZone.of("Europe/Moscow")

    private fun lesson(
        start: String,
        end: String,
        subject: String,
        typeId: Int,
        teacher: String?,
        room: String?,
        building: String?,
        online: Boolean = false,
        change: LessonChange? = null,
        sport: SportMark? = null,
    ) = DayLesson(
        start = LocalTime.parse(start),
        end = LocalTime.parse(end),
        subject = subject,
        typeId = typeId,
        teacher = teacher,
        room = room,
        building = building,
        online = online,
        target = LessonTarget.Lesson(
            LessonDetailsArgs(1, Monday.toString(), subject, typeId, "", start, end, teacher, null, room, building, null, null, null, null, null, null),
        ),
        videoUrl = if (online) "https://example.org/meet" else null,
        change = change,
        sport = sport,
    )

    private val today = listOf(
        lesson("08:20", "09:50", "Физика", 1, "Смирнова Елена Андреевна", "331", "Ломоносова ул., 9"),
        lesson(
            "10:00", "11:30", "Базы данных", 2, "Орлова Наталья Викторовна", "2202", "Кронверкский пр., 49",
            change = LessonChange(LocalDateTime(2026, 9, 6, 20, 14), previousRoom = "1506"),
        ),
        lesson("11:40", "13:10", PreviewFixtures.LongSubjectName, 3, "Лебедев Алексей Сергеевич", "1506", "Кронверкский пр., 49"),
        lesson("15:20", "16:50", "Английский язык", 3, "Петрова Анна Викторовна", null, null, online = true),
        lesson("17:00", "18:30", "Волейбол", 11, "Захаров Николай Иванович", "спортзал", "Ломоносова ул., 9", sport = SportMark.BOOKED),
    )

    private val tomorrow = listOf(
        lesson("10:00", "11:30", "Алгоритмы и структуры данных", 1, "Белов Павел Игоревич", "1404", "Биржевая линия, 14"),
        lesson("11:40", "13:10", "История", 3, "Орлова Наталья Сергеевна", "311", "Чайковского ул., 11"),
        lesson("15:20", "16:50", "Плавание", 11, "Захаров Николай Иванович", "бассейн", "Ломоносова ул., 9", sport = SportMark.QUEUED),
    )

    private val days = listOf(
        LessonDay(Monday, today, Instant.parse("2026-09-07T08:44:00Z")),
        LessonDay(Tuesday, tomorrow, Instant.parse("2026-09-07T08:44:00Z")),
    )

    val BarsTodo = TodayTodo(
        id = "bars-sign-in",
        icon = AppIcon.LOGIN,
        title = UiText.Dynamic("Войдите в БАРС заново"),
        reason = UiText.Dynamic("Сессия закончилась, баллы БАРС не обновляются с 09:40"),
        action = UiText.Dynamic("Войти"),
        target = EntryRoute(AppTab.HOME),
    )

    fun at(time: String, todos: List<TodayTodo> = emptyList(), refresh: TodayRefresh = TodayRefresh.Done): TodayUiState =
        TodayPresenter.state(LocalDateTime(Monday, LocalTime.parse(time)), days, todos, refresh, refreshing = false, Zone)

    val Empty: TodayUiState = TodayPresenter.state(
        LocalDateTime(Monday, LocalTime(9, 0)), emptyList(), emptyList(), TodayRefresh.Done, refreshing = false, Zone,
    )

    val Failed: TodayUiState = TodayUiState.Failed(AppError.Network, retrying = false)

    private val at = Instant.parse("2026-09-07T09:05:00Z")

    private val mark = BellItem(
        id = "mark", kind = BellKind.MARK, at = at,
        title = UiText.Dynamic("Матанализ: контрольная работа 2"), text = UiText.Dynamic("9 из 10, итого 74"), lead = "+9",
    )
    private val request = BellItem(
        id = "friend", kind = BellKind.FRIEND_REQUEST, at = null,
        title = UiText.Dynamic("${PreviewFixtures.LongPersonName} хочет добавить вас в друзья"), text = UiText.Dynamic("M3205"),
        lead = PreviewFixtures.LongPersonName, request = 123456,
    )
    private val sport = BellItem(
        id = "sport", kind = BellKind.SPORT, at = at,
        title = UiText.Dynamic("Волейбол: вы записаны"), text = UiText.Dynamic("Очередь на чт, 10 сентября, 15:20"),
    )
    private val change = BellItem(
        id = "change", kind = BellKind.SCHEDULE_CHANGE, at = at,
        title = UiText.Dynamic("Физика: пара перенесена"),
        was = UiText.Dynamic("чт, 10 сент., 10:00, 1506, Кронва"), now = UiText.Dynamic("ср, 9 сент., 10:00, 2202, Кронва"),
    )
    private val oldMark = BellItem(
        id = "old-mark", kind = BellKind.MARK, at = Instant.parse("2026-09-06T12:00:00Z"),
        title = UiText.Dynamic("ППРИС: лабораторная 3"), text = UiText.Dynamic("18 из 20, итого 63,5"), lead = "+18",
    )
    private val refused = BellItem(
        id = "refused", kind = BellKind.SPORT, at = Instant.parse("2026-09-06T12:00:00Z"),
        title = UiText.Dynamic("Плавание: не удалось записать"), text = UiText.Dynamic("Очередь на вт, 8 сентября, 15:20"),
        failed = true,
    )

    val BellNew = BellUiState(
        loading = false,
        fresh = listOf(request, mark, sport, change).map { BellRow(it, new = true, time = it.at?.let { UiText.Dynamic("12:05") }) },
        earlier = listOf(oldMark, refused).map { BellRow(it, new = false, time = UiText.Dynamic("вчера")) },
    )

    val BellRead = BellUiState(
        loading = false,
        earlier = listOf(mark, change, oldMark).map { BellRow(it, new = false, time = UiText.Dynamic("12:05")) },
    )

    val BellEmpty = BellUiState(loading = false)
}
