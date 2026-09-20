package dev.alllexey.itmowidgets.site

import android.view.View
import androidx.core.os.bundleOf
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.material.tabs.TabLayout
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.app.FriendSelectorFixture
import dev.alllexey.itmowidgets.app.HomeFixture
import dev.alllexey.itmowidgets.app.SettingsNavigationTestActivity
import dev.alllexey.itmowidgets.core.home.HomeCard
import dev.alllexey.itmowidgets.core.home.HomeLessonState
import dev.alllexey.itmowidgets.core.home.HomeScheduleRow
import dev.alllexey.itmowidgets.core.model.RelationshipState
import dev.alllexey.itmowidgets.core.model.UserGroup
import dev.alllexey.itmowidgets.core.model.UserProfile
import dev.alllexey.itmowidgets.core.model.UserSharing
import dev.alllexey.itmowidgets.core.model.UserSummary
import dev.alllexey.itmowidgets.core.navigation.LessonDetailsArgs
import dev.alllexey.itmowidgets.core.navigation.PendingSportDetailsArgs
import dev.alllexey.itmowidgets.core.navigation.UserScreenArgs
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.schedule.SubjectLesson
import dev.alllexey.itmowidgets.core.session.CurrentUser
import dev.alllexey.itmowidgets.core.social.FriendRequests
import dev.alllexey.itmowidgets.core.sport.PendingSportBooking
import dev.alllexey.itmowidgets.core.sport.SportScorePeriod
import dev.alllexey.itmowidgets.core.sport.SportScoreRepository
import dev.alllexey.itmowidgets.core.sport.SportScoreSummary
import dev.alllexey.itmowidgets.core.ui.navigation.AppScreen
import dev.alllexey.itmowidgets.core.util.DataState
import dev.alllexey.itmowidgets.feature.recordbook.domain.RecordbookRepository
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookControl
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookPeriod
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookProgram
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookSubject
import dev.alllexey.itmowidgets.feature.recordbook.ui.RecordbookPreviewActivity
import dev.alllexey.itmowidgets.feature.schedule.domain.model.Building
import dev.alllexey.itmowidgets.feature.schedule.domain.model.DaySchedule
import dev.alllexey.itmowidgets.feature.schedule.domain.model.Lesson
import dev.alllexey.itmowidgets.feature.schedule.domain.model.Room
import dev.alllexey.itmowidgets.feature.schedule.ui.ScheduleLifecycleTestActivity
import dev.alllexey.itmowidgets.feature.sport.domain.model.FriendSportBooking
import dev.alllexey.itmowidgets.feature.sport.domain.model.SectionName
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportBooking
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportFreeSignEntry
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportLesson
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportQueueEntryStatus
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportQueueLesson
import dev.alllexey.itmowidgets.feature.sport.ui.SportCardsPreviewActivity
import dev.alllexey.itmowidgets.feature.sport.ui.sign.SportLessonItem
import dev.alllexey.itmowidgets.testing.Screenshots
import dev.alllexey.itmowidgets.testing.TestUi
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Not a test: with `captureScreenshots=true` it walks the debug hosts with
 * lifelike, invented data and saves full-screen PNGs for the website
 * (`site-screenshots-light` / `-night`). `siteTheme=night` switches every host to
 * its dark appearance; the schedule host follows the system, so the caller
 * toggles `cmd uimode night` as well. No real account, no network.
 */
@RunWith(AndroidJUnit4::class)
class SiteScreenshotCapture {
    private val night = InstrumentationRegistry.getArguments().getString("siteTheme") == "night"
    private val directory = "site-screenshots-" + if (night) "night" else "light"

    @Test
    fun captureSiteScreenshots() {
        if (!Screenshots.enabled) return
        captureHomeAndSocial()
        captureSchedule()
        captureSport()
        captureRecordbook()
    }

    // region Home, profile, QR, picker (SettingsNavigationTestActivity)

