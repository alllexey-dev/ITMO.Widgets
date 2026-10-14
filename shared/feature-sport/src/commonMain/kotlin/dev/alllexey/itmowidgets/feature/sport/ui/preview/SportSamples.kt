package dev.alllexey.itmowidgets.feature.sport.ui.preview

import dev.alllexey.itmowidgets.core.lesson.DayLesson
import dev.alllexey.itmowidgets.core.lesson.LessonDay
import dev.alllexey.itmowidgets.core.lesson.LessonTarget
import dev.alllexey.itmowidgets.core.model.UserSharing
import dev.alllexey.itmowidgets.core.model.UserSummary
import dev.alllexey.itmowidgets.core.navigation.PendingSportDetailsArgs
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.sport.SportScoreSummary
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import dev.alllexey.itmowidgets.feature.sport.domain.model.FriendSportBooking
import dev.alllexey.itmowidgets.feature.sport.domain.model.SectionName
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportAttendance
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportAutoSignLimits
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportBooking
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportFilterCatalog
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportFilterOption
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportFreeSignEntry
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportFreeSignQueue
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportLesson
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportQueueEntryStatus
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportQueueLesson
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportScore
import dev.alllexey.itmowidgets.feature.sport.domain.model.UnavailableReason
import dev.alllexey.itmowidgets.feature.sport.domain.repository.SportRecurringRule
import dev.alllexey.itmowidgets.feature.sport.presentation.common.SportLessonSheetPresenter
import dev.alllexey.itmowidgets.feature.sport.presentation.common.SportLessonSheetUi
import dev.alllexey.itmowidgets.feature.sport.presentation.common.SportPrompt
import dev.alllexey.itmowidgets.feature.sport.presentation.common.sportDayLabel
import dev.alllexey.itmowidgets.feature.sport.presentation.common.toDetailsArgs
import dev.alllexey.itmowidgets.feature.sport.presentation.common.toLessonUi
import dev.alllexey.itmowidgets.feature.sport.presentation.my.SportBookingDay
import dev.alllexey.itmowidgets.feature.sport.presentation.my.SportBookingItem
import dev.alllexey.itmowidgets.feature.sport.presentation.my.SportMyBody
import dev.alllexey.itmowidgets.feature.sport.presentation.my.SportMyUiState
import dev.alllexey.itmowidgets.feature.sport.presentation.my.SportScoreUi
import dev.alllexey.itmowidgets.feature.sport.presentation.points.SportPointsFilter
import dev.alllexey.itmowidgets.feature.sport.presentation.points.SportPointsPresenter
import dev.alllexey.itmowidgets.feature.sport.presentation.points.SportPointsUiState
import dev.alllexey.itmowidgets.feature.sport.presentation.sections.SportSectionsBody
import dev.alllexey.itmowidgets.feature.sport.presentation.sections.SportSectionsInput
import dev.alllexey.itmowidgets.feature.sport.presentation.sections.SportSectionsPresenter
import dev.alllexey.itmowidgets.feature.sport.presentation.sections.SportSectionsUiState
import dev.alllexey.itmowidgets.feature.sport.presentation.sign.SportCatalogRow
import dev.alllexey.itmowidgets.feature.sport.presentation.sign.SportDayPart
import dev.alllexey.itmowidgets.feature.sport.presentation.sign.SportSignBody
import dev.alllexey.itmowidgets.feature.sport.presentation.sign.SportSignCommand
import dev.alllexey.itmowidgets.feature.sport.presentation.sign.SportSignFilters
import dev.alllexey.itmowidgets.feature.sport.presentation.sign.SportSignInput
import dev.alllexey.itmowidgets.feature.sport.presentation.sign.SportSignPresenter
import dev.alllexey.itmowidgets.feature.sport.presentation.sign.SportSignUiState
import dev.alllexey.itmowidgets.shared.feature.sport.SportStrings
import kotlin.time.Instant
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

/**
 * Мария's sport on Monday, 7 September (the design lab's data, synthetic) in the states of the design lab's axes,
 * built by the real presenters: the Android previews and the iOS snapshot tests draw the same states.
 */
object SportSamples {
    private val zone = TimeZone.of("Europe/Moscow")
    private val today = LocalDate(2026, 9, 7)
    private const val ONLINE = -1L
    private val now: Instant = LocalDateTime(today, LocalTime(12, 0)).toInstant(zone)

    /** The tab's name in the header. */
    const val TITLE = "Спорт"

