package dev.alllexey.itmowidgets.feature.schedule.presentation.feed

import androidx.lifecycle.SavedStateHandle
import dev.alllexey.itmowidgets.core.coroutines.AppDispatchers
import dev.alllexey.itmowidgets.core.lesson.LessonDay
import dev.alllexey.itmowidgets.core.navigation.UserScreenArgs
import dev.alllexey.itmowidgets.core.presentation.RefreshMode
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.testing.FakeAppForeground
import dev.alllexey.itmowidgets.core.testing.FakeCalendarSync
import dev.alllexey.itmowidgets.core.testing.FixedAcademicTime
import dev.alllexey.itmowidgets.feature.schedule.DaysRefresh
import dev.alllexey.itmowidgets.feature.schedule.FakeScheduleDays
import dev.alllexey.itmowidgets.feature.schedule.feedLesson
import dev.alllexey.itmowidgets.testkit.TestMainDispatcher
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertSame
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime

class ScheduleFeedViewModelTest {

    private val main = TestMainDispatcher()
    private val monday = LocalDate(2026, 9, 7)
    private val time = FixedAcademicTime(LocalDateTime(monday, LocalTime(12, 22)))

    /** The window around Monday 7 September: the week before, its own and two after. */
    private val window = DaysRefresh(null, LocalDate(2026, 8, 31), LocalDate(2026, 9, 27))

    @BeforeTest
    fun setUp() = main.install()

    @AfterTest
    fun tearDown() = main.reset()

    private val foreground = FakeAppForeground()

    private fun viewModel(
        days: FakeScheduleDays,
        state: SavedStateHandle = SavedStateHandle(),
        teacher: FakeScheduleDays = FakeScheduleDays(),
    ) =
        ScheduleFeedViewModel(state, days, teacher, time, FakeCalendarSync(), foreground, AppDispatchers(main.dispatcher, main.dispatcher, main.dispatcher))

    @Test
    fun theFirstShowLoadsTheWindowOnceAndJumpsToToday() = runTest(main.dispatcher) {
        val days = FakeScheduleDays(LessonDay(monday, listOf(feedLesson("11:40", "13:10")), null))
        val vm = viewModel(days)

        vm.refresh(RefreshMode.Silent)
        vm.refresh(RefreshMode.Silent)
        val state = vm.uiState.first { it.body is ScheduleFeedBody.Days && !it.loading }

        assertEquals(listOf(window), days.refreshes)
        assertEquals(monday, state.jump?.date)
        vm.onJumped(state.jump!!.id)
        assertEquals(null, vm.uiState.first { it.jump == null }.jump)
    }

    @Test
    fun onlyAReturnToTheForegroundAfterTheFirstShowReloadsTheWindow() = runTest(main.dispatcher) {
        val days = FakeScheduleDays(LessonDay(monday, listOf(feedLesson("11:40", "13:10")), null))
        val vm = viewModel(days)
        runCurrent()

        foreground.comeBack()
        runCurrent()
        assertEquals(emptyList(), days.refreshes)

        vm.refresh(RefreshMode.Silent)
        runCurrent()
        foreground.comeBack()
        runCurrent()
        assertEquals(listOf(window, window), days.refreshes)
    }

    @Test
    fun aFailureWithoutContentIsTheErrorAndBehindContentAnEvent() = runTest(main.dispatcher) {
        val empty = FakeScheduleDays().apply { result = AppResult.Failure(AppError.Network) }
        val failed = viewModel(empty)
        failed.refresh(RefreshMode.Silent)
        assertEquals(ScheduleFeedBody.Failed(AppError.Network), failed.uiState.first { it.body is ScheduleFeedBody.Failed }.body)

        val cached = FakeScheduleDays(LessonDay(monday, listOf(feedLesson("11:40", "13:10")), null))
        val behind = viewModel(cached)
        behind.uiState.first { it.body is ScheduleFeedBody.Days }
        cached.result = AppResult.Failure(AppError.Network)
        behind.refresh(RefreshMode.Pull)

        assertEquals(ScheduleFeedEvent.ShowError(AppError.Network), behind.events.first())
    }

