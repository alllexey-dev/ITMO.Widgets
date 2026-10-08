package dev.alllexey.itmowidgets.app.shell.entries

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import dev.alllexey.itmowidgets.app.shell.BottomSheetSceneStrategy
import dev.alllexey.itmowidgets.app.shell.EntryRegistry
import dev.alllexey.itmowidgets.app.shell.Nav3AppNavigator
import dev.alllexey.itmowidgets.app.shell.ShellContent
import dev.alllexey.itmowidgets.app.shell.entryRegistry
import dev.alllexey.itmowidgets.app.shell.rememberNav3AppNavigator
import dev.alllexey.itmowidgets.core.navigation.AppRoute
import dev.alllexey.itmowidgets.core.navigation.AppRoutes
import dev.alllexey.itmowidgets.core.navigation.AppTab
import dev.alllexey.itmowidgets.core.navigation.EntryRoute
import dev.alllexey.itmowidgets.core.navigation.ShellBackStack
import dev.alllexey.itmowidgets.core.navigation.ShellSurface
import dev.alllexey.itmowidgets.core.navigation.TabRequest
import dev.alllexey.itmowidgets.core.navigation.toDetailsArgs
import dev.alllexey.itmowidgets.core.result.LoadState
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.di.bridge.StopKoinRule
import dev.alllexey.itmowidgets.feature.debug.ui.PreviewHostApplication
import dev.alllexey.itmowidgets.feature.friendselector.ui.FriendSelectorTestTags
import dev.alllexey.itmowidgets.feature.schedule.domain.model.DaySchedule
import dev.alllexey.itmowidgets.feature.schedule.domain.model.Lesson
import dev.alllexey.itmowidgets.feature.schedule.domain.model.toDetailsArgs
import dev.alllexey.itmowidgets.feature.schedule.ui.changes.ScheduleChangesTestTags
import dev.alllexey.itmowidgets.feature.schedule.ui.details.LessonDetailsTestTags
import dev.alllexey.itmowidgets.feature.schedule.ui.details.PendingSportDetailsTestTags
import dev.alllexey.itmowidgets.feature.schedule.ui.list.ScheduleListTestTags
import dev.alllexey.itmowidgets.feature.schedule.ui.list.ScheduleScreenTestTags
import dev.alllexey.itmowidgets.feature.schedule.ui.list.UserScheduleTestTags
import dev.alllexey.itmowidgets.feature.sport.cards.SportCardFixtures
import dev.alllexey.itmowidgets.feature.sport.domain.model.SectionName
import dev.alllexey.itmowidgets.feature.sport.navigation.SportDetailsOpener
import dev.alllexey.itmowidgets.feature.sport.navigation.SportRoutes
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.toInstant
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.compose.KoinContext
import org.koin.core.Koin
import org.koin.core.context.startKoin
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The schedule keys in the Compose shell with Koin ViewModels on synthetic data: the tab root, another user's
 * schedule, the changes and both sheets render under the real [shellEntries]; the root opens the lesson sheet and the
 * friend picker and shows the picked friend, and a sport lesson opens the sport tab's sheet of its booking through
 * the sport redirect; `ScheduleToday` reaches the own schedule exactly once, also across a
 * saved-state restore, and another user's schedule never takes it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = PreviewHostApplication::class)
class ScheduleEntriesTest {

    @get:Rule(order = 0)
    val stopKoin = StopKoinRule()

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val schedules = ScheduleSamples.repository()
    private val sportScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val sport = SportTestGraph(sportScope)
    private lateinit var koin: Koin
    private lateinit var navigator: Nav3AppNavigator

    @Before
    fun startGraph() {
        koin = startKoin { modules(scheduleTestModule(schedules), socialTestModule(), sport.holderModule()) }.koin
    }

    @After
    fun stopSportScope() = sportScope.cancel()

    @Test
    fun theScheduleKeysRenderTheirScreensAndCloseThemselves() {
        show(Nav3AppNavigator(ShellBackStack(AppTab.SCHEDULE)))
        compose.onNodeWithTag(ScheduleListTestTags.lesson(LESSON.pairId)).assertIsDisplayed()
        compose.onNodeWithTag(EntryRegistry.placeholderTag(AppRoutes.TabRoot(AppTab.SCHEDULE))).assertDoesNotExist()

        assertRendersAndCloses(AppRoutes.UserSchedule(FRIEND_ISU, FRIEND_NAME), UserScheduleTestTags.TOP_BAR)
        assertTrue(schedules.observed.any { it.userIsu == FRIEND_ISU })
        assertRendersAndCloses(AppRoutes.ScheduleChanges, ScheduleChangesTestTags.EMPTY)

        assertSheetRenders(AppRoutes.LessonDetails(LESSON_ARGS), LessonDetailsTestTags.SCROLL)
        assertSheetRenders(
            AppRoutes.PendingSportDetails(ScheduleSamples.BOOKING.toDetailsArgs(ScheduleSamples.time.timeZone)),
            PendingSportDetailsTestTags.SCROLL,
        )
    }

