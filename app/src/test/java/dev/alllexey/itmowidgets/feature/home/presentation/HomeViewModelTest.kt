package dev.alllexey.itmowidgets.feature.home.presentation

import dev.alllexey.itmowidgets.core.home.HomeCard
import dev.alllexey.itmowidgets.core.home.HomeCardKind
import dev.alllexey.itmowidgets.core.home.HomeHint
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.testing.MainDispatcherRule
import dev.alllexey.itmowidgets.feature.home.FakeHomeCardPreferences
import dev.alllexey.itmowidgets.feature.home.FakeHomeCardSource
import dev.alllexey.itmowidgets.feature.home.FakeHomeHintStore
import dev.alllexey.itmowidgets.feature.home.homeScheduleCard
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    private val schedule = FakeHomeCardSource(homeScheduleCard())
    private val hints = FakeHomeCardSource(HomeCard.Hint(HomeHint.WIDGETS))
    private val sport = FakeHomeCardSource(HomeCard.Sport(null, emptyList()))
    private val preferences = FakeHomeCardPreferences()
    private val hintStore = FakeHomeHintStore()
    private var nowMillis = 1_000_000L
    private val clock = object : Clock() {
        override fun getZone() = ZoneOffset.UTC
        override fun withZone(zone: java.time.ZoneId) = this
        override fun instant(): Instant = Instant.ofEpochMilli(nowMillis)
    }

    private fun model(vararg sources: FakeHomeCardSource = arrayOf(hints, sport, schedule)) =
        HomeViewModel(sources.toSet(), preferences, hintStore, clock)

    private fun kotlinx.coroutines.test.TestScope.subscribe(vm: HomeViewModel): Job =
        vm.uiState.onEach { }.launchIn(backgroundScope)

    private fun HomeViewModel.content() = uiState.value as HomeUiState.Content

    @Test
    fun `cards sort by kind whatever the source order`() = runTest {
        val vm = model()
        subscribe(vm)
        advanceUntilIdle()

        assertEquals(
            listOf(HomeCardKind.SCHEDULE, HomeCardKind.SPORT, HomeCardKind.HINT_WIDGETS),
            vm.content().cards.map { it.kind }
        )
    }

    @Test
    fun `state is loading until every source has spoken`() = runTest {
        val vm = model()
        assertEquals(HomeUiState.Loading, vm.uiState.value)

        subscribe(vm)
        advanceUntilIdle()
        assertTrue(vm.uiState.value is HomeUiState.Content)
    }

    @Test
    fun `hidden kinds drop out and return with the preference`() = runTest {
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
    fun `an empty feed is content not loading`() = runTest {
        schedule.cards.value = emptyList()
        hints.cards.value = emptyList()
        sport.cards.value = emptyList()
        val vm = model()
        subscribe(vm)
        advanceUntilIdle()

        assertEquals(HomeUiState.Content(emptyList(), refreshing = false), vm.uiState.value)
    }

    @Test
    fun `refresh asks every source and reports one failure for two`() = runTest {
        schedule.refreshResult = AppResult.Failure(AppError.Network)
        sport.refreshResult = AppResult.Failure(AppError.Unauthorized)
        val vm = model()
        subscribe(vm)
        val events = mutableListOf<HomeEvent>()
        vm.events.onEach(events::add).launchIn(backgroundScope)

        vm.refresh()
        advanceUntilIdle()

        assertEquals(listOf(1, 1, 1), listOf(schedule.refreshes, sport.refreshes, hints.refreshes))
        assertEquals(1, events.size)
        assertTrue(events.single() is HomeEvent.RefreshFailed)
        assertFalse(vm.content().refreshing)
    }

    @Test
    fun `refreshing shows while sources answer and a second call waits`() = runTest {
        val gate = CompletableDeferred<AppResult<Unit>>()
        schedule.pendingRefresh = gate
        val vm = model()
        subscribe(vm)

        vm.refresh()
        advanceUntilIdle()
        assertTrue(vm.content().refreshing)
        vm.refresh()
        assertEquals(1, schedule.refreshes)

        gate.complete(AppResult.Success(Unit))
        advanceUntilIdle()
        assertFalse(vm.content().refreshing)
    }

    @Test
    fun `ensureDataLoaded refreshes only once`() = runTest {
        val vm = model()
        subscribe(vm)

        vm.ensureDataLoaded()
        advanceUntilIdle()
        vm.ensureDataLoaded()
        advanceUntilIdle()

        assertEquals(1, schedule.refreshes)
    }

    @Test
    fun `returning to the screen revalidates always and refreshes only when stale`() = runTest {
        val vm = model()
        subscribe(vm)
        vm.ensureDataLoaded()
        advanceUntilIdle()

        nowMillis += 60_000
        vm.onScreenResumed()
        advanceUntilIdle()
        assertEquals(1, schedule.revalidations)
        assertEquals(1, schedule.refreshes)

        nowMillis += 5 * 60_000
        vm.onScreenResumed()
        advanceUntilIdle()
        assertEquals(2, schedule.revalidations)
        assertEquals(2, schedule.refreshes)
    }

    @Test
    fun `dismissing a hint writes to the store`() = runTest {
        val vm = model()

        vm.dismissHint(HomeHint.WIDGETS)
        advanceUntilIdle()

        assertEquals(setOf(HomeHint.WIDGETS), hintStore.dismissed.value)
        assertNull(vm.uiState.value as? HomeUiState.Content)
    }
}