    @Test
    fun aPickedFriendLoadsTheirWeeksAndAClosedScheduleIsDenied() = runTest(main.dispatcher) {
        val days = FakeScheduleDays()
        days.results[FRIEND.isu] = AppResult.Failure(AppError.Forbidden)
        val vm = viewModel(days)
        vm.refresh(RefreshMode.Silent)

        vm.selectUser(FRIEND)
        val state = vm.uiState.first { it.body is ScheduleFeedBody.Failed }

        assertEquals(FRIEND, state.friend)
        assertEquals(ScheduleFeedBody.Failed(AppError.Forbidden), state.body)
        assertTrue(window.copy(userIsu = FRIEND.isu) in days.refreshes)

        vm.selectUser(null)
        assertEquals(null, vm.uiState.first { it.friend == null }.friend)
    }

    @Test
    fun aFriendSurvivesProcessDeathAndTheShortcutReturnsToTheOwnScheduleOnToday() = runTest(main.dispatcher) {
        val saved = SavedStateHandle()
        viewModel(FakeScheduleDays(), saved).selectUser(FRIEND)

        val restored = viewModel(FakeScheduleDays(), saved)
        assertEquals(FRIEND, restored.uiState.first { it.friend != null }.friend)

        restored.showDate(LocalDate(2026, 12, 1))
        restored.openToday()
        val state = restored.uiState.first { it.friend == null && it.jump?.date == monday }
        assertEquals(monday, state.selected)
    }

    @Test
    fun aFarDayMovesTheWindowAndNearTheEndTwoMoreWeeksLoad() = runTest(main.dispatcher) {
        val days = FakeScheduleDays()
        val vm = viewModel(days)
        vm.refresh(RefreshMode.Silent)

        vm.showDate(LocalDate(2026, 12, 2))
        runCurrent()
        assertEquals(LocalDate(2026, 12, 2), vm.uiState.first { it.jump?.date == LocalDate(2026, 12, 2) }.selected)
        assertEquals(DaysRefresh(null, LocalDate(2026, 11, 23), LocalDate(2026, 12, 20)), days.refreshes.last())

        vm.onNearEnd()
        runCurrent()
        assertEquals(DaysRefresh(null, LocalDate(2026, 12, 21), LocalDate(2027, 1, 3)), days.refreshes.last())
        vm.onNearStart()
        runCurrent()
        assertEquals(DaysRefresh(null, LocalDate(2026, 11, 9), LocalDate(2026, 11, 22)), days.refreshes.last())
    }

    @Test
    fun aSwipeToAnotherWeekShowsItsMondayAndTheCurrentWeekToday() = runTest(main.dispatcher) {
        val vm = viewModel(FakeScheduleDays())

        vm.beginWeekPaging()
        vm.showWeek(LocalDate(2026, 9, 14))
        assertEquals(LocalDate(2026, 9, 14), vm.uiState.first { it.selected == LocalDate(2026, 9, 14) }.selected)
        assertTrue(vm.uiState.value.offWeek)

        vm.beginWeekPaging()
        vm.showWeek(monday)
        assertEquals(monday, vm.uiState.first { it.selected == monday }.selected)
    }

    @Test
    fun anotherUsersScreenReadsItsArgumentsAndOffersNoPicker() = runTest(main.dispatcher) {
        val days = FakeScheduleDays()
        val state = SavedStateHandle(mapOf(UserScreenArgs.ISU to FRIEND.isu, UserScreenArgs.NAME to FRIEND.name))
        val vm = viewModel(days, state)

        vm.refresh(RefreshMode.Silent)
        vm.selectUser(null)
        val shown = vm.uiState.first { !it.loading }

        assertEquals(false, shown.canPickFriend)
        assertEquals(listOf(window.copy(userIsu = FRIEND.isu)), days.refreshes)
    }