    @Test
    fun aLessonOpensItsSheet() {
        show(Nav3AppNavigator(ShellBackStack(AppTab.SCHEDULE)))

        compose.onNodeWithTag(ScheduleListTestTags.lesson(LESSON.pairId)).performClick()
        compose.waitForIdle()

        assertEquals(listOf<AppRoute>(AppRoutes.LessonDetails(LESSON_ARGS)), navigator.state.floating)
    }

    @Test
    fun aSportLessonOnTheRootOpensTheSportSheetOfItsBooking() {
        schedules.days.value = listOf(DaySchedule(DATE.dayOfWeek.isoDayNumber, 1, DATE, null, listOf(SPORT_LESSON)))
        val start = LocalDateTime(DATE, SPORT_LESSON.start).toInstant(ScheduleSamples.time.timeZone)
        val booking = SportCardFixtures.booking(SPORT_BOOKING_ID)
            .copy(start = start, end = start + 90.minutes, sectionName = SectionName(SPORT_LESSON.subjectName))
        sport.bookings.value = LoadState.Content(listOf(booking))
        show(Nav3AppNavigator(ShellBackStack(AppTab.SCHEDULE)))

        compose.onNodeWithTag(ScheduleListTestTags.lesson(SPORT_LESSON.pairId)).performClick()
        compose.waitForIdle()

        val sheet = navigator.state.floating.single() as SportRoutes.SportCommonDetails
        assertEquals(SPORT_BOOKING_ID, sheet.item.lessonId)
        assertEquals(SportDetailsOpener.SHELL, sheet.replyTo)
    }

    @Test
    fun anotherUsersLessonOpensItsSheetAboveThatSchedule() {
        show(Nav3AppNavigator(ShellBackStack(AppTab.SCHEDULE)), withoutScheduleRoot())
        act { open(AppRoutes.UserSchedule(FRIEND_ISU, FRIEND_NAME)) }

        compose.onNodeWithTag(ScheduleListTestTags.lesson(LESSON.pairId)).performClick()
        compose.waitForIdle()

        assertEquals(listOf<AppRoute>(AppRoutes.UserSchedule(FRIEND_ISU, FRIEND_NAME)), navigator.state.overlays)
        assertEquals(listOf<AppRoute>(AppRoutes.LessonDetails(LESSON_ARGS)), navigator.state.floating)
    }

    @Test
    fun theFriendButtonOpensThePickerAndThePickedFriendIsShown() {
        show(Nav3AppNavigator(ShellBackStack(AppTab.SCHEDULE)))

        pickFriend()

        assertTrue(navigator.state.floating.isEmpty())
        compose.onNodeWithTag(ScheduleScreenTestTags.SELECTED_USER).assertIsDisplayed()
        assertTrue(schedules.refreshed.any { it.userIsu == FRIEND_ISU })
    }

    @Test
    fun scheduleTodayShowsTheOwnScheduleOnce() {
        show(Nav3AppNavigator(ShellBackStack(AppTab.SCHEDULE)))
        pickFriend()

        act { apply(EntryRoute(tab = AppTab.SCHEDULE, request = TabRequest.ScheduleToday)) }

        assertNull(navigator.pendingRequest(AppTab.SCHEDULE))
        compose.onNodeWithTag(ScheduleScreenTestTags.SELECTED_USER).assertDoesNotExist()
        pickFriend()
        compose.onNodeWithTag(ScheduleScreenTestTags.SELECTED_USER).assertIsDisplayed()
    }

    @Test
    fun scheduleTodayWaitsAcrossASavedStateRestoreAndIsDeliveredOnce() {
        var surface: ShellSurface by mutableStateOf(ShellSurface.Tabs(demoBanner = false))
        // The saved navigator starts on the home tab, whose root this test does not need.
        val scheduleAndPicker = entryRegistry {
            entry<AppRoutes.TabRoot> { key, shown -> if (key.tab == AppTab.SCHEDULE) ScheduleTabRoot(shown) }
            scheduleEntries()
            socialEntries()
        }
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            navigator = rememberNav3AppNavigator()
            KoinContext(koin) {
                ItmoTheme { ShellContent(navigator, scheduleAndPicker, surface, onDemoSignIn = {}) }
            }
        }
        act { select(AppTab.SCHEDULE) }
        pickFriend()
        surface = ShellSurface.Progress
        act { apply(EntryRoute(tab = AppTab.SCHEDULE, request = TabRequest.ScheduleToday)) }

        restoration.emulateSavedInstanceStateRestore()
        compose.runOnIdle { assertEquals(TabRequest.ScheduleToday, navigator.pendingRequest(AppTab.SCHEDULE)) }