    private fun captureHomeAndSocial() {
        SettingsNavigationTestActivity.appearance = SettingsNavigationTestActivity.Appearance(dark = night)
        SettingsNavigationTestActivity.homeFixture = HomeFixture(cards = homeCards())
        SettingsNavigationTestActivity.sessionUser = CurrentUser(ME_ISU, ME_NAME, null)
        SettingsNavigationTestActivity.socialCurrentUser = me()
        SettingsNavigationTestActivity.socialFriends = FRIENDS.map { UserProfile(it, RelationshipState.FRIENDS) }
        SettingsNavigationTestActivity.socialRequests = FriendRequests(
            incoming = REQUESTS.map { UserProfile(it, RelationshipState.INCOMING) },
            outgoing = emptyList()
        )
        SettingsNavigationTestActivity.profileFor = { isu ->
            val user = (FRIENDS + REQUESTS).firstOrNull { it.isu == isu } ?: FRIENDS.first()
            UserProfile(user, if (user in FRIENDS) RelationshipState.FRIENDS else RelationshipState.INCOMING)
        }
        SettingsNavigationTestActivity.friendsResult = AppResult.Success(FRIENDS.take(4).map { UserProfile(it, RelationshipState.NONE) })
        SettingsNavigationTestActivity.friendSelectorFixture = FriendSelectorFixture(FRIENDS)
        try {
            SettingsNavigationTestActivity.startDestination = R.id.navigation_home
            ActivityScenario.launch(SettingsNavigationTestActivity::class.java).use { scenario ->
                settle()
                capture("home")
                scenario.onActivity { it.openScreen(AppScreen.QR_PASS, null) }
                settle()
                capture("qr")
                scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
                settle()
                scenario.onActivity {
                    it.openScreen(AppScreen.USER_PROFILE, bundleOf(UserScreenArgs.ISU to FRIENDS[0].isu, UserScreenArgs.NAME to FRIENDS[0].name))
                }
                settle()
                capture("profile")
                scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
                settle()
                scenario.onActivity { it.host.navController.navigate(R.id.friend_selector) }
                settle()
                capture("friend-picker")
            }
            SettingsNavigationTestActivity.startDestination = R.id.navigation_me
            ActivityScenario.launch(SettingsNavigationTestActivity::class.java).use {
                settle()
                capture("me")
            }
        } finally {
            SettingsNavigationTestActivity.appearance = SettingsNavigationTestActivity.Appearance()
            SettingsNavigationTestActivity.homeFixture = HomeFixture()
            SettingsNavigationTestActivity.startDestination = R.id.navigation_home
            SettingsNavigationTestActivity.friendSelectorFixture = FriendSelectorFixture()
        }
    }

    private fun homeCards(): List<HomeCard> = listOf(
        HomeCard.Schedule(
            date = TODAY,
            tomorrow = false,
            rows = listOf(
                HomeScheduleRow.Lesson(lessonArgs(2, "11:30", "13:00", MATH, "Лекция", 1, IVANOVA, "1506", KRONVA), HomeLessonState.CURRENT, progress = 0.45f),
                HomeScheduleRow.Lesson(lessonArgs(3, "13:30", "15:00", ALGO, "Практика", 3, SMIRNOV, "2334", KRONVA), HomeLessonState.UPCOMING),
                HomeScheduleRow.PendingSport(pendingArgs(16, "Волейбол", "Кронверкский пр., 49 · спортивный зал", prediction = false), predicted = false),
                HomeScheduleRow.Lesson(lessonArgs(4, "17:00", "18:30", ENGLISH, "Практика", 3, VOLKOVA, "403", LOMO), HomeLessonState.UPCOMING)
            ),
            completed = 1
        ),
        HomeCard.Sport(SportScoreSummary(attendances = 48, bonus = 16), listOf(
            pendingBooking(1, "Волейбол", 16, "Кронверкский пр., 49 · спортивный зал", prediction = false),
            pendingBooking(2, "Плавание", 19, "Ломоносова, 9 · бассейн", prediction = true, day = TODAY.plusDays(2))
        )),
        HomeCard.FriendRequests(REQUESTS)
    )