    @Test
    fun aTeachersScreenReadsTheTeacherTimetableOnly() = runTest(main.dispatcher) {
        val days = FakeScheduleDays()
        val teacher = FakeScheduleDays()
        val state = SavedStateHandle(
            mapOf(UserScreenArgs.ISU to FRIEND.isu, UserScreenArgs.NAME to FRIEND.name, ScheduleFeedViewModel.ARG_TEACHER to true),
        )
        val vm = viewModel(days, state, teacher)

        vm.refresh(RefreshMode.Silent)
        val shown = vm.uiState.first { !it.loading }

        assertEquals(false, shown.canPickFriend)
        assertEquals(listOf(window.copy(userIsu = FRIEND.isu)), teacher.refreshes)
        assertEquals(emptyList(), days.refreshes)
    }

    @Test
    fun aRepeatedCallbackForTheSelectedWeekDoesNotJumpBackToMonday() = runTest(main.dispatcher) {
        val vm = viewModel(FakeScheduleDays())
        val wednesday = LocalDate(2026, 9, 16)
        vm.showDate(wednesday)
        val jumped = vm.uiState.first { it.selected == wednesday }
        vm.onJumped(jumped.jump!!.id)
        val before = vm.uiState.first { it.jump == null }

        vm.showWeek(LocalDate(2026, 9, 14))
        runCurrent()

        assertEquals(wednesday, vm.uiState.value.selected)
        assertEquals(before.jump, vm.uiState.value.jump)
    }

    @Test
    fun weekCallbacksKeepTheirDatesAfterPrependAndOldWindowsAreIgnored() = runTest(main.dispatcher) {
        val vm = viewModel(FakeScheduleDays())
        vm.onNearStart()
        runCurrent()
        val nextWeek = LocalDate(2026, 9, 14)
        vm.beginWeekPaging()
        vm.showWeek(nextWeek)
        assertEquals(nextWeek, vm.uiState.first { it.selected == nextWeek }.selected)

        val december = LocalDate(2026, 12, 2)
        vm.showDate(december)
        val jumped = vm.uiState.first { it.selected == december }
        vm.onJumped(jumped.jump!!.id)
        val moved = vm.uiState.first { it.jump == null }
        vm.showWeek(nextWeek)
        runCurrent()
        assertEquals(december, vm.uiState.value.selected)
        assertEquals(moved.jump, vm.uiState.value.jump)
    }

    @Test
    fun aLateWeekCallbackCannotUndoANewerExplicitJumpButANewGestureCanTakeOver() = runTest(main.dispatcher) {
        val vm = viewModel(FakeScheduleDays())
        vm.beginWeekPaging()
        val target = LocalDate(2026, 9, 16)
        vm.showDate(target)
        vm.showWeek(monday)
        assertEquals(target, vm.uiState.first { it.jump?.date == target }.selected)

        vm.beginWeekPaging()
        vm.showWeek(monday)
        assertEquals(monday, vm.uiState.first { it.jump?.date == monday }.selected)
    }

    @Test
    fun aLateVisibleDayCannotUndoAPendingJump() = runTest(main.dispatcher) {
        val vm = viewModel(FakeScheduleDays())
        val target = LocalDate(2026, 9, 16)
        vm.showDate(target)
        vm.onVisibleDate(monday)

        val jumped = vm.uiState.first { it.jump?.date == target }
        assertEquals(target, jumped.selected)
        vm.onJumped(jumped.jump!!.id)
        vm.onVisibleDate(LocalDate(2026, 9, 17))
        assertEquals(LocalDate(2026, 9, 17), vm.uiState.first { it.jump == null && it.selected != target }.selected)
    }

