package dev.alllexey.itmowidgets.feature.home.presentation

import dev.alllexey.itmowidgets.core.home.HomeCard
import dev.alllexey.itmowidgets.core.home.HomeCardKind
import dev.alllexey.itmowidgets.core.home.HomeHint
import dev.alllexey.itmowidgets.core.presentation.RefreshMode
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.testing.FakeHomeCardSource
import dev.alllexey.itmowidgets.core.testing.scheduleChange
import dev.alllexey.itmowidgets.feature.home.FakeHomeCardPreferences
import dev.alllexey.itmowidgets.feature.home.FakeHomeHintStore
import dev.alllexey.itmowidgets.feature.home.homeScheduleCard
import dev.alllexey.itmowidgets.testkit.FakeClock
import dev.alllexey.itmowidgets.testkit.TestMainDispatcher
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest

class HomeViewModelTest {
    private val main = TestMainDispatcher()
    private val schedule = FakeHomeCardSource(homeScheduleCard())
    private val hints = FakeHomeCardSource(HomeCard.Hint(HomeHint.WIDGETS))
    private val sport = FakeHomeCardSource(HomeCard.Sport(null, emptyList()))
    private val preferences = FakeHomeCardPreferences()
    private val hintStore = FakeHomeHintStore()
    private val clock = FakeClock(Instant.fromEpochMilliseconds(1_000_000))

    @BeforeTest
    fun setUp() = main.install()

    @AfterTest
    fun tearDown() = main.reset()

    private fun model(vararg sources: FakeHomeCardSource = arrayOf(hints, sport, schedule)) =
        HomeViewModel(sources.toList(), preferences, hintStore, clock)

    private fun TestScope.subscribe(vm: HomeViewModel): Job =
        vm.uiState.onEach { }.launchIn(backgroundScope)

    private fun HomeViewModel.content() = uiState.value as HomeUiState.Content

    @Test
    fun cardsSortByKindWhateverTheSourceOrder() = runTest(main.dispatcher) {
        val vm = model()
        subscribe(vm)
        advanceUntilIdle()

        assertEquals(
            listOf(HomeCardKind.SCHEDULE, HomeCardKind.SPORT, HomeCardKind.HINT_WIDGETS),
            vm.content().cards.map { it.kind }
        )
    }

    @Test
    fun stateIsLoadingUntilEverySourceHasSpoken() = runTest(main.dispatcher) {
        val vm = model()
        assertEquals(HomeUiState.Loading, vm.uiState.value)

        subscribe(vm)
        advanceUntilIdle()
        assertTrue(vm.uiState.value is HomeUiState.Content)
    }

    @Test
    fun hiddenKindsDropOutAndReturnWithThePreference() = runTest(main.dispatcher) {
        val vm = model()
        subscribe(vm)
        preferences.hidden.value = setOf(HomeCardKind.SPORT)
        advanceUntilIdle()
        assertEquals(listOf(HomeCardKind.SCHEDULE, HomeCardKind.HINT_WIDGETS), vm.content().cards.map { it.kind })

        preferences.hidden.value = emptySet()
        advanceUntilIdle()
        assertEquals(3, vm.content().cards.size)
    }

    @Test
    fun scheduleChangesSitRightAfterTheScheduleAndHideByTheirOwnKind() = runTest(main.dispatcher) {
        val changes = FakeHomeCardSource(HomeCard.ScheduleChanges(unread = 2, latest = scheduleChange()))
        val vm = model(hints, sport, changes, schedule)
        subscribe(vm)
        advanceUntilIdle()
        assertEquals(
            listOf(HomeCardKind.SCHEDULE, HomeCardKind.SCHEDULE_CHANGES, HomeCardKind.SPORT, HomeCardKind.HINT_WIDGETS),
            vm.content().cards.map { it.kind }
        )

        preferences.hidden.value = setOf(HomeCardKind.SCHEDULE_CHANGES)
        advanceUntilIdle()
        assertEquals(
            listOf(HomeCardKind.SCHEDULE, HomeCardKind.SPORT, HomeCardKind.HINT_WIDGETS),
            vm.content().cards.map { it.kind }
        )
    }

    @Test
    fun newMarksSitRightAfterScheduleChangesAndHideByTheirOwnKind() = runTest(main.dispatcher) {
        val changes = FakeHomeCardSource(HomeCard.ScheduleChanges(unread = 2, latest = scheduleChange()))
        val marks = FakeHomeCardSource(HomeCard.Marks(listOf("Тестовый предмет")))
        val vm = model(marks, hints, sport, changes, schedule)
        subscribe(vm)
        advanceUntilIdle()
        assertEquals(
            listOf(
                HomeCardKind.SCHEDULE, HomeCardKind.SCHEDULE_CHANGES, HomeCardKind.MARKS, HomeCardKind.SPORT,
                HomeCardKind.HINT_WIDGETS
            ),
            vm.content().cards.map { it.kind }
        )

        preferences.hidden.value = setOf(HomeCardKind.MARKS)
        advanceUntilIdle()
        assertEquals(
            listOf(HomeCardKind.SCHEDULE, HomeCardKind.SCHEDULE_CHANGES, HomeCardKind.SPORT, HomeCardKind.HINT_WIDGETS),
            vm.content().cards.map { it.kind }
        )
    }

    @Test
    fun dismissingNewMarksReachesEverySource() = runTest(main.dispatcher) {
        val marks = FakeHomeCardSource(HomeCard.Marks(listOf("Тестовый предмет")))
        val vm = model(marks, hints, sport, schedule)

        vm.dismissCard(HomeCardKind.MARKS)
        advanceUntilIdle()

        assertEquals(
            List(4) { listOf(HomeCardKind.MARKS) },
            listOf(marks.dismissed, hints.dismissed, sport.dismissed, schedule.dismissed)
        )
    }