    // endregion

    // region Schedule (ScheduleLifecycleTestActivity)

    private fun captureSchedule() {
        ScheduleLifecycleTestActivity.days.value = listOf(
            day(TODAY, lesson(1, "09:30", "11:00", DISCRETE, "Лекция", 1, PETROV, "1405", KRONVA),
                lesson(2, "11:30", "13:00", MATH, "Лекция", 1, IVANOVA, "1506", KRONVA, zoom = true),
                lesson(3, "13:30", "15:00", ALGO, "Практика", 3, SMIRNOV, "2334", KRONVA),
                lesson(4, "17:00", "18:30", ENGLISH, "Практика", 3, VOLKOVA, "403", LOMO)),
            day(TODAY.plusDays(1), lesson(5, "10:00", "11:30", PHYSICS, "Лекция", 1, KUZNETSOVA, "1220", KRONVA),
                lesson(6, "11:40", "13:10", PHYSICS, "Лабораторная", 2, KUZNETSOVA, "1224", KRONVA),
                lesson(7, "15:20", "16:50", HISTORY, "Лекция", 1, PETROV, "301", BIRZH)),
            day(TODAY.plusDays(2), lesson(8, "09:30", "11:00", LINALG, "Практика", 3, IVANOVA, "1506", KRONVA),
                lesson(9, "11:30", "13:00", PROGRAMMING, "Лабораторная", 2, SMIRNOV, "2338", KRONVA))
        )
        ScheduleLifecycleTestActivity.showPendingSport.value = true
        ScheduleLifecycleTestActivity.pendingSport.value = DataState.Success(listOf(
            pendingBooking(1, "Волейбол", 16, "Кронверкский пр., 49 · спортивный зал", prediction = false)
        ))
        try {
            ActivityScenario.launch(ScheduleLifecycleTestActivity::class.java).use { scenario ->
                settle()
                capture("schedule")
                scenario.onActivity { activity ->
                    val cards = activity.findViewById<RecyclerView>(R.id.outer_recycler_view)
                    cards.descendantsOf().filter { it.id == R.id.card_container && it.isShown }.elementAt(1).performClick()
                }
                settle()
                capture("lesson")
            }
        } finally {
            ScheduleLifecycleTestActivity.days.value = emptyList()
            ScheduleLifecycleTestActivity.showPendingSport.value = false
            ScheduleLifecycleTestActivity.pendingSport.value = DataState.Success(emptyList())
        }
    }

    // endregion

    // region Sport (SportCardsPreviewActivity)

    private fun captureSport() {
        SportCardsPreviewActivity.appearance = SportCardsPreviewActivity.Appearance(dark = night)
        try {
            ActivityScenario.launch(SportCardsPreviewActivity::class.java).use { scenario ->
                scenario.onActivity { it.showLessons(catalog().map { lesson -> SportLessonItem(lesson) }) }
                settle()
                capture("sport-catalog")
                scenario.onActivity { it.showBookings(bookings()) }
                settle()
                capture("sport-my")
                scenario.onActivity { it.showDetails(bookings()[1]) }
                settle()
                capture("sport-details")
            }
        } finally {
            SportCardsPreviewActivity.appearance = SportCardsPreviewActivity.Appearance()
        }
    }