    @Test
    fun scrollingOnlyChangesSelectionAndReusesProjectedTimelinesAndWeeks() = runTest(main.dispatcher) {
        val vm = viewModel(FakeScheduleDays(LessonDay(monday, listOf(feedLesson("10:00", "11:30")), null)))
        val initial = vm.uiState.first { it.body is ScheduleFeedBody.Days }
        vm.onJumped(initial.jump!!.id)
        val before = vm.uiState.first { it.jump == null }
        val target = LocalDate(2026, 9, 16)

        vm.onVisibleDate(target)

        val after = vm.uiState.first { it.selected == target }
        assertSame(before.body, after.body)
        assertSame(before.weeks, after.weeks)
        assertEquals(2, after.selectedWeek)
        assertTrue(after.offWeek)
    }

    @Test
    fun repeatedRefreshesOfTheSameRangeShareTheRequest() = runTest(main.dispatcher) {
        val finish = CompletableDeferred<Unit>()
        val days = FakeScheduleDays().apply { onRefresh = { finish.await() } }
        val vm = viewModel(days)
        vm.refresh(RefreshMode.Silent)
        vm.refresh(RefreshMode.Pull)
        runCurrent()
        assertEquals(listOf(window), days.refreshes)
        finish.complete(Unit)
        assertEquals(false, vm.uiState.first { !it.loading }.refreshing)
    }

    @Test
    fun returningToTheSameOwnerDoesNotDeduplicateAgainstAnOldGeneration() = runTest(main.dispatcher) {
        val finish = CompletableDeferred<Unit>()
        val days = FakeScheduleDays().apply { onRefresh = { finish.await() } }
        val vm = viewModel(days)
        vm.refresh(RefreshMode.Silent)
        vm.selectUser(FRIEND)
        vm.selectUser(null)
        runCurrent()
        assertEquals(listOf(null, FRIEND.isu, null), days.refreshes.map { it.userIsu })
        finish.complete(Unit)
        assertEquals(null, vm.uiState.first { !it.loading && it.friend == null }.friend)
    }

    @Test
    fun repeatedEdgeCallbacksOnlyLoadOnePageInEachDirection() = runTest(main.dispatcher) {
        val finish = CompletableDeferred<Unit>()
        val days = FakeScheduleDays().apply { onRefresh = { finish.await() } }
        val vm = viewModel(days)
        vm.onNearEnd()
        vm.onNearEnd()
        vm.onNearStart()
        runCurrent()
        assertEquals(2, days.refreshes.size)
        finish.complete(Unit)
        vm.uiState.first { !it.loading }
    }

    @Test
    fun reachingAnEdgeDuringRefreshStillLoadsTheNextPage() = runTest(main.dispatcher) {
        val finish = CompletableDeferred<Unit>()
        val days = FakeScheduleDays().apply { onRefresh = { finish.await() } }
        val vm = viewModel(days)
        vm.refresh(RefreshMode.Pull)
        vm.onNearEnd()
        runCurrent()
        assertEquals(2, days.refreshes.size)
        assertEquals(window, days.refreshes.first())
        assertEquals(LocalDate(2026, 9, 28), days.refreshes.last().from)
        finish.complete(Unit)
        vm.uiState.first { !it.loading }
    }

    @Test
    fun aFarJumpDoesNotKeepTheOldWindowsEdgeRequestAsAGuard() = runTest(main.dispatcher) {
        val finish = CompletableDeferred<Unit>()
        val days = FakeScheduleDays().apply { onRefresh = { finish.await() } }
        val vm = viewModel(days)
        vm.onNearEnd()
        vm.showDate(LocalDate(2026, 12, 2))
        vm.onNearEnd()
        runCurrent()
        assertEquals(3, days.refreshes.size)
        assertEquals(LocalDate(2026, 12, 21), days.refreshes.last().from)
        finish.complete(Unit)
        vm.uiState.first { !it.loading }
    }

    private companion object {
        val FRIEND = SelectedUser(300002, "Егорова Полина", null, "M3204")
    }
}
