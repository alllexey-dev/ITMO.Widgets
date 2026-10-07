package dev.alllexey.itmowidgets.feature.schedule.ui.list

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import dev.alllexey.itmowidgets.core.presentation.RefreshMode
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.sport.PendingSportBooking
import dev.alllexey.itmowidgets.core.testing.FakeCalendarSync
import dev.alllexey.itmowidgets.core.testing.FakePendingSportBookingsRepository
import dev.alllexey.itmowidgets.core.testing.FakeSchedulePreferencesRepository
import dev.alllexey.itmowidgets.core.testing.FixedAcademicTime
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.feature.schedule.FakeScheduleChangesRepository
import dev.alllexey.itmowidgets.feature.schedule.FakeScheduleRepository
import dev.alllexey.itmowidgets.feature.schedule.domain.model.DaySchedule
import dev.alllexey.itmowidgets.feature.schedule.domain.model.Lesson
import dev.alllexey.itmowidgets.feature.schedule.presentation.ScheduleUiState
import dev.alllexey.itmowidgets.feature.schedule.presentation.ScheduleViewModel
import dev.alllexey.itmowidgets.feature.schedule.presentation.SelectedUser
import dev.alllexey.itmowidgets.feature.schedule.ui.list.preview.ScheduleListPreviewData
import dev.alllexey.itmowidgets.testkit.RobolectricTestRunner
import dev.alllexey.itmowidgets.testkit.RunWith
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.atTime
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.robolectric.annotation.Config

