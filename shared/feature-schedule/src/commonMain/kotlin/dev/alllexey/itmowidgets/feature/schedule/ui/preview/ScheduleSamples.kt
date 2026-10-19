package dev.alllexey.itmowidgets.feature.schedule.ui.preview

import dev.alllexey.itmowidgets.core.lesson.DayLesson
import dev.alllexey.itmowidgets.core.lesson.LessonChange
import dev.alllexey.itmowidgets.core.lesson.LessonDay
import dev.alllexey.itmowidgets.core.lesson.LessonPill
import dev.alllexey.itmowidgets.core.lesson.LessonPillKind
import dev.alllexey.itmowidgets.core.lesson.LessonTarget
import dev.alllexey.itmowidgets.core.lesson.QueuePlace
import dev.alllexey.itmowidgets.core.lesson.SportMark
import dev.alllexey.itmowidgets.core.model.UserGroup
import dev.alllexey.itmowidgets.core.model.UserSharing
import dev.alllexey.itmowidgets.core.model.UserSummary
import dev.alllexey.itmowidgets.core.navigation.AppRoutes
import dev.alllexey.itmowidgets.core.navigation.AppTab
import dev.alllexey.itmowidgets.core.navigation.LessonDetailsArgs
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.schedule.ROOM_BOOKING_TYPE_ID
import dev.alllexey.itmowidgets.core.text.ScheduleChangeLine
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.core.text.personInitials
import dev.alllexey.itmowidgets.core.preview.PreviewFixtures
import dev.alllexey.itmowidgets.feature.schedule.presentation.feed.FeedInput
import dev.alllexey.itmowidgets.feature.schedule.presentation.feed.FeedJump
import dev.alllexey.itmowidgets.feature.schedule.presentation.feed.FeedOwner
import dev.alllexey.itmowidgets.feature.schedule.presentation.feed.ScheduleFeedPresenter
import dev.alllexey.itmowidgets.feature.schedule.presentation.feed.ScheduleFeedUiState
import dev.alllexey.itmowidgets.feature.schedule.presentation.feed.SelectedUser
import dev.alllexey.itmowidgets.feature.schedule.presentation.friends.FriendPickerUiState
import dev.alllexey.itmowidgets.feature.schedule.presentation.friends.PickerFriends
import dev.alllexey.itmowidgets.feature.schedule.presentation.friends.PickerRow
import dev.alllexey.itmowidgets.feature.schedule.presentation.lesson.LessonAction
import dev.alllexey.itmowidgets.feature.schedule.presentation.lesson.LessonActionKind
import dev.alllexey.itmowidgets.feature.schedule.presentation.lesson.LessonSheetChanges
import dev.alllexey.itmowidgets.feature.schedule.presentation.lesson.LessonSheetUiState
import dev.alllexey.itmowidgets.feature.schedule.presentation.lesson.LessonSport
import dev.alllexey.itmowidgets.feature.schedule.presentation.lesson.LessonTeacher
import dev.alllexey.itmowidgets.feature.schedule.presentation.month.ScheduleMonthUiState
import dev.alllexey.itmowidgets.feature.schedule.presentation.month.scheduleMonthState
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.YearMonth
import kotlinx.datetime.plus
import kotlin.time.Instant

/**
 * Synthetic schedules for the schedule's previews and the iOS snapshot tests, through the real presenters: Monday
 * 7 September 2026 at 12:22 in the 2nd (even) week, the weeks around it, a friend's week, the month sheet, the friend
 * picker and the lesson sheet.
 */
object ScheduleSamples {
    private val Monday = LocalDate(2026, 9, 7)
    private val Now = LocalDateTime(Monday, LocalTime(12, 22))
    private val CachedAt = Instant.parse("2026-09-07T08:44:00Z")
    private val WindowEnd = LocalDate(2026, 9, 27)