    private fun catalog(): List<SportLesson> = listOf(
        sportLesson(11, "Волейбол", "Кронверкский пр., 49 · спортивный зал", NIKOLAEV, day = TODAY, hour = 16, limit = 24, available = 5),
        sportLesson(12, "Плавание", "Ломоносова, 9 · бассейн", FEDOROVA, day = TODAY, hour = 19, limit = 12, available = 0,
            entry = freeEntry(12, "Плавание", position = 2, total = 7)),
        sportLesson(13, "Настольный теннис", "Вяземский пер., 5-7 · игровой зал", NIKOLAEV, day = TODAY.plusDays(1), hour = 18, limit = 16, available = 9,
            signed = true, friends = FRIENDS.take(2)),
        sportLesson(14, "Фитнес (функциональная тренировка)", "Кронверкский пр., 49 · зал 2", GRIGORIEVA, day = TODAY.plusDays(1), hour = 20, limit = 20, available = 14)
    )

    private fun bookings(): List<SportBooking> = listOf(
        booking(13, "Настольный теннис", "Вяземский пер., 5-7 · игровой зал", NIKOLAEV, day = TODAY.plusDays(1), hour = 18, signed = true, friends = FRIENDS.take(2)),
        booking(12, "Плавание", "Ломоносова, 9 · бассейн", FEDOROVA, day = TODAY, hour = 19, signed = false,
            entry = freeEntry(12, "Плавание", position = 2, total = 7)),
        booking(15, "Волейбол", "Кронверкский пр., 49 · спортивный зал", NIKOLAEV, day = TODAY.plusDays(3), hour = 16, signed = true)
    )

    // endregion

    // region Recordbook (RecordbookPreviewActivity)

    private fun captureRecordbook() {
        RecordbookPreviewActivity.appearance = RecordbookPreviewActivity.Appearance(dark = night)
        RecordbookPreviewActivity.repository = SiteRecordbook()
        RecordbookPreviewActivity.sportRepository = object : SportScoreRepository {
            override suspend fun getScorePeriods() = AppResult.Success(listOf(SportScorePeriod(7, "Весна 2025/2026")))
            override suspend fun getScoreSummary(semesterId: Long) = AppResult.Success(SportScoreSummary(48, 16))
        }
        RecordbookPreviewActivity.lessonsGateway = RecordbookPreviewActivity.MemoryLessons(listOf(
            // The recordbook host's clock stands on 2026-06-01; the hub window starts there.
            subjectLesson(21, RECORDBOOK_TODAY.plusDays(1), ALGO_ID, 1, SMIRNOV, 300002, "10:00", "11:30", ALGO),
            subjectLesson(22, RECORDBOOK_TODAY.plusDays(3), ALGO_ID, 3, SMIRNOV, 300002, "13:30", "15:00", ALGO),
            subjectLesson(23, RECORDBOOK_TODAY.plusDays(8), ALGO_ID, 1, SMIRNOV, 300002, "10:00", "11:30", ALGO),
            subjectLesson(24, RECORDBOOK_TODAY.plusDays(10), ALGO_ID, 3, ORLOVA, 300006, "13:30", "15:00", ALGO),
            subjectLesson(25, RECORDBOOK_TODAY.plusDays(15), ALGO_ID, 1, SMIRNOV, 300002, "10:00", "11:30", ALGO)
        ))
        try {
            ActivityScenario.launch(RecordbookPreviewActivity::class.java).use { scenario ->
                settle()
                capture("recordbook")
                scenario.onActivity { activity ->
                    activity.findViewById<RecyclerView>(R.id.main_recycler_view).children()
                        .first { it.findViewById<android.widget.TextView>(R.id.name)?.text?.contains("Алгоритмы") == true }
                        .performClick()
                }
                settle()
                capture("subject-scores")
                scenario.onActivity { it.findViewById<TabLayout>(R.id.tabs).getTabAt(1)!!.select() }
                settle()
                capture("subject-schedule")
            }
        } finally {
            RecordbookPreviewActivity.appearance = RecordbookPreviewActivity.Appearance()
            RecordbookPreviewActivity.repository = null
            RecordbookPreviewActivity.sportRepository = null
            RecordbookPreviewActivity.lessonsGateway = RecordbookPreviewActivity.MemoryLessons()
        }
    }