    /** Monday, 7 September 2026, 12:00 in Saint Petersburg. */
    val time: AcademicTimeProvider = object : AcademicTimeProvider {
        override val timeZone: TimeZone = zone
        override fun today(): LocalDate = today
        override fun now(): Instant = now
    }

    private val tuesday = LocalDate(2026, 9, 8)
    private val thursday = LocalDate(2026, 9, 10)

    private fun at(date: LocalDate, time: String): Instant = LocalDateTime(date, LocalTime.parse(time)).toInstant(zone)

    private fun person(isu: Int, name: String) = UserSummary(isu, name, null, emptyList(), UserSharing(sport = true, schedule = true))

    private val timur = person(301, "Каримов Тимур")
    private val polina = person(302, "Егорова Полина")
    private val ivan = person(303, "Козлов Иван")

    private val catalog = SportFilterCatalog(
        buildings = listOf(
            SportFilterOption(13, "Кронверкский пр., 49"),
            SportFilterOption(273, "ул. Ломоносова, 9"),
            SportFilterOption(5, "Биржевая линия, 14"),
            SportFilterOption(7, "ул. Чайковского, 11"),
        ),
        sections = emptyList(),
        sportTypes = emptyList(),
        teachers = emptyList(),
    )

    private var nextId = 100L

    private fun lesson(
        date: LocalDate,
        start: String,
        end: String,
        section: String,
        teacher: String,
        building: Long,
        room: String,
        limit: Int,
        taken: Int,
        level: Int = 1,
        signed: Boolean = false,
        queued: Pair<Int, Int>? = null,
        clash: Boolean = false,
        friends: List<UserSummary> = emptyList(),
        reasons: List<UnavailableReason> = emptyList(),
        comment: String? = null,
        real: Boolean = true,
    ): SportLesson {
        val id = nextId++
        val startAt = at(date, start)
        val endAt = at(date, end)
        // Venues outside My ITMO's dictionary (building 0) and online rooms (-1) name themselves.
        val address = catalog.buildings.firstOrNull { it.id == building }?.value
        val roomName = address?.let { "$it, $room" } ?: room
        return SportLesson(
            isLessonReal = real, lessonId = id, start = startAt, end = endAt, sectionId = section.hashCode().toLong(),
            sectionName = SectionName(section), sectionLevel = 1, lessonGroupId = 1, lessonLevel = level, typeId = 2,
            buildingId = building, roomId = if (building == ONLINE) ONLINE else id, roomName = roomName, limit = limit, available = limit - taken,
            comment = comment, timeSlotId = 1, timeSlotStart = start, timeSlotEnd = end, intersection = clash,
            canSignIn = reasons.isEmpty() && taken < limit, unavailableReasons = reasons, signed = signed,
            teacherIsu = 200 + id.toInt(), teacherFio = teacher,
            signEntry = queued?.let { (position, total) -> entry(id, position, total, section, startAt, endAt, roomName, teacher) },
            signQueue = SportFreeSignQueue(id, total = 2).takeIf { taken >= limit },
            friendsBookings = friends.map { FriendSportBooking(it, id, null) },
        )
    }

    private fun entry(id: Long, position: Int, total: Int, section: String, start: Instant, end: Instant, room: String, teacher: String) =
        SportFreeSignEntry(
            id = id, lessonId = id, position = position, total = total, isCancelled = false, status = SportQueueEntryStatus.WAITING,
            createdAt = now, firstNotifiedAt = null, lastNotifiedAt = null, cancelledAt = null, satisfiedAt = null, expiredAt = null,
            notificationAttempts = 0, maxNotificationAttempts = 5,
            targetLesson = SportQueueLesson(id, 1, section, 1, 1, 2, 1, room, start, end, 1, 200, teacher),
            forceSign = false,
        )

