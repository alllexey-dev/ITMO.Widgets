package dev.alllexey.itmowidgets.site

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.core.os.bundleOf
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.app.FriendSelectorFixture
import dev.alllexey.itmowidgets.app.HomeFixture
import dev.alllexey.itmowidgets.app.SettingsNavigationTestActivity
import dev.alllexey.itmowidgets.core.debug.PreviewAppearance
import dev.alllexey.itmowidgets.core.demo.DemoPeople
import dev.alllexey.itmowidgets.core.demo.DemoStudy
import dev.alllexey.itmowidgets.core.home.HomeCard
import dev.alllexey.itmowidgets.core.home.HomeLessonState
import dev.alllexey.itmowidgets.core.home.HomeScheduleRow
import dev.alllexey.itmowidgets.core.model.RelationshipState
import dev.alllexey.itmowidgets.core.model.UserProfile
import dev.alllexey.itmowidgets.core.navigation.UserScreenArgs
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.schedule.SubjectLesson
import dev.alllexey.itmowidgets.core.session.CurrentUser
import dev.alllexey.itmowidgets.core.social.FriendRequests
import dev.alllexey.itmowidgets.core.sport.PendingSportBooking
import dev.alllexey.itmowidgets.core.sport.SportScoreRepository
import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import dev.alllexey.itmowidgets.core.ui.navigation.AppScreen
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.feature.recordbook.RecordbookSemantics
import dev.alllexey.itmowidgets.feature.recordbook.data.demo.DemoRecordbook
import dev.alllexey.itmowidgets.feature.recordbook.domain.RecordbookRepository
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookSubject
import dev.alllexey.itmowidgets.feature.recordbook.ui.RecordbookPreviewActivity
import dev.alllexey.itmowidgets.feature.schedule.data.demo.DemoSchedule
import dev.alllexey.itmowidgets.feature.schedule.domain.model.Lesson
import dev.alllexey.itmowidgets.feature.schedule.domain.model.toDetailsArgs
import dev.alllexey.itmowidgets.feature.schedule.presentation.ScheduleUiState
import dev.alllexey.itmowidgets.feature.schedule.presentation.buildScheduleDisplayDays
import dev.alllexey.itmowidgets.feature.schedule.ui.details.LessonDetailsActions
import dev.alllexey.itmowidgets.feature.schedule.ui.details.LessonDetailsContent
import dev.alllexey.itmowidgets.feature.schedule.ui.details.LessonDetailsSheetState
import dev.alllexey.itmowidgets.feature.schedule.ui.list.ScheduleScreen
import dev.alllexey.itmowidgets.feature.schedule.ui.list.ScheduleScreenActions
import dev.alllexey.itmowidgets.feature.schedule.ui.list.scheduleScreenState
import dev.alllexey.itmowidgets.feature.settings.ui.SettingsPreviewActivity
import dev.alllexey.itmowidgets.feature.sport.data.demo.DemoSport
import dev.alllexey.itmowidgets.feature.sport.data.mapper.toBooking
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportAutoSignEntry
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportBooking
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportFreeSignEntry
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportLesson
import dev.alllexey.itmowidgets.feature.sport.ui.SportCardsPreviewActivity
import dev.alllexey.itmowidgets.testing.Screenshots
import dev.alllexey.itmowidgets.testing.TestUi
import java.time.LocalDate
import java.time.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toKotlinLocalDate
import kotlinx.datetime.toKotlinLocalDateTime
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Not a test: with `captureScreenshots=true` it walks the debug hosts with
 * lifelike, invented data and saves full-screen PNGs for the website
 * (`site-screenshots-light` / `-night`). `siteTheme=night` switches every host to
 * its dark appearance. No real account, no network.
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
        SettingsNavigationTestActivity.appearance = PreviewAppearance(dark = night)
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
            SettingsNavigationTestActivity.appearance = PreviewAppearance()
            SettingsNavigationTestActivity.homeFixture = HomeFixture()
            SettingsNavigationTestActivity.startDestination = R.id.navigation_home
            SettingsNavigationTestActivity.friendSelectorFixture = FriendSelectorFixture()
        }
    }

    private fun homeCards(): List<HomeCard> {
        val today = DemoSchedule.ownDays(TODAY.toKotlinLocalDate(), TODAY.toKotlinLocalDate(), TODAY.toKotlinLocalDate()).single()
        val rows = today.lessons.mapIndexed { index, lesson ->
            val state = if (index == 0) HomeLessonState.CURRENT else HomeLessonState.UPCOMING
            HomeScheduleRow.Lesson(lesson.toDetailsArgs(TODAY.toKotlinLocalDate()), state, progress = if (index == 0) 0.45f else null)
        }
        return listOf(
            HomeCard.Schedule(date = TODAY.toKotlinLocalDate(), tomorrow = false, rows = rows, completed = 0),
            HomeCard.ScheduleChanges(unread = 1, latest = DemoSchedule.changes(TODAY.toKotlinLocalDate(), TIME.now()).first()),
            HomeCard.Marks(listOf(DemoStudy.DATABASES.name, DemoStudy.DISCRETE.name)),
            HomeCard.Sport(DemoSport.score(TIME).summary, pendingBookings()),
            HomeCard.FriendRequests(REQUESTS)
        )
    }

    private fun pendingBookings(): List<PendingSportBooking> = DemoSport.queueEntries(TIME).map { entry ->
        val booking = entry.toBooking()
        PendingSportBooking(
            queueId = entry.id,
            queueKind = if (entry is SportAutoSignEntry) PendingSportBooking.QueueKind.AUTO else PendingSportBooking.QueueKind.FREE,
            lessonId = booking.lessonId,
            sectionName = booking.sectionName.raw,
            start = booking.start,
            end = booking.end,
            teacherFio = booking.teacherFio,
            roomName = booking.roomName,
            isPrediction = !booking.isLessonReal,
            teacherIsu = booking.teacherIsu
        )
    }
    // endregion

    // region Schedule (SettingsPreviewActivity with the shared screens; SH-3 replaces this region)

    /** The own schedule from today with the pending sport rows, then the second lesson's sheet. */
    private fun captureSchedule() {
        SettingsPreviewActivity.appearance = PreviewAppearance(dark = night)
        val today = TODAY.toKotlinLocalDate()
        val official = DemoSchedule.ownDays(today, TODAY.plusDays(2).toKotlinLocalDate(), today)
        val displayDays = buildScheduleDisplayDays(
            official, pendingBookings(), today, TODAY.plusDays(2).toKotlinLocalDate(), TIME.timeZone, TIME.now()
        )
        val state = scheduleScreenState(
            ScheduleUiState.Content(official, loadingMore = false, selectedUser = null, displayDays = displayDays),
            TIME.localNow(),
            TIME.timeZone,
            ownTab = true
        )
        val lessons = official.first().lessons
        val lesson = lessons.getOrElse(1) { lessons.first() }
        try {
            ActivityScenario.launch(SettingsPreviewActivity::class.java).use { scenario ->
                scenario.onActivity { it.showCompose { ScheduleScreen(state, ScheduleScreenActions()) } }
                settle()
                capture("schedule")
                scenario.onActivity {
                    it.showCompose {
                        LessonDetailsContent(
                            LessonDetailsSheetState(lesson.toDetailsArgs(today), mapAvailable = true),
                            LessonDetailsActions()
                        )
                    }
                }
                settle()
                capture("lesson")
            }
        } finally {
            SettingsPreviewActivity.appearance = PreviewAppearance()
        }
    }

    private fun SettingsPreviewActivity.showCompose(content: @Composable () -> Unit) =
        showContent(ComposeView(this).apply { setContent { ItmoTheme(content = content) } })

    // endregion

    // region Sport (SportCardsPreviewActivity)

    private fun captureSport() {
        SportCardsPreviewActivity.appearance = PreviewAppearance(dark = night)
        try {
            ActivityScenario.launch(SportCardsPreviewActivity::class.java).use { scenario ->
                scenario.onActivity { it.showLessons(catalog()) }
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
            SportCardsPreviewActivity.appearance = PreviewAppearance()
        }
    }

    /** The demo catalog with the queues and friends merged as the sport tab shows them. */
    private fun catalog(): List<SportLesson> {
        val entries = DemoSport.queueEntries(TIME).filterIsInstance<SportFreeSignEntry>().associateBy { it.lessonId }
        val friends = DemoSport.friendsBookings(TIME).groupBy { it.lessonId }
        return DemoSport.schedule(TIME).values.flatten().filter { it.start > TIME.now() }.take(CATALOG_SIZE).map {
            it.copy(signEntry = entries[it.lessonId], friendsBookings = friends[it.lessonId].orEmpty())
        }
    }

    private fun bookings(): List<SportBooking> {
        val friends = DemoSport.friendsBookings(TIME).groupBy { it.lessonId }
        return (DemoSport.bookings(TIME) + DemoSport.queueEntries(TIME).map { it.toBooking() })
            .map { it.copy(friendsBookings = friends[it.lessonId].orEmpty()) }
            .sortedBy { it.start }
    }
    // endregion

    // region Recordbook (RecordbookPreviewActivity)

    private fun captureRecordbook() {
        RecordbookPreviewActivity.appearance = PreviewAppearance(dark = night)
        RecordbookPreviewActivity.repository = SiteRecordbook()
        RecordbookPreviewActivity.sportRepository = object : SportScoreRepository {
            override suspend fun getScorePeriods() = AppResult.Success(DemoSport.periods(RECORDBOOK_TIME))
            override suspend fun getScoreSummary(semesterId: Long) = AppResult.Success(DemoSport.summary(semesterId, RECORDBOOK_TIME))
        }
        // The recordbook host's clock stands on 2026-06-01; the hub window starts there.
        RecordbookPreviewActivity.lessonsGateway = RecordbookPreviewActivity.MemoryLessons(
            DemoSchedule.ownDays(RECORDBOOK_TODAY.toKotlinLocalDate(), RECORDBOOK_TODAY.plusDays(15).toKotlinLocalDate(), RECORDBOOK_TODAY.toKotlinLocalDate()).flatMap { day ->
                day.lessons.filter { it.subjectId == DemoStudy.ALGORITHMS.id }.map { it.toSubjectLesson(day.date) }
            }
        )
        try {
            ActivityScenario.launch(RecordbookPreviewActivity::class.java).use { scenario ->
                settle()
                capture("recordbook")
                // The list is Compose (LR-3): the row opens by its semantics click.
                RecordbookSemantics.openSubject(scenario, "Алгоритмы", ::settle)
                capture("subject-scores")
                // One page: the second shot is its lower half with teachers and lessons, scrolled by semantics (LR-4b).
                scenario.onActivity(RecordbookSemantics::scrollSubjectToEnd)
                settle()
                capture("subject-schedule")
            }
        } finally {
            RecordbookPreviewActivity.appearance = PreviewAppearance()
            RecordbookPreviewActivity.repository = null
            RecordbookPreviewActivity.sportRepository = null
            RecordbookPreviewActivity.lessonsGateway = RecordbookPreviewActivity.MemoryLessons()
        }
    }

    private class SiteRecordbook : RecordbookRepository {
        override suspend fun getPrograms() = AppResult.Success(DemoRecordbook.programs(RECORDBOOK_TODAY.toKotlinLocalDate()))
        override suspend fun getSubjects(programId: Long, semester: Int): AppResult<List<RecordbookSubject>> =
            AppResult.Success(DemoRecordbook.subjects(programId, semester, RECORDBOOK_TIME.today(), RECORDBOOK_TIME.timeZone).orEmpty())
        override suspend fun getControls(entryId: Long) =
            AppResult.Success(DemoRecordbook.controls(entryId, RECORDBOOK_TIME.now()).orEmpty())
    }
    // endregion

    // region Builders

    private fun Lesson.toSubjectLesson(date: kotlinx.datetime.LocalDate) = SubjectLesson(
        pairId = pairId, date = date, start = start, end = end,
        typeId = typeId.raw, type = type, subjectId = subjectId,
        subjectName = subjectName, flowId = flowId, teacherIsu = teacherIsu, teacherFio = teacherFio, room = room?.raw,
        building = building?.raw, formatId = formatId
    )

    private fun me() = DemoPeople.ME_PERSON.summary()

    private fun settle() = TestUi.settle(900)

    private fun capture(name: String) = Screenshots.capture(directory, name) { settle() }

    // endregion

    private companion object {
        val TODAY: LocalDate = LocalDate.of(2026, 9, 7)
        val RECORDBOOK_TODAY: LocalDate = LocalDate.of(2026, 6, 1)
        val TIME = FixedTime(TODAY.atTime(11, 0))
        val RECORDBOOK_TIME = FixedTime(RECORDBOOK_TODAY.atTime(11, 0))
        const val ME_ISU = DemoPeople.ME_ISU
        const val ME_NAME = DemoPeople.ME_NAME
        const val CATALOG_SIZE = 6
        val FRIENDS = DemoPeople.FRIENDS.map { it.summary() }
        val REQUESTS = listOf(DemoPeople.SOFIA.summary())
    }

    /** The academic clock of the captures, standing still in Moscow. */
    private class FixedTime(private val at: LocalDateTime) : AcademicTimeProvider {
        override val timeZone: TimeZone = TimeZone.of("Europe/Moscow")
        override fun today() = at.toLocalDate().toKotlinLocalDate()
        override fun now() = at.toKotlinLocalDateTime().toInstant(timeZone)
    }
}