    private class SiteRecordbook : RecordbookRepository {
        override suspend fun getPrograms() = AppResult.Success(listOf(RecordbookProgram(1, "Программная инженерия", listOf(
            RecordbookPeriod("2026/2027", 4, 2, false), RecordbookPeriod("2026/2027", 3, 2, false),
            RecordbookPeriod("2025/2026", 2, 1, true), RecordbookPeriod("2025/2026", 1, 1, false)
        ))))

        override suspend fun getSubjects(programId: Long, semester: Int): AppResult<List<RecordbookSubject>> = AppResult.Success(listOf(
            subject(1, MATH, null, 41.0, IVANOVA),
            subject(ALGO_ID, ALGO, null, 58.5, SMIRNOV),
            subject(3, "Физическая культура и спорт (элективная)", null, null, NIKOLAEV, details = false),
            subject(4, DISCRETE, "4/C", 71.0, PETROV),
            subject(5, ENGLISH, "зачет", 68.0, VOLKOVA),
            subject(6, PHYSICS, null, 22.0, KUZNETSOVA),
            subject(7, HISTORY, "5/A", 91.0, PETROV)
        ))

        override suspend fun getControls(entryId: Long) = AppResult.Success(listOf(
            RecordbookControl(1, "Лабораторные работы", 27.0, 20.0, 40.0, true, null, null),
            RecordbookControl(2, "Лабораторная 1. Сортировки", 9.0, 5.0, 10.0, true, null, SMIRNOV, 1),
            RecordbookControl(3, "Лабораторная 2. Хеш-таблицы", 10.0, 5.0, 10.0, true, null, SMIRNOV, 1),
            RecordbookControl(4, "Лабораторная 3. Графы", 8.0, 5.0, 10.0, true, null, ORLOVA, 1),
            RecordbookControl(5, "Лабораторная 4. Динамическое программирование", null, 5.0, 10.0, true, null, SMIRNOV, 1),
            RecordbookControl(6, "Контрольная работа", 17.5, 10.0, 20.0, true, null, null),
            RecordbookControl(7, "Экзамен", null, 20.0, 40.0, true, null, null)
        ))

        private fun subject(id: Long, name: String, rate: String?, score: Double?, teacher: String, details: Boolean = true) =
            RecordbookSubject(name, id, id, if (id == 3L || id == 5L) "Зачёт" else "Экзамен", score, rate, null, null, details, teacher)
    }

    // endregion

    // region Builders

    private fun lessonArgs(
        pairId: Long, start: String, end: String, subject: String, type: String, typeId: Int,
        teacher: String, room: String, building: String, zoom: Boolean = false
    ) = LessonDetailsArgs(
        pairId = pairId, date = TODAY.toString(), subjectName = subject, typeId = typeId, format = "Очный",
        start = start, end = end, teacherFio = teacher, teacherIsu = 300000 + pairId, room = room, building = building,
        buildingId = 13, mainBuildingId = 13, note = null,
        zoomUrl = if (zoom) "https://itmo.ktalk.ru/example" else null, zoomPassword = null, zoomInfo = null
    )

    private fun lesson(
        pairId: Long, start: String, end: String, subject: String, type: String, typeId: Int,
        teacher: String, room: String, building: String, zoom: Boolean = false
    ) = Lesson(
        pairId = pairId, start = LocalTime.parse(start), end = LocalTime.parse(end), type = type, typeId = Lesson.TypeId(typeId),
        note = null, subjectName = subject, subjectId = pairId, groupName = ME_GROUP, flowId = 100 + pairId, flowTypeId = 2,
        teacherIsu = 300000 + pairId, teacherFio = teacher, room = Room(room), building = Building(building),
        buildingId = 13, mainBuildingId = 13, format = "Очный", formatId = 1,
        zoomUrl = if (zoom) "https://itmo.ktalk.ru/example" else null, zoomPassword = null, zoomInfo = null
    )

    private fun day(date: LocalDate, vararg lessons: Lesson) = DaySchedule(date.dayOfWeek.value, 1, date, null, lessons.toList())