    private val ofp = lesson(tuesday, "08:20", "09:50", "Общая физическая подготовка", "Васильева Анастасия Романовна", 13, "спортивный зал", 20, 6, clash = true)
    private val yoga = lesson(tuesday, "10:00", "11:30", "Йога", "Белова Ирина Сергеевна", 7, "зал", 16, 11)
    private val badminton = lesson(tuesday, "13:30", "15:00", "Бадминтон", "Орлов Денис Викторович", 5, "зал", 14, 12, level = 2, friends = listOf(ivan))
    private val swimming = lesson(tuesday, "15:20", "16:50", "Плавание", "Захаров Николай Иванович", 273, "бассейн", 16, 16, level = 2, queued = 2 to 5, friends = listOf(polina))
    private val fitness = lesson(
        tuesday, "17:00", "18:30", "Фитнес", "Кузнецова Елена Игоревна", 7, "зал, 1 этаж", 18, 18,
        reasons = listOf(UnavailableReason.Full), comment = "Сменная обувь обязательна",
    )
    private val tennis = lesson(tuesday, "17:00", "18:30", "Настольный теннис", "Гусев Олег Павлович", 5, "зал", 12, 8)
    private val volleyball = lesson(tuesday, "18:30", "20:00", "Волейбол", "Тихонова Марина Юрьевна", 13, "спортивный зал", 24, 22, friends = listOf(timur))
    private val basketball = lesson(
        tuesday, "18:40", "20:10", "Баскетбол", "Морозов Павел Андреевич", 13, "спортивный зал", 15, 9, level = 4,
        reasons = listOf(UnavailableReason.Other("Только для сборной")),
    )
    private val volleyThursday = lesson(thursday, "15:20", "16:50", "Волейбол", "Тихонова Марина Юрьевна", 13, "спортивный зал", 24, 20, signed = true)
    private val volleyThursdayEvening = lesson(thursday, "18:30", "20:00", "Волейбол", "Тихонова Марина Юрьевна", 13, "спортивный зал", 24, 22)
    private val swimToday = lesson(today, "17:00", "18:30", "Плавание", "Захаров Николай Иванович", 273, "бассейн", 16, 14, level = 2, signed = true)
    private val swimNext = lesson(LocalDate(2026, 9, 22), "15:20", "16:50", "Плавание", "Захаров Николай Иванович", 273, "бассейн", 16, 0, level = 2, real = false)

    private val nextTuesday = LocalDate(2026, 9, 15)
    private val nextWeek = listOf(
        lesson(nextTuesday, "10:00", "11:30", "Йога", "Белова Ирина Сергеевна", 7, "зал", 16, 9),
        lesson(nextTuesday, "13:30", "15:00", "Скалолазание", "Соколов Артём Ильич", 273, "скалодром", 10, 7),
        lesson(nextTuesday, "17:00", "18:30", "Футбол", "Никитин Сергей Олегович", 0, "ул. Правды, 11, ФОК «Юность»", 30, 21),
        lesson(nextTuesday, "18:30", "20:00", "Шахматы", "Лебедева Ольга Петровна", ONLINE, "Online", 40, 12),
        lesson(LocalDate(2026, 9, 17), "18:30", "20:00", "Волейбол", "Тихонова Марина Юрьевна", 13, "спортивный зал", 24, 23),
    )

    private val tuesdayLessons = listOf(ofp, yoga, badminton, swimming, fitness, tennis, volleyball, basketball)
    private val lessons: List<SportLesson> = tuesdayLessons + listOf(volleyThursday, volleyThursdayEvening, swimToday, swimNext) +
        (1..4).map { lesson(today, "19:00", "20:30", "Фитнес", "Кузнецова Елена Игоревна", 7, "зал", 18, it) } +
        (1..3).map { lesson(LocalDate(2026, 9, 12), "11:00", "12:30", "Йога", "Белова Ирина Сергеевна", 7, "зал", 16, 4 + it) }

    /** Tuesday's lessons of Мария's own timetable: Алгоритмы at 08:20 clashes with ОФП. */
    private val timetable = listOf(
        LessonDay(
            tuesday,
            listOf(
                DayLesson(
                    LocalTime(8, 20), LocalTime(9, 50), "Алгоритмы и структуры данных", 1, "Лебедев Артём Сергеевич", "1506",
                    "Кронверкский пр., 49", online = false,
                    target = LessonTarget.PendingSport(PendingSportDetailsArgs(0, "", false, false, "", "", "", "")),
                ),
            ),
            cachedAt = null,
        ),
    )

    private fun signInput(filters: SportSignFilters, sectionQuery: String? = null) = SportSignInput(
        lessons = lessons + nextWeek, catalog = catalog, filters = filters, pinned = setOf(SectionName("Плавание"), SectionName("Волейбол")),
        busy = emptySet(), timetable = timetable, showTeacher = true, showDayPart = true,
        now = now, today = today, zone = zone, history = listOf(SectionName("Плавание")), sectionQuery = sectionQuery,
    )