    private const val Lebedev = "Лебедев Артём Сергеевич"
    private const val Smirnova = "Смирнова Ольга Петровна"
    private const val Ershov = "Ершов Никита Андреевич"
    private const val Petrova = "Петрова Анна Викторовна"
    private const val Zakharov = "Захаров Николай Иванович"
    private const val Filatov = "Филатов Андрей Юрьевич"
    private const val Orlov = "Орлов Дмитрий Викторович"
    private const val Matan = "Математический анализ (продвинутый уровень)"

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
        withYou: Boolean = false,
    ) = DayLesson(
        start = LocalTime.parse(start),
        end = LocalTime.parse(end),
        subject = subject,
        typeId = typeId,
        teacher = teacher,
        room = room,
        building = building,
        online = online,
        target = LessonTarget.Lesson(args(subject, typeId, start, end, teacher, room, building)),
        change = change,
        sport = sport,
        withYou = withYou,
    )

    private fun args(subject: String, typeId: Int, start: String, end: String, teacher: String?, room: String?, building: String?) =
        LessonDetailsArgs(1, Monday.toString(), subject, typeId, "", start, end, teacher, null, room, building, null, null, null, null, null, null)

    private const val Kronva = "Кронверкский пр., 49"
    private const val Lomo = "Ломоносова ул., 9"
    private const val Birzha = "Биржевая линия, 14"

    private val ownDays: List<LessonDay> = buildList {
        fun day(date: LocalDate, week: Int, vararg lessons: DayLesson) = add(LessonDay(date, lessons.toList(), CachedAt, week))
        // The 1st week: the semester starts on Tuesday 1 September.
        (1..4).forEach { offset ->
            day(
                LocalDate(2026, 9, offset),
                1,
                lesson("08:20", "09:50", Matan, 1, Lebedev, "1506", Kronva),
                lesson("10:00", "11:30", "Физика", 3, Smirnova, "1506", Kronva),
            )
        }
        day(
            Monday, 2,
            lesson("08:20", "09:50", Matan, 1, Lebedev, "1506", Kronva),
            lesson(
                "10:00", "11:30", "Физика", 3, Smirnova, "2202", Lomo,
                change = LessonChange(LocalDateTime(2026, 9, 6, 20, 14), previousRoom = "1506"),
            ),
            lesson("11:40", "13:10", "Программирование", 2, Ershov, "1506", Kronva),
            lesson("15:20", "16:50", "Английский язык", 3, Petrova, "дистант", null, online = true),
            lesson("17:00", "18:30", "Плавание", 11, Zakharov, "бассейн", Lomo, sport = SportMark.BOOKED),
        )
        day(
            Monday.plus(1, DateTimeUnit.DAY), 2,
            lesson("08:20", "09:50", "Алгоритмы и структуры данных", 1, Filatov, "2337", Kronva),
            lesson("11:40", "13:10", "История", 1, Orlov, "426", Birzha),
            lesson("15:20", "16:50", "Плавание", 11, Zakharov, "бассейн", null, sport = SportMark.QUEUED).copy(queue = QueuePlace(2, 5)),
        )
        day(
            Monday.plus(2, DateTimeUnit.DAY), 2,
            lesson("08:20", "09:50", PreviewFixtures.LongSubjectName, 3, Lebedev, "Актовый зал", Kronva),
            lesson("10:00", "11:30", "Физика", 2, Smirnova, "2210", Lomo),
        )
        day(
            Monday.plus(5, DateTimeUnit.DAY), 2,
            lesson(
                "10:00", "11:30", "Проектирование информационных систем", 3, Orlov, "309", Birzha,
                change = LessonChange(LocalDateTime(2026, 9, 6, 20, 14), cancelled = true),
            ),
        )
        day(
            LocalDate(2026, 9, 14), 3,
            lesson("08:20", "09:50", Matan, 1, Lebedev, "1506", Kronva),
            lesson("10:00", "11:30", "Физика", 3, Smirnova, "1506", Kronva),
            lesson("15:20", "16:50", "Английский язык", 3, Petrova, "дистант", null, online = true),
        )
        day(
            LocalDate(2026, 9, 16), 3,
            lesson("13:30", "15:00", "Проектирование информационных систем", 1, Orlov, "426", Birzha),
        )
        day(
            LocalDate(2026, 9, 17), 3,
            lesson("10:00", "11:30", "Программирование", 1, Ershov, "2337", Kronva),
            lesson("11:40", "13:10", "Английский язык", 3, Petrova, "дистант", null, online = true),
        )
    }

    private val friendDays = listOf(
        LessonDay(
            Monday,
            listOf(
                lesson("08:20", "09:50", Matan, 1, Lebedev, "1506", Kronva, withYou = true),
                lesson("10:00", "11:30", "Дискретная математика", 3, "Соколова Елена Игоревна", "1410", Kronva),
                lesson("15:20", "16:50", "История", 1, Orlov, "426", Birzha),
                lesson("17:00", "18:30", "Плавание", 11, Zakharov, "бассейн", Lomo, withYou = true),
            ),
            CachedAt,
            2,
        ),
        LessonDay(
            Monday.plus(1, DateTimeUnit.DAY),
            listOf(lesson("08:20", "09:50", "Дискретная математика", 1, "Соколова Елена Игоревна", "2337", Kronva)),
            CachedAt,
            2,
        ),
    )

    private val known: Set<LocalDate> =
        generateSequence(LocalDate(2026, 8, 31)) { it.plus(1, DateTimeUnit.DAY) }.takeWhile { it <= LocalDate(2026, 9, 20) }.toSet()

    val Polina = SelectedUser(300002, "Егорова Полина", null, "M3204")

    private fun feed(
        owner: FeedOwner = FeedOwner.Own,
        days: List<LessonDay> = ownDays,
        visible: LocalDate = Monday,
        known: Set<LocalDate> = this.known,
        loading: Boolean = false,
        error: AppError? = null,
        now: LocalDateTime = Now,
    ) = ScheduleFeedPresenter.state(
        // The window starts on the shown week, so the jump lands on the first row and the iOS snapshot is exact.
        FeedInput(now, owner, visible..WindowEnd, days, known, visible, loading, error, denied = null, refreshing = false, jump = FeedJump(visible, 0)),
    )

    /** The own feed on today, Monday. */
    val Feed: ScheduleFeedUiState get() = feed()

    /** Monday and Tuesday shown on Wednesday: their headers are dimmed as past, Wednesday's is today's. */
    val PastDays: ScheduleFeedUiState get() = feed(now = LocalDateTime(Monday.plus(2, DateTimeUnit.DAY), LocalTime(12, 22)))

    /** The own feed on the next week: `Сегодня` shows. */
    val NextWeek: ScheduleFeedUiState get() = feed(visible = LocalDate(2026, 9, 14))

    /** Polina's week: the shared lessons say `Вы`, `Вернуться к своему`. */
    val FriendFeed: ScheduleFeedUiState
        get() = feed(FeedOwner.Friend(Polina), friendDays, known = known.filter { it <= Monday.plus(1, DateTimeUnit.DAY) }.toSet())

    /**
     * Two lessons at once on Monday at 16:30: a lab 15:30-17:00 and a room booking 16:00-20:30. One now marker on the
     * lab, the rail fills once, the booking says `одновременно с «Веб-программирование»`.
     */
    val Overlap: ScheduleFeedUiState
        get() = feed(
            days = listOf(
                LessonDay(
                    Monday,
                    listOf(
                        lesson("15:30", "17:00", "Веб-программирование", 2, "Лазеев Сергей Максимович", "2113", Kronva),
                        lesson("16:00", "20:30", "Коворкинг", ROOM_BOOKING_TYPE_ID, null, "1216", Kronva)
                            .copy(note = "Бронирование рабочего места"),
                    ),
                    CachedAt,
                    2,
                ),
            ),
            now = LocalDateTime(Monday, LocalTime(16, 30)),
        )

    /** Weeks without a lesson (a holiday): short `пар нет` rows and collapsed weekends. */
    val Empty: ScheduleFeedUiState get() = feed(days = emptyList())

    val Loading: ScheduleFeedUiState get() = feed(days = emptyList(), known = emptySet(), loading = true)

    val Failed: ScheduleFeedUiState get() = feed(days = emptyList(), known = emptySet(), error = AppError.Network)

    /** The month sheet on a day with lessons and on a free one. */
    val Month: ScheduleMonthUiState get() = scheduleMonthState(YearMonth(2026, 9), LocalDate(2026, 9, 17), ownDays, Monday)

    val MonthFreeDay: ScheduleMonthUiState get() = scheduleMonthState(YearMonth(2026, 9), LocalDate(2026, 9, 15), ownDays, Monday)

    private fun friend(isu: Int, name: String, group: String, status: String?, open: Boolean = true) = PickerRow(
        user = SelectedUser(isu, name, null, group),
        title = UiText.Dynamic(name),
        person = name,
        initials = personInitials(name),
        avatar = null,
        status = status?.let(UiText::Dynamic),
        selectable = open,
        selected = false,
    )

    private val own = PickerRow(null, UiText.Dynamic("Моё"), "Макаров Алексей", "МА", null, UiText.Dynamic("на паре до 13:10"), true, true)

    val Picker = FriendPickerUiState(
        own,
        PickerFriends.Content(
            listOf(
                friend(300001, "Козлов Иван", "M3205", "на паре до 13:10"),
                friend(300002, "Егорова Полина", "M3204", "окно до 15:20"),
                friend(300003, PreviewFixtures.LongPersonName, "M3203", "пары закончились"),
                friend(300004, "Каримов Тимур", "M3201", null, open = false),
            ),
        ),
    )

    val PickerDisabled = FriendPickerUiState(own, PickerFriends.Disabled)

    val PickerEmpty = FriendPickerUiState(own, PickerFriends.Content(emptyList()))

    private val friendsOnLesson = listOf(
        UserSummary(300001, "Козлов Иван", null, listOf(UserGroup("M3205", 2, "ФИТИП")), UserSharing(sport = true, schedule = true)),
    )

    private fun sheet(
        over: String,
        status: LessonPill?,
        subject: String,
        room: String?,
        time: String,
        address: String?,
        online: Boolean = false,
        actions: List<LessonAction> = emptyList(),
        changes: LessonSheetChanges? = null,
        note: String? = null,
        password: String? = null,
        teacher: LessonTeacher? = null,
        flow: String? = null,
        sport: LessonSport? = null,
        friends: List<UserSummary> = emptyList(),
    ) = LessonSheetUiState(
        UiText.Dynamic(over), status, subject, room?.let(UiText::Dynamic), online, UiText.Dynamic(time), address, actions,
        actionsPending = false, changes, note, password, teacher, flow, sport, friends,
    )

    private val subjectRoute = AppRoutes.TabRoot(AppTab.RECORDBOOK)

    /** The lesson going on now, in a room. */
    val SheetNow = sheet(
        "Лабораторная, пн, 7 сентября",
        LessonPill(LessonPillKind.NOW, UiText.Dynamic("идёт ещё 48 мин")),
        "Программирование",
        "1506",
        "11:40–13:10, 1 ч 30 мин",
        Kronva,
        actions = listOf(
            LessonAction(LessonActionKind.ROUTE, UiText.Dynamic("Маршрут"), route = AppRoutes.CampusMap()),
            LessonAction(LessonActionKind.FRIENDS, UiText.Dynamic("Друзья 1")),
            LessonAction(LessonActionKind.LINKS, UiText.Dynamic("Ссылки 3"), route = subjectRoute),
            LessonAction(LessonActionKind.SUBJECT, UiText.Dynamic("Предмет"), route = subjectRoute),
        ),
        teacher = LessonTeacher(Ershov, 100001, UiText.Dynamic("в основном хвалят")),
        flow = "ПРОГ 2.1",
        friends = friendsOnLesson,
    )

    /** An online lesson with its meeting and password. */
    val SheetOnline = sheet(
        "Практика, пн, 7 сентября",
        LessonPill(LessonPillKind.NEXT, UiText.Dynamic("через 2 ч 58 мин")),
        "Английский язык",
        null,
        "15:20–16:50, 1 ч 30 мин",
        null,
        online = true,
        actions = listOf(LessonAction(LessonActionKind.VIDEO, UiText.Dynamic("Видео"), url = "https://example.org/meet")),
        password = "1234",
        teacher = LessonTeacher(Petrova, 100002, UiText.Dynamic("Преподаватель")),
    )

    /** A lesson moved to another room and time, with a long room name. */
    val SheetChanged = sheet(
        "Практика, ср, 9 сентября",
        LessonPill(LessonPillKind.CHANGED, UiText.Dynamic("изменена")),
        "Физика",
        "Актовый зал",
        "10:00–11:30, 1 ч 30 мин",
        Lomo,
        changes = LessonSheetChanges(
            summary = null,
            lines = listOf(
                ScheduleChangeLine(UiText.Dynamic("Время"), UiText.Dynamic("пт, 11 сентября, 13:30"), UiText.Dynamic("ср, 9 сентября, 10:00")),
                ScheduleChangeLine(UiText.Dynamic("Аудитория"), UiText.Dynamic("1506, Кронва"), UiText.Dynamic("Акт. зал, Ломо")),
            ),
            note = UiText.Dynamic("Изменено вчера в 20:14"),
        ),
        note = "Возьмите лабораторный журнал",
        teacher = LessonTeacher(PreviewFixtures.LongPersonName, 100003, UiText.Dynamic("мнения расходятся")),
        flow = "ФИЗ 2.3",
    )

    /** An own sport queue: the row leads to Спорт. */
    val SheetSport = sheet(
        "Спорт, вт, 8 сентября",
        LessonPill(LessonPillKind.QUEUE, UiText.Dynamic("очередь")),
        "Плавание",
        "бассейн",
        "15:20–16:50, 1 ч 30 мин",
        null,
        teacher = LessonTeacher(Zakharov, 100004, UiText.Dynamic("Преподаватель")),
        sport = LessonSport(UiText.Dynamic("Очередь"), UiText.Dynamic("Запишем сами, когда освободится место")),
    )
}