        surface = ShellSurface.Tabs(demoBanner = false)
        compose.waitForIdle()
        assertNull(navigator.pendingRequest(AppTab.SCHEDULE))
        compose.onNodeWithTag(ScheduleScreenTestTags.LIST).assertIsDisplayed()
        compose.onNodeWithTag(ScheduleScreenTestTags.SELECTED_USER).assertDoesNotExist()

        pickFriend()
        restoration.emulateSavedInstanceStateRestore()
        compose.waitForIdle()
        assertNull(navigator.pendingRequest(AppTab.SCHEDULE))
        compose.onNodeWithTag(ScheduleScreenTestTags.SELECTED_USER).assertIsDisplayed()
    }

    @Test
    fun anotherUsersScheduleNeverTakesScheduleToday() {
        val waiting = ShellBackStack(
            tab = AppTab.SCHEDULE,
            overlays = listOf(AppRoutes.UserSchedule(FRIEND_ISU, FRIEND_NAME)),
            requests = mapOf(AppTab.SCHEDULE to TabRequest.ScheduleToday),
        )
        show(Nav3AppNavigator(waiting), withoutScheduleRoot())

        compose.onNodeWithTag(UserScheduleTestTags.TOP_BAR).assertIsDisplayed()
        compose.onNodeWithTag(ScheduleListTestTags.lesson(LESSON.pairId)).assertIsDisplayed()
        assertEquals(TabRequest.ScheduleToday, navigator.pendingRequest(AppTab.SCHEDULE))
    }

    private fun show(shown: Nav3AppNavigator, registry: EntryRegistry = shellEntries()) {
        navigator = shown
        compose.setContent {
            // Koin Compose caches the first graph it reads for the JVM; this test's graph replaces the stopped one.
            KoinContext(koin) {
                ItmoTheme { ShellContent(navigator, registry, ShellSurface.Tabs(demoBanner = false), onDemoSignIn = {}) }
            }
        }
        compose.waitForIdle()
    }

    /** The schedule keys over empty tab roots, so only another user's list is on screen. */
    private fun withoutScheduleRoot() = entryRegistry {
        entry<AppRoutes.TabRoot> { _, _ -> }
        scheduleEntries()
    }

    private fun act(block: Nav3AppNavigator.() -> Unit) {
        compose.runOnIdle { navigator.block() }
        compose.waitForIdle()
    }

    /** The own schedule's friend button opens the picker, which hands the one friend back to the schedule. */
    private fun pickFriend() {
        compose.onNodeWithTag(ScheduleScreenTestTags.FRIENDS_BUTTON).performClick()
        compose.waitForIdle()
        assertTrue(navigator.state.floating.single() is AppRoutes.FriendSelector)
        compose.onNodeWithTag(FriendSelectorTestTags.row(FRIEND_ISU)).performClick()
        compose.waitForIdle()
        compose.onNodeWithTag(FriendSelectorTestTags.APPLY).performClick()
        compose.waitForIdle()
    }

    /** [route] shows its screen's [tag] instead of the placeholder, and its back button closes it. */
    private fun assertRendersAndCloses(route: AppRoute, tag: String) {
        act { open(route) }
        compose.onNodeWithTag(tag).assertIsDisplayed()
        compose.onNodeWithTag(EntryRegistry.placeholderTag(route)).assertDoesNotExist()
        compose.onNodeWithContentDescription(BACK).performClick()
        compose.waitForIdle()
        assertTrue("$route", navigator.state.overlays.isEmpty())
    }

    /** The sheet of [route] shows its body's [tag] instead of the placeholder; reselecting the tab closes it. */
    private fun assertSheetRenders(route: AppRoute, tag: String) {
        act { open(route) }
        compose.onNodeWithTag(BottomSheetSceneStrategy.tag(route.toString())).assertExists()
        compose.onNodeWithTag(tag).assertExists()
        compose.onNodeWithTag(EntryRegistry.placeholderTag(route)).assertDoesNotExist()
        act { select(AppTab.SCHEDULE) }
        assertTrue("$route", navigator.state.floating.isEmpty())
    }

    private companion object {
        const val BACK = "Назад"
        const val FRIEND_ISU = SocialSamples.FRIEND_ISU
        const val FRIEND_NAME = SocialSamples.FRIEND_NAME
        val LESSON = ScheduleSamples.LESSON
        val LESSON_ARGS = LESSON.toDetailsArgs(ScheduleSamples.DATE)
        val DATE = ScheduleSamples.DATE
        const val SPORT_BOOKING_ID = 7L

        /** A sport lesson (type 11) of the own schedule, in the slot of the sport graph's one booking. */
        val SPORT_LESSON = LESSON.copy(
            pairId = 7002, typeId = Lesson.TypeId(11), type = "Спорт", subjectName = "Плавание",
            start = LocalTime(18, 30), end = LocalTime(20, 0),
        )
    }
}