    /**
     * `Запись` at the lab's axes: [filters] `none`, `active` (`Есть места`, `Вечер`), `sheet` (the top types and `Ещё`)
     * or `sheet-all` (every type with the search, `Шахматы` chosen); [day] `normal` (Tuesday), `empty` (Sunday) or
     * `week2` (Tuesday of the next week).
     */
    fun sign(filters: String, day: String): SportSignUiState {
        val date = when (day) {
            "empty" -> LocalDate(2026, 9, 13)
            "week2" -> nextTuesday
            else -> tuesday
        }
        val chosen = SportSignFilters(selectedDate = date).let {
            when (filters) {
                "none" -> it
                "sheet-all" -> it.copy(sections = setOf(SectionName("Шахматы")))
                else -> it.copy(onlyFree = true, dayPart = SportDayPart.EVENING)
            }
        }
        val query = "".takeIf { filters == "sheet-all" }
        return SportSignPresenter.present(signInput(chosen, query), filtersOpen = filters.startsWith("sheet"))
    }

    val signLoading: SportSignUiState get() = sign("none", "normal").copy(body = SportSignBody.Loading)

    val signFailed: SportSignUiState get() = sign("none", "normal").copy(body = SportSignBody.Failed(AppError.Network))

    /** The rows of Tuesday, for row-level previews. */
    val rows: List<SportCatalogRow> get() = (sign("none", "normal").body as SportSignBody.Day).rows

    private fun booking(lesson: SportLesson) = SportBooking(
        isLessonReal = lesson.isLessonReal, lessonId = lesson.lessonId, sectionName = lesson.sectionName, start = lesson.start,
        end = lesson.end, roomName = lesson.roomName, teacherFio = lesson.teacherFio, teacherIsu = lesson.teacherIsu,
        sectionLevel = 1, lessonLevel = lesson.lessonLevel, signed = lesson.signed, signEntry = lesson.signEntry,
        friendsBookings = lesson.friendsBookings,
    )

    private val myBookings = listOf(swimToday, swimming, volleyThursday).map(::booking)

    /** Another user's bookings, for their read-only sport. */
    val friendBookings: List<SportBooking> = listOf(volleyThursday, volleyThursdayEvening).map(::booking)

    private val score = SportScoreUi.of(SportScoreSummary(attendances = 15, bonus = 15))

    /** `Моё` at the lab's axes: [state] `active` (bookings and a queue) or `none`. */
    fun my(state: String): SportMyUiState = SportMyUiState(
        score = score,
        body = SportMyBody.Content(
            if (state == "none") {
                emptyList()
            } else {
                myBookings.groupBy { it.start.toLocalDateTimeIn() }.map { (date, items) ->
                    SportBookingDay(sportDayLabel(date, today), items.map { SportBookingItem(it.lessonId, it.toLessonUi(time)) })
                }
            },
        ),
        sections = UiText.Dynamic("Волейбол, Плавание"),
    )

    val myLoading = SportMyUiState(score = null, body = SportMyBody.Loading)

    val myFailed = SportMyUiState(score = null, body = SportMyBody.Failed(AppError.Network))

    /** The lesson sheet at the lab's axes: [offer] `can`, `queue`, `clash` or `booked`. */
    fun lesson(offer: String): SportLessonSheetUi {
        val item = when (offer) {
            "queue" -> fitness
            "clash" -> ofp
            "booked" -> volleyThursday
            else -> volleyball
        }
        var args = item.toDetailsArgs()
        if (offer == "clash") args = args.copy(clashSubject = "Алгоритмы и структуры данных", clashStart = "08:20")
        return SportLessonSheetPresenter.present(args, now, zone)
    }

    /** `Встать в очередь?` at the lab's axes: [kind] `full` (Фитнес, Tuesday) or `future` (Плавание in two weeks). */
    fun join(kind: String): SportPrompt.Join = if (kind == "future") {
        SportPrompt.Join(
            lessonId = swimNext.lessonId,
            what = UiText.Dynamic("Плавание, Ломо"),
            whenText = UiText.Dynamic("вт, 22 сент., 15:20–16:50"),
            steps = listOf(
                UiText.Res(SportStrings.sport_join_future_step_1),
                UiText.Res(SportStrings.sport_join_future_step_2),
                UiText.Res(SportStrings.sport_join_future_step_3),
            ),
            lateSign = false,
            lateSignHint = null,
            limit = UiText.Res(SportStrings.sport_queue_limit_left, listOf(1, 3)),
            command = SportSignCommand.CreateAutoSign(swimNext.lessonId),
        )
    } else {
        SportPrompt.Join(
            lessonId = fitness.lessonId,
            what = UiText.Dynamic("Фитнес, Чайковского"),
            whenText = UiText.Dynamic("вт, 8 сент., 17:00–18:30"),
            steps = listOf(
                UiText.Res(SportStrings.sport_join_full_step_1, listOf(18, 18)),
                UiText.Res(SportStrings.sport_join_full_step_2),
                UiText.Res(SportStrings.sport_join_full_step_3, listOf("16:00")),
            ),
            lateSign = true,
            lateSignHint = UiText.Res(SportStrings.sport_join_late_hint, listOf("17:00", "16:00")),
            limit = null,
            command = SportSignCommand.CreateFreeSign(fitness.lessonId),
        )
    }