/**
 * `ScheduleRoute` over a real `ScheduleViewModel` and fakes, the cases of the deleted `ScheduleFragmentLifecycleTest`:
 * the position survives recreation and late data; a refresh, a failure with its snackbar retry and pending-only
 * updates keep the day and the offset in every frame; a friend switch keeps the day, pages toward it or falls back
 * to today; the `Сегодня` request scrolls once, also from a friend; rows and the picker reach the host.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class ScheduleRouteTest {

    @get:Rule
    val compose = createComposeRule()

    private val time = FixedAcademicTime(LocalDateTime(2026, 9, 7, 12, 0))
    private val today = time.today()
    private val repository = FakeScheduleRepository()
    private val preferences = FakeSchedulePreferencesRepository()
    private val pending = FakePendingSportBookingsRepository()
    private val requests = Channel<ScheduleRouteRequest>(Channel.UNLIMITED)
    private val requestFlow = requests.receiveAsFlow()
    private val lessons = mutableListOf<Pair<Long, LocalDate>>()
    private val bookings = mutableListOf<Long>()
    private val picks = mutableListOf<SelectedUser?>()
    private val actions = ScheduleRouteActions(
        onLessonClick = { lesson, date -> lessons += lesson.pairId to date },
        onPendingClick = { bookings += it.queueId },
        onPickFriend = { picks += it },
    )

    private var viewModel = viewModel()
    private lateinit var listState: LazyListState
    private lateinit var scope: CoroutineScope

    /** Every position the list reported after [recordPositions]. */
    private val positions = mutableListOf<Pair<Int, Int>>()
    private var recording = false

    @Test
    fun thePositionSurvivesRecreationAndADataSourceThatAnswersLate() {
        repository.days.value = days(30)
        val restoration = StateRestorationTester(compose)
        restoration.setContent { Route() }
        awaitDays(16)
        awaitToday()
        scrollTo(8, 40)

        restoration.emulateSavedInstanceStateRestore()
        awaitDays(16)
        assertEquals(8 to 40, position())

        // Process death: a fresh ViewModel whose schedule arrives after the screen is back.
        val late = FakeScheduleRepository()
        val answer = CompletableDeferred<AppResult<Unit>>()
        late.refreshHandler = { answer.await() }
        viewModel = viewModel(late)
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag(ScheduleScreenTestTags.LOADING).assertExists()
        compose.runOnIdle {
            late.days.value = days(30)
            answer.complete(AppResult.Success(Unit))
        }
        awaitDays(16)
        assertEquals(8 to 40, position())
    }

    @Test
    fun aRefreshOfAPagedListKeepsTheDayAndOffsetInEveryFrame() {
        repository.days.value = days(60)
        show()
        pageOnce()
        scrollTo(17, 40)
        val answer = CompletableDeferred<Unit>()
        repository.refreshHandler = {
            answer.await()
            repository.days.value = days(60, note = "Обновлено")
            AppResult.Success(Unit)
        }
        recordPositions()

        compose.runOnIdle { viewModel.refresh(RefreshMode.Pull) }
        compose.waitUntil { contentOrNull()?.loadingMore == true }
        compose.runOnIdle { answer.complete(Unit) }
        compose.waitUntil { contentOrNull()?.let { !it.loadingMore && it.schedule.all { day -> day.note == "Обновлено" } } == true }

        assertEquals(30, listState.layoutInfo.totalItemsCount)
        assertEquals(0, repository.clears)
        assertEquals(17 to 40, position())
        assertTrue(positions.toString(), positions.all { it == 17 to 40 })
    }

    @Test
    fun aFailedRefreshShowsTheSnackbarAndItsRetryKeepsTheViewport() {
        repository.days.value = days(60)
        show()
        pageOnce()
        scrollTo(17, 40)
        recordPositions()
        repository.refreshResult = AppResult.Failure(AppError.Network)

        compose.runOnIdle { viewModel.refresh(RefreshMode.Pull) }
        compose.onNodeWithText("Повторить", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag(ScheduleScreenTestTags.STATE).assertDoesNotExist()
        val retry = CompletableDeferred<AppResult<Unit>>()
        repository.refreshHandler = { retry.await() }
        compose.onNodeWithText("Повторить", useUnmergedTree = true).performClick()
        compose.waitUntil { contentOrNull()?.loadingMore == true }
        compose.runOnIdle { retry.complete(AppResult.Success(Unit)) }
        compose.waitUntil { contentOrNull()?.loadingMore == false }

        assertEquals(30, listState.layoutInfo.totalItemsCount)
        assertEquals(17 to 40, position())
        assertTrue(positions.toString(), positions.all { it == 17 to 40 })
    }

    @Test
    fun pendingOnlyRefreshAndPaginationKeepTheVisibleDay() {
        preferences.enabled.value = true
        pending.values.value = AppResult.Success((1..42).map(::booking))
        show()
        awaitDays(14)
        compose.waitForIdle()
        assertTrue(content().schedule.isEmpty())
        scrollTo(6, 40)
        recordPositions()
        val answer = CompletableDeferred<AppResult<Unit>>()
        repository.refreshHandler = { answer.await() }

        compose.runOnIdle { viewModel.refresh(RefreshMode.Pull) }
        compose.waitUntil { contentOrNull()?.loadingMore == true }
        assertEquals(14, listState.layoutInfo.totalItemsCount)
        compose.runOnIdle { answer.complete(AppResult.Success(Unit)) }
        compose.waitUntil { contentOrNull()?.loadingMore == false }
        assertEquals(6 to 40, position())
        assertTrue(positions.toString(), positions.all { it == 6 to 40 })

        // The reader scrolls near the end: the next page comes in under the same day.
        val page = CompletableDeferred<AppResult<Unit>>()
        repository.refreshHandler = { page.await() }
        scrollTo(13, 0, exact = false)
        compose.waitUntil { contentOrNull()?.loadingMore == true }
        awaitDays(28)
        val anchor = position()
        positions.clear()
        compose.runOnIdle { page.complete(AppResult.Success(Unit)) }
        compose.waitUntil { contentOrNull()?.loadingMore == false }
        assertEquals(anchor, position())
        assertTrue(positions.all { it == anchor })
    }

    @Test
    fun aFriendSwitchKeepsTheDayAndItsOffset() {
        repository.days.value = days(30)
        repository.schedulesFor(FRIEND.isu).value = days(30).drop(3)
        show()
        awaitDays(16)
        awaitToday()
        scrollTo(8, 40)
        val day = today.plus(7, DateTimeUnit.DAY)

        request(ScheduleRouteRequest.SelectUser(FRIEND))
        compose.waitUntil { contentOrNull()?.selectedUser == FRIEND && listState.layoutInfo.totalItemsCount == 13 }

        assertEquals(day to 40, visibleDay())
        compose.onNodeWithTag(ScheduleScreenTestTags.SELECTED_USER).assertExists()
    }

    @Test
    fun aFriendSwitchPagesUntilADayBeyondTheFirstPage() {
        repository.days.value = days(60)
        repository.schedulesFor(FRIEND.isu).value = days(60).drop(1)
        show()
        pageOnce()
        scrollTo(17, 40)
        val day = today.plus(16, DateTimeUnit.DAY)

        request(ScheduleRouteRequest.SelectUser(FRIEND))
        compose.waitUntil {
            val state = viewModel.uiState.value
            state is ScheduleUiState.Content && state.selectedUser == FRIEND && !state.loadingMore &&
                listState.layoutInfo.totalItemsCount >= 29
        }

        assertEquals(day to 40, visibleDay())
        assertTrue(repository.refreshed.count { it.userIsu == FRIEND.isu } >= 2)
    }

    @Test
    fun aFriendSwitchWithoutTheReadDayOpensOnToday() {
        repository.days.value = days(60)
        repository.schedulesFor(FRIEND.isu).value = days(10)
        show()
        pageOnce()
        scrollTo(17, 40)

        request(ScheduleRouteRequest.SelectUser(FRIEND))
        compose.waitUntil {
            val state = viewModel.uiState.value
            state is ScheduleUiState.Content && state.selectedUser == FRIEND && !state.loadingMore
        }

        assertEquals(10, listState.layoutInfo.totalItemsCount)
        awaitToday()
    }

    @Test
    fun theTodayRequestScrollsOnceAlsoFromAFriend() {
        repository.days.value = days(30)
        repository.schedulesFor(FRIEND.isu).value = days(30).drop(3)
        show()
        awaitDays(16)
        awaitToday()
        scrollTo(15, 0, exact = false)
        assertTrue("the reader left today", todayTop() == null)

        request(ScheduleRouteRequest.Today)
        awaitToday()

        // Later data without a request keeps the reader where they scrolled.
        scrollTo(8, 40)
        compose.runOnIdle { repository.days.value = days(31, note = "Обновлено") }
        compose.waitUntil { contentOrNull()?.schedule?.all { it.note == "Обновлено" } == true }
        assertEquals(8 to 40, position())

        request(ScheduleRouteRequest.SelectUser(FRIEND))
        compose.waitUntil { contentOrNull()?.selectedUser == FRIEND && listState.layoutInfo.totalItemsCount == 13 }
        request(ScheduleRouteRequest.Today)
        compose.waitUntil { contentOrNull()?.let { it.selectedUser == null && listState.layoutInfo.totalItemsCount == 16 } == true }
        awaitToday()
    }

    @Test
    fun rowsReachTheirSheetsAndThePickerKnowsTheShownFriend() {
        preferences.enabled.value = true
        repository.days.value = listOf(day(today))
        repository.schedulesFor(FRIEND.isu).value = listOf(day(today))
        pending.values.value = AppResult.Success(listOf(booking(days = 0)))
        show()
        awaitDays(1)

        compose.onNodeWithTag(ScheduleListTestTags.lesson(LESSON_ID)).performClick()
        compose.onNodeWithTag(ScheduleScreenTestTags.LIST).performScrollToNode(hasTestTag(ScheduleListTestTags.pending(0)))
        compose.onNodeWithTag(ScheduleListTestTags.pending(0)).performClick()
        compose.onNodeWithTag(ScheduleScreenTestTags.FRIENDS_BUTTON).performClick()
        request(ScheduleRouteRequest.SelectUser(FRIEND))
        compose.waitUntil { contentOrNull()?.selectedUser == FRIEND }
        compose.onNodeWithText("Сменить").performClick()
        compose.onNodeWithTag(ScheduleScreenTestTags.SELECTED_USER).assertExists()

        assertEquals(listOf(LESSON_ID to today), lessons)
        assertEquals(listOf(0L), bookings)
        assertEquals(listOf(null, FRIEND), picks)

        compose.onNodeWithContentDescription("Вернуться к своему").performClick()
        compose.waitUntil { contentOrNull()?.selectedUser == null && contentOrNull() != null }
        assertNull(content().selectedUser)
    }

    @Test
    fun aUserScheduleOffersNoPickerAndNoShortcut() {
        repository.days.value = days(30)
        show(ownTab = false)
        awaitDays(16)

        compose.onNodeWithTag(ScheduleScreenTestTags.FRIENDS_BUTTON).assertDoesNotExist()
        assertFalse(content().loadingMore)
    }

    // region Harness

    private fun viewModel(source: FakeScheduleRepository = repository) = ScheduleViewModel(
        repository = source,
        timeProvider = time,
        savedStateHandle = SavedStateHandle(),
        preferences = preferences,
        pendingRepository = pending,
        changesRepository = FakeScheduleChangesRepository(),
        calendarSync = FakeCalendarSync(),
    )

    @Composable
    private fun Route(ownTab: Boolean = true) = ItmoTheme {
        val state = rememberLazyListState()
        listState = state
        scope = rememberCoroutineScope()
        LaunchedEffect(state) {
            snapshotFlow { state.firstVisibleItemIndex to state.firstVisibleItemScrollOffset }
                .collect { if (recording) positions += it }
        }
        ScheduleRoute(
            ownTab = ownTab,
            actions = actions,
            requests = requestFlow,
            viewModel = viewModel,
            timeProvider = time,
            listState = state,
        )
    }

    private fun show(ownTab: Boolean = true) = compose.setContent { Route(ownTab) }

    private fun content(): ScheduleUiState.Content = viewModel.uiState.value as ScheduleUiState.Content

    private fun contentOrNull(): ScheduleUiState.Content? = viewModel.uiState.value as? ScheduleUiState.Content

    /** Today's card at the top with the peek of the day before, as a fresh screen opens. */
    private fun awaitToday() = compose.waitUntil { todayTop() == peek() }

    private fun todayTop(): Int? = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == today.toString() }?.offset

    private fun awaitDays(count: Int) = compose.waitUntil {
        viewModel.uiState.value is ScheduleUiState.Content && listState.layoutInfo.totalItemsCount == count
    }

    /** The own schedule's second page, as the list asks for it near its end. */
    private fun pageOnce() {
        awaitDays(16)
        awaitToday()
        compose.runOnIdle { viewModel.fetchNextDays() }
        compose.waitUntil { contentOrNull()?.loadingMore == false && listState.layoutInfo.totalItemsCount == 30 }
    }

    /** Scrolls as a reader does; [exact] when the list is long enough below [index] not to stop at its end. */
    private fun scrollTo(index: Int, offset: Int, exact: Boolean = true) {
        compose.runOnIdle { scope.launch { listState.scrollToItem(index, offset) } }
        compose.waitForIdle()
        if (exact) assertEquals(index to offset, position())
    }

    private fun recordPositions() = compose.runOnIdle {
        positions.clear()
        recording = true
    }

    private fun request(request: ScheduleRouteRequest) {
        compose.runOnIdle { requests.trySend(request) }
        compose.waitForIdle()
    }

    private fun position(): Pair<Int, Int> =
        compose.runOnIdle { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }

    private fun visibleDay(): Pair<LocalDate, Int> = compose.runOnIdle {
        val first = listState.layoutInfo.visibleItemsInfo.single { it.index == listState.firstVisibleItemIndex }
        LocalDate.parse(first.key as String) to listState.firstVisibleItemScrollOffset
    }

    private fun peek(): Int = with(compose.density) { 8.dp.roundToPx() }

    private fun days(count: Int, note: String? = null): List<DaySchedule> =
        (0 until count).map { day(today.plus(it - 1, DateTimeUnit.DAY), note) }

    private fun day(date: LocalDate, note: String? = null) = DaySchedule(
        dayNumber = date.dayOfWeek.isoDayNumber,
        weekNumber = 1,
        date = date,
        note = note,
        lessons = listOf(lesson()),
    )

    private fun lesson(): Lesson = ScheduleListPreviewData.lesson(LESSON_ID, LocalTime(10, 0), "Физика", 3)

    /** A predicted auto-sign row [days] after today at 16:00, after the clock's noon. */
    private fun booking(days: Int): PendingSportBooking {
        val start = today.plus(days, DateTimeUnit.DAY).atTime(16, 0).toInstant(time.timeZone)
        return PendingSportBooking(
            queueId = days.toLong(),
            queueKind = PendingSportBooking.QueueKind.AUTO,
            lessonId = 1_000L + days,
            sectionName = "Тестовая секция плавания",
            start = start,
            end = start + 90.minutes,
            teacherFio = "Тестовый преподаватель",
            roomName = "Тестовый корпус",
            isPrediction = true,
        )
    }

    // endregion

    private companion object {
        const val LESSON_ID = 7L
        val FRIEND = SelectedUser(isu = 123456, name = "Тестовый друг", avatar = null)
    }
}
