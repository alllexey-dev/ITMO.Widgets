package dev.alllexey.itmowidgets.feature.schedule.presentation

import androidx.lifecycle.SavedStateHandle
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.testing.MainDispatcherRule
import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import dev.alllexey.itmowidgets.feature.schedule.domain.ScheduleRepository
import dev.alllexey.itmowidgets.feature.schedule.domain.model.DaySchedule
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class ScheduleViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val timeProvider = FixedAcademicTimeProvider(
        LocalDate.of(2026, 2, 16)
    )

    @Test
    fun `renders empty state after successful initial refresh`() =
        runTest(mainDispatcherRule.dispatcher) {
            val viewModel = createViewModel(FakeScheduleRepository())

            viewModel.ensureDataLoaded()
            advanceUntilIdle()

            assertEquals(ScheduleUiState.Empty(null), viewModel.uiState.value)
        }

    @Test
    fun `renders cached content after successful refresh`() =
        runTest(mainDispatcherRule.dispatcher) {
            val repository = FakeScheduleRepository().apply {
                schedules.value = listOf(daySchedule())
            }
            val viewModel = createViewModel(repository)

            viewModel.ensureDataLoaded()
            advanceUntilIdle()

            val state = viewModel.uiState.value as ScheduleUiState.Content
            assertEquals(repository.schedules.value, state.schedule)
            assertFalse(state.loadingMore)
        }

    @Test
    fun `renders typed error when initial refresh fails`() =
        runTest(mainDispatcherRule.dispatcher) {
            val repository = FakeScheduleRepository().apply {
                refreshResult = AppResult.Failure(AppError.Network)
            }
            val viewModel = createViewModel(repository)

            viewModel.ensureDataLoaded()
            advanceUntilIdle()

            assertEquals(
                ScheduleUiState.Error(AppError.Network, selectedUser = null),
                viewModel.uiState.value
            )
        }

    @Test
    fun `keeps cached content and emits event when refresh fails`() =
        runTest(mainDispatcherRule.dispatcher) {
            val repository = FakeScheduleRepository().apply {
                schedules.value = listOf(daySchedule())
            }
            val viewModel = createViewModel(repository)
            viewModel.ensureDataLoaded()
            advanceUntilIdle()

            val refresh = CompletableDeferred<AppResult<Unit>>()
            repository.refreshHandler = { refresh.await() }
            val event = async { viewModel.events.first() }
            viewModel.loadInitialSchedule(forceRefresh = true)
            runCurrent()

            val loadingState = viewModel.uiState.value as ScheduleUiState.Content
            assertEquals(repository.schedules.value, loadingState.schedule)
            assertTrue(loadingState.loadingMore)
            assertFalse(repository.cachesCleared)

            refresh.complete(AppResult.Failure(AppError.Forbidden))
            advanceUntilIdle()

            assertEquals(
                ScheduleEvent.ShowError(AppError.Forbidden),
                event.await()
            )
            val state = viewModel.uiState.value as ScheduleUiState.Content
            assertEquals(repository.schedules.value, state.schedule)
            assertFalse(state.loadingMore)
            assertFalse(repository.cachesCleared)
        }

    @Test
    fun `forced refresh keeps cached content until successful replacement`() =
        runTest(mainDispatcherRule.dispatcher) {
            val cached = listOf(daySchedule())
            val repository = FakeScheduleRepository().apply {
                schedules.value = cached
            }
            val viewModel = createViewModel(repository)
            viewModel.ensureDataLoaded()
            advanceUntilIdle()

            val refresh = CompletableDeferred<AppResult<Unit>>()
            val updated = listOf(daySchedule().copy(note = "Обновлено"))
            repository.refreshHandler = {
                refresh.await().also { result ->
                    if (result is AppResult.Success) repository.schedules.value = updated
                }
            }
            viewModel.loadInitialSchedule(forceRefresh = true)
            runCurrent()

            assertEquals(ScheduleUiState.Content(cached, true, null), viewModel.uiState.value)
            assertEquals(cached, repository.schedules.value)
            assertFalse(repository.cachesCleared)

            refresh.complete(AppResult.Success(Unit))
            advanceUntilIdle()

            assertEquals(ScheduleUiState.Content(updated, false, null), viewModel.uiState.value)
            assertFalse(repository.cachesCleared)
        }

    @Test
    fun `forced refresh requests the entire loaded range without restarting observation`() =
        runTest(mainDispatcherRule.dispatcher) {
            val loadedDays = daysIncludingNextPage()
            val repository = FakeScheduleRepository().apply {
                schedules.value = loadedDays
            }
            val viewModel = createViewModel(repository)
            viewModel.ensureDataLoaded()
            advanceUntilIdle()
            viewModel.fetchNextDays()
            advanceUntilIdle()
            val observations = repository.observedRanges.toList()

            viewModel.loadInitialSchedule(forceRefresh = true)
            advanceUntilIdle()

            assertEquals(
                ScheduleRequest(null, timeProvider.today().minusDays(1), timeProvider.today().plusDays(28)),
                repository.refreshRequests.last()
            )
            assertEquals(observations, repository.observedRanges)
            assertEquals(ScheduleUiState.Content(loadedDays, false, null), viewModel.uiState.value)
            assertFalse(repository.cachesCleared)
        }

    @Test
    fun `initial reload resets the paginated range`() =
        runTest(mainDispatcherRule.dispatcher) {
            val repository = FakeScheduleRepository().apply {
                schedules.value = daysIncludingNextPage()
            }
            val viewModel = createViewModel(repository)
            viewModel.ensureDataLoaded()
            advanceUntilIdle()
            viewModel.fetchNextDays()
            advanceUntilIdle()

            viewModel.loadInitialSchedule()
            advanceUntilIdle()

            assertEquals(initialRequest(), repository.refreshRequests.last())
            assertEquals(initialRequest(), repository.observedRanges.last())
            assertEquals(16, (viewModel.uiState.value as ScheduleUiState.Content).schedule.size)
        }

    @Test
    fun `switching user resets the range and does not retain another users content`() =
        runTest(mainDispatcherRule.dispatcher) {
            val user = SelectedUser(123456, "Иван Иванов", null)
            val ownDays = daysIncludingNextPage()
            val friendDays = ownDays.map { it.copy(note = "Расписание друга") }
            val repository = FakeScheduleRepository().apply {
                schedules.value = ownDays
                schedulesFor(user.isu).value = friendDays
            }
            val viewModel = createViewModel(repository)
            viewModel.ensureDataLoaded()
            advanceUntilIdle()
            viewModel.fetchNextDays()
            advanceUntilIdle()

            viewModel.setSelectedUser(user)
            viewModel.loadInitialSchedule(forceRefresh = true)

            assertEquals(ScheduleUiState.Loading(user), viewModel.uiState.value)
            advanceUntilIdle()

            assertEquals(initialRequest(user.isu), repository.refreshRequests.last())
            assertEquals(initialRequest(user.isu), repository.observedRanges.last())
            assertEquals(
                ScheduleUiState.Content(friendDays.take(16), false, user),
                viewModel.uiState.value
            )
            assertEquals(ownDays, repository.schedules.value)
            assertFalse(repository.cachesCleared)
        }

    @Test
    fun `canceled refresh cannot finish loading a newly selected user`() =
        runTest(mainDispatcherRule.dispatcher) {
            val user = SelectedUser(123456, "Иван Иванов", null)
            val repository = FakeScheduleRepository().apply {
                schedules.value = listOf(daySchedule())
            }
            val viewModel = createViewModel(repository)
            viewModel.ensureDataLoaded()
            advanceUntilIdle()

            val oldRefresh = CompletableDeferred<AppResult<Unit>>()
            val newRefresh = CompletableDeferred<AppResult<Unit>>()
            repository.refreshHandler = { request ->
                if (request.userIsu == null) {
                    withContext(NonCancellable) { oldRefresh.await() }
                } else {
                    newRefresh.await()
                }
            }
            viewModel.loadInitialSchedule(forceRefresh = true)
            runCurrent()
            viewModel.setSelectedUser(user)
            viewModel.loadInitialSchedule()
            runCurrent()

            oldRefresh.complete(AppResult.Failure(AppError.Forbidden))
            runCurrent()

            assertEquals(ScheduleUiState.Loading(user), viewModel.uiState.value)
            assertEquals(initialRequest(user.isu), repository.refreshRequests.last())

            newRefresh.complete(AppResult.Success(Unit))
            advanceUntilIdle()

            assertEquals(ScheduleUiState.Empty(user), viewModel.uiState.value)
        }

    @Test
    fun `restores selected user through saved state`() {
        val savedStateHandle = SavedStateHandle()
        val firstViewModel = createViewModel(
            repository = FakeScheduleRepository(),
            savedStateHandle = savedStateHandle
        )
        val user = SelectedUser(
            isu = 123456,
            name = "Иван Иванов",
            avatar = null
        )

        firstViewModel.setSelectedUser(user)
        val restoredViewModel = createViewModel(
            repository = FakeScheduleRepository(),
            savedStateHandle = savedStateHandle
        )

        assertEquals(
            ScheduleUiState.Loading(selectedUser = user),
            restoredViewModel.uiState.value
        )
    }

    private fun createViewModel(
        repository: ScheduleRepository,
        savedStateHandle: SavedStateHandle = SavedStateHandle()
    ): ScheduleViewModel {
        return ScheduleViewModel(
            repository = repository,
            timeProvider = timeProvider,
            savedStateHandle = savedStateHandle
        )
    }

    private fun initialRequest(userIsu: Int? = null) = ScheduleRequest(
        userIsu,
        timeProvider.today().minusDays(1),
        timeProvider.today().plusDays(14)
    )

    private fun daysIncludingNextPage(): List<DaySchedule> = (-1L..28L).map { offset ->
        daySchedule(timeProvider.today().plusDays(offset))
    }

    private fun daySchedule(date: LocalDate = timeProvider.today()): DaySchedule {
        return DaySchedule(
            dayNumber = 1,
            weekNumber = 1,
            date = date,
            note = null,
            lessons = emptyList()
        )
    }

    private data class ScheduleRequest(
        val userIsu: Int?,
        val startDate: LocalDate,
        val endDate: LocalDate
    )

    private class FakeScheduleRepository : ScheduleRepository {
        private val schedulesByUser = mutableMapOf<Int?, MutableStateFlow<List<DaySchedule>>>()
        val schedules get() = schedulesFor(null)
        val observedRanges = mutableListOf<ScheduleRequest>()
        val refreshRequests = mutableListOf<ScheduleRequest>()
        var refreshResult: AppResult<Unit> = AppResult.Success(Unit)
        var refreshHandler: suspend (ScheduleRequest) -> AppResult<Unit> = { refreshResult }
        var cachesCleared = false

        fun schedulesFor(userIsu: Int?): MutableStateFlow<List<DaySchedule>> =
            schedulesByUser.getOrPut(userIsu) { MutableStateFlow(emptyList()) }

        override fun observeScheduleForRange(
            userIsu: Int?,
            startDate: LocalDate,
            endDate: LocalDate
        ): Flow<List<DaySchedule>> {
            observedRanges += ScheduleRequest(userIsu, startDate, endDate)
            return schedulesFor(userIsu).map { days ->
                days.filter { !it.date.isBefore(startDate) && !it.date.isAfter(endDate) }
            }
        }

        override suspend fun refreshSchedule(
            userIsu: Int?,
            startDate: LocalDate,
            endDate: LocalDate
        ): AppResult<Unit> {
            val request = ScheduleRequest(userIsu, startDate, endDate)
            refreshRequests += request
            return refreshHandler(request)
        }

        override suspend fun clearCaches() {
            cachesCleared = true
            schedulesByUser.values.forEach { it.value = emptyList() }
        }
    }

    private class FixedAcademicTimeProvider(
        private val date: LocalDate
    ) : AcademicTimeProvider {
        override val zoneId: ZoneId = ZoneId.of("Europe/Moscow")

        override fun today(): LocalDate = date

        override fun now(): OffsetDateTime = date
            .atStartOfDay()
            .atZone(zoneId)
            .toOffsetDateTime()
    }
}