    /**
     * `Мои секции` at the lab's axes: [queue] `on`, `off` or `limit`. The repeat needs Backend; the app hides it, the
     * samples show it with rules of their own.
     */
    fun sections(queue: String): SportSectionsUiState {
        val limits = SportAutoSignLimits(limit = 3, available = if (queue == "limit") 0 else 2, nextAvailableAt = at(LocalDate(2026, 10, 1), "00:00"))
        val pinned = setOf(SectionName("Плавание"), SectionName("Волейбол"))
        val rules = if (queue == "on") listOf(SportSectionsPresenter.rule(swimToday, zone)) else emptyList<SportRecurringRule>()
        val input = SportSectionsInput(lessons, pinned, limits, rules, timetable, emptySet(), now, today, zone)
        return SportSectionsUiState(
            subtitle = SportSectionsPresenter.subtitle(limits),
            body = SportSectionsBody.Content(SportSectionsPresenter.cards(input), UiText.Res(SportStrings.sport_sections_note)),
            limitGate = SportSectionsPresenter.limitGate(input),
        )
    }

    /** `Мои секции` as the app shows it today: without the repeat, which waits for Backend. */
    val sectionsWithoutRepeat: SportSectionsUiState
        get() {
            val pinned = setOf(SectionName("Плавание"), SectionName("Волейбол"))
            val limits = SportAutoSignLimits(limit = 3, available = 2, nextAvailableAt = at(LocalDate(2026, 10, 1), "00:00"))
            val input = SportSectionsInput(lessons, pinned, limits, null, timetable, emptySet(), now, today, zone)
            return SportSectionsUiState(
                subtitle = SportSectionsPresenter.subtitle(limits),
                body = SportSectionsBody.Content(SportSectionsPresenter.cards(input), null),
            )
        }

    val sectionsEmpty = SportSectionsUiState(body = SportSectionsBody.Empty)

    val sectionsPicker = sectionsEmpty.copy(picker = SportSectionsPresenter.picker(lessons, setOf(SectionName("Плавание"))))

    private fun award(type: String, name: String, score: Int, date: LocalDate, competition: String? = null, place: String? = null) =
        SportAttendance(
            type = type, name = SectionName(name), evaluationId = date.toEpochDays(), evaluationName = null, sectionLevel = 1,
            score = score, dateTime = at(date, "18:00"), isCompetition = competition != null, competitionName = competition, place = place,
        )

    /** `Откуда баллы` at the lab's axes: [progress] `short` (30 of 100) or `done`. */
    fun points(progress: String): SportPointsUiState {
        val history = if (progress == "done") {
            (1..13).map { award("lesson", if (it % 2 == 0) "Плавание" else "Волейбол", 5, LocalDate(2026, 9, 1).plusDays(it * 4)) } +
                listOf(
                    award("competition", "Плавание", 12, LocalDate(2026, 10, 24), "Кубок ИТМО по плаванию", "3 место"),
                    award("competition", "Волейбол", 15, LocalDate(2026, 9, 19), "Турнир по волейболу", "1 место"),
                    award("exercise", "Общая физическая подготовка", 12, LocalDate(2026, 9, 3)),
                )
        } else {
            listOf(
                award("competition", "Волейбол", 10, LocalDate(2026, 9, 5), "Турнир по волейболу", "2 место"),
                award("lesson", "Волейбол", 5, LocalDate(2026, 9, 4)),
                award("exercise", "Нормативы ГТО", 5, LocalDate(2026, 9, 3)),
                award("lesson", "Общая физическая подготовка", 5, LocalDate(2026, 9, 2)),
                award("lesson", "Плавание", 5, LocalDate(2026, 9, 1)),
            )
        }
        val score = SportScore(
            attendances = history.filter { it.type == "lesson" }.sumOf { it.score },
            other = history.filter { it.type != "lesson" }.sumOf { it.score },
            attendancesData = history,
        )
        return SportPointsPresenter.present(score, SportPointsFilter.ALL, zone)
    }

    private fun Instant.toLocalDateTimeIn(): LocalDate = toLocalDateTime(zone).date

    private fun LocalDate.plusDays(days: Int): LocalDate = plus(days, DateTimeUnit.DAY)
}