    private fun pendingBooking(id: Long, section: String, hour: Int, room: String, prediction: Boolean, day: LocalDate = TODAY) =
        PendingSportBooking(
            queueId = id, queueKind = if (prediction) PendingSportBooking.QueueKind.AUTO else PendingSportBooking.QueueKind.FREE,
            lessonId = 100 + id, sectionName = section,
            start = OffsetDateTime.of(day, LocalTime.of(hour, 0), OFFSET), end = OffsetDateTime.of(day, LocalTime.of(hour + 1, 30), OFFSET),
            teacherFio = NIKOLAEV, roomName = room, isPrediction = prediction
        )

    private fun pendingArgs(hour: Int, section: String, room: String, prediction: Boolean) = PendingSportDetailsArgs(
        lessonId = 116, sectionName = section, autoSign = prediction, isPrediction = prediction,
        start = OffsetDateTime.of(TODAY, LocalTime.of(hour, 0), OFFSET).toString(),
        end = OffsetDateTime.of(TODAY, LocalTime.of(hour + 1, 30), OFFSET).toString(),
        teacherFio = NIKOLAEV, roomName = room
    )

    private fun sportLesson(
        id: Long, section: String, room: String, teacher: String, day: LocalDate, hour: Int, limit: Int, available: Int,
        signed: Boolean = false, entry: SportFreeSignEntry? = null, friends: List<UserSummary> = emptyList()
    ): SportLesson {
        val start = OffsetDateTime.of(day, LocalTime.of(hour, 0), OFFSET)
        return SportLesson(
            isLessonReal = true, lessonId = id, start = start, end = start.plusMinutes(90),
            sectionId = id, sectionName = SectionName(section), sectionLevel = 1, lessonGroupId = id, lessonLevel = 1, typeId = 2,
            buildingId = 1, roomId = id, roomName = room, limit = limit, available = available, comment = null,
            timeSlotId = hour.toLong(), timeSlotStart = "%02d:00".format(hour), timeSlotEnd = "%02d:30".format(hour + 1),
            intersection = false, canSignIn = available > 0 && !signed, unavailableReasons = emptyList(), signed = signed,
            teacherIsu = 200, teacherFio = teacher, signEntry = entry, signQueue = null,
            friendsBookings = friends.map { FriendSportBooking(it, id, null) }
        )
    }

    private fun booking(
        id: Long, section: String, room: String, teacher: String, day: LocalDate, hour: Int, signed: Boolean,
        entry: SportFreeSignEntry? = null, friends: List<UserSummary> = emptyList()
    ): SportBooking {
        val start = OffsetDateTime.of(day, LocalTime.of(hour, 0), OFFSET)
        return SportBooking(
            isLessonReal = true, lessonId = id, sectionName = SectionName(section), start = start, end = start.plusMinutes(90),
            roomName = room, teacherFio = teacher, teacherIsu = 200, sectionLevel = 1, lessonLevel = 1, signed = signed,
            signEntry = entry, friendsBookings = friends.map { FriendSportBooking(it, id, null) }
        )
    }

    private fun freeEntry(lessonId: Long, section: String, position: Int, total: Int): SportFreeSignEntry {
        val start = OffsetDateTime.of(TODAY, LocalTime.of(19, 0), OFFSET)
        return SportFreeSignEntry(
            id = lessonId, lessonId = lessonId, position = position, total = total, isCancelled = false,
            status = SportQueueEntryStatus.WAITING, createdAt = start.minusDays(1), firstNotifiedAt = start.minusHours(5),
            lastNotifiedAt = start.minusHours(2), cancelledAt = null, satisfiedAt = null, expiredAt = null,
            notificationAttempts = 2, maxNotificationAttempts = 5,
            targetLesson = SportQueueLesson(lessonId, lessonId, section, 1, 1, 1, 1, "Ломоносова, 9 · бассейн", start, start.plusMinutes(90), 1, 200, FEDOROVA),
            forceSign = false
        )
    }