    @Test
    fun dismissingACardReachesEverySource() = runTest(main.dispatcher) {
        val vm = model()

        vm.dismissCard(HomeCardKind.SCHEDULE_CHANGES)
        advanceUntilIdle()

        assertEquals(
            listOf(listOf(HomeCardKind.SCHEDULE_CHANGES)).let { it + it + it },
            listOf(hints.dismissed, sport.dismissed, schedule.dismissed)
        )
    }

    @Test
    fun anEmptyFeedIsContentNotLoading() = runTest(main.dispatcher) {
        schedule.cards.value = emptyList()
        hints.cards.value = emptyList()
        sport.cards.value = emptyList()
        val vm = model()
        subscribe(vm)
        advanceUntilIdle()

        assertEquals(HomeUiState.Content(emptyList(), refreshing = false), vm.uiState.value)
    }

    @Test
    fun refreshAsksEverySourceAndReportsOneFailureForTwo() = runTest(main.dispatcher) {
        schedule.refreshResult = AppResult.Failure(AppError.Network)
        sport.refreshResult = AppResult.Failure(AppError.Unauthorized)
        val vm = model()
        subscribe(vm)
        val events = mutableListOf<HomeEvent>()
        vm.events.onEach(events::add).launchIn(backgroundScope)

        vm.refresh(RefreshMode.Pull)
        advanceUntilIdle()

        assertEquals(listOf(1, 1, 1), listOf(schedule.refreshes, sport.refreshes, hints.refreshes))
        assertEquals(1, events.size)
        assertTrue(events.single() is HomeEvent.RefreshFailed)
        assertFalse(vm.content().refreshing)
    }

    @Test
    fun aFailureReportedWhileNobodyCollectsReachesTheNextCollector() = runTest(main.dispatcher) {
        schedule.refreshResult = AppResult.Failure(AppError.Network)
        val vm = model()
        subscribe(vm)

        vm.refresh(RefreshMode.Pull)
        advanceUntilIdle()

        assertEquals(HomeEvent.RefreshFailed(AppError.Network), vm.events.first())
    }

    @Test
    fun theFirstLoadAndAStaleResumeRefreshSilently() = runTest(main.dispatcher) {
        val gate = CompletableDeferred<AppResult<Unit>>()
        schedule.pendingRefresh = gate
        val vm = model()
        subscribe(vm)
        vm.ensureDataLoaded()
        advanceUntilIdle()
        assertFalse(vm.content().refreshing)
        assertEquals(1, schedule.refreshes)
        gate.complete(AppResult.Success(Unit))
        advanceUntilIdle()
        assertFalse(vm.content().refreshing)
    }

    @Test
    fun onlyPullShowsTheIndicator() = runTest(main.dispatcher) {
        val vm = model()
        subscribe(vm)
        advanceUntilIdle()

        val shown = listOf(RefreshMode.Silent, RefreshMode.Pull, RefreshMode.Force).associateWith { mode ->
            val gate = CompletableDeferred<AppResult<Unit>>()
            schedule.pendingRefresh = gate
            vm.refresh(mode)
            advanceUntilIdle()
            val refreshing = vm.content().refreshing
            gate.complete(AppResult.Success(Unit))
            advanceUntilIdle()
            assertFalse(vm.content().refreshing)
            refreshing
        }

        assertEquals(mapOf(RefreshMode.Silent to false, RefreshMode.Pull to true, RefreshMode.Force to false), shown)
        assertEquals(3, schedule.refreshes)
    }

    @Test
    fun refreshingShowsWhileSourcesAnswerAndASecondCallWaits() = runTest(main.dispatcher) {
        val gate = CompletableDeferred<AppResult<Unit>>()
        schedule.pendingRefresh = gate
        val vm = model()
        subscribe(vm)

        vm.refresh(RefreshMode.Pull)
        advanceUntilIdle()
        assertTrue(vm.content().refreshing)
        vm.refresh(RefreshMode.Pull)
        assertEquals(1, schedule.refreshes)

        gate.complete(AppResult.Success(Unit))
        advanceUntilIdle()
        assertFalse(vm.content().refreshing)
    }

    @Test
    fun ensureDataLoadedRefreshesOnlyOnce() = runTest(main.dispatcher) {
        val vm = model()
        subscribe(vm)

        vm.ensureDataLoaded()
        advanceUntilIdle()
        vm.ensureDataLoaded()
        advanceUntilIdle()

        assertEquals(1, schedule.refreshes)
    }

    @Test
    fun returningToTheScreenRevalidatesAlwaysAndRefreshesOnlyWhenStale() = runTest(main.dispatcher) {
        val vm = model()
        subscribe(vm)
        vm.ensureDataLoaded()
        advanceUntilIdle()

        clock.advanceBy(60_000.milliseconds)
        vm.onScreenResumed()
        advanceUntilIdle()
        assertEquals(1, schedule.revalidations)
        assertEquals(1, schedule.refreshes)

        clock.advanceBy((5 * 60_000).milliseconds)
        vm.onScreenResumed()
        advanceUntilIdle()
        assertEquals(2, schedule.revalidations)
        assertEquals(2, schedule.refreshes)
    }

    @Test
    fun dismissingAHintWritesToTheStore() = runTest(main.dispatcher) {
        val vm = model()

        vm.dismissHint(HomeHint.WIDGETS)
        advanceUntilIdle()

        assertEquals(setOf(HomeHint.WIDGETS), hintStore.dismissed.value)
        assertNull(vm.uiState.value as? HomeUiState.Content)
    }
}