    private fun subjectLesson(pairId: Long, date: LocalDate, subjectId: Long, typeId: Int, teacher: String, isu: Long, start: String, end: String, name: String) =
        SubjectLesson(
            pairId = pairId, date = date, start = LocalTime.parse(start), end = LocalTime.parse(end), typeId = typeId, type = "",
            subjectId = subjectId, subjectName = name, flowId = subjectId * 10, teacherIsu = isu, teacherFio = teacher,
            room = if (typeId == 1) "1506" else "2334", building = KRONVA, formatId = 1
        )

    private fun me() = UserSummary(ME_ISU, ME_NAME, null, listOf(UserGroup(ME_GROUP, 2, "ФИТиП")), UserSharing(true, true, true))

    private fun View.descendantsOf(): Sequence<View> = sequence {
        yield(this@descendantsOf)
        if (this@descendantsOf is android.view.ViewGroup) {
            for (i in 0 until childCount) yieldAll(getChildAt(i).descendantsOf())
        }
    }

    private fun RecyclerView.children(): List<View> = (0 until childCount).map(::getChildAt)

    private fun settle() = TestUi.settle(900)

    private fun capture(name: String) = Screenshots.capture(directory, name) { settle() }

    // endregion

    private companion object {
        val TODAY: LocalDate = LocalDate.of(2026, 9, 7)
        val RECORDBOOK_TODAY: LocalDate = LocalDate.of(2026, 6, 1)
        val OFFSET: ZoneOffset = ZoneOffset.ofHours(3)
        const val ME_ISU = 367201
        const val ME_NAME = "Морозова Анна Сергеевна"
        const val ME_GROUP = "M3234"
        const val ALGO_ID = 2L

        const val MATH = "Математический анализ"
        const val ALGO = "Алгоритмы и структуры данных"
        const val DISCRETE = "Дискретная математика"
        const val ENGLISH = "Иностранный язык"
        const val PHYSICS = "Физика"
        const val HISTORY = "История России"
        const val LINALG = "Линейная алгебра"
        const val PROGRAMMING = "Программирование"

        const val IVANOVA = "Иванова Елена Петровна"
        const val SMIRNOV = "Смирнов Алексей Викторович"
        const val PETROV = "Петров Дмитрий Андреевич"
        const val VOLKOVA = "Волкова Анна Игоревна"
        const val KUZNETSOVA = "Кузнецова Ольга Сергеевна"
        const val ORLOVA = "Орлова Мария Николаевна"
        const val NIKOLAEV = "Николаев Сергей Павлович"
        const val FEDOROVA = "Фёдорова Ирина Владимировна"
        const val GRIGORIEVA = "Григорьева Наталья Юрьевна"

        const val KRONVA = "Кронверкский проспект, 49"
        const val LOMO = "Ломоносова, 9"
        const val BIRZH = "Биржевая линия, 14"

        val FRIENDS = listOf(
            "Соколов Артём Игоревич" to "M3234", "Кузнецова Дарья Олеговна" to "M3237", "Орлов Никита Сергеевич" to "M3234",
            "Белова Полина Андреевна" to "M3238", "Морозов Илья Дмитриевич" to "M3236", "Лебедева Ксения Романовна" to "M3237"
        ).mapIndexed { index, (name, group) ->
            UserSummary(368100 + index, name, null, listOf(UserGroup(group, 2, "ФИТиП")), UserSharing(sport = true, schedule = index != 3, friends = true))
        }
        val REQUESTS = listOf("Новиков Егор Максимович" to "M3235", "Павлова Алина Витальевна" to "M3239").mapIndexed { index, (name, group) ->
            UserSummary(368200 + index, name, null, listOf(UserGroup(group, 2, "ФИТиП")), UserSharing(sport = false, schedule = false))
        }
    }
}
