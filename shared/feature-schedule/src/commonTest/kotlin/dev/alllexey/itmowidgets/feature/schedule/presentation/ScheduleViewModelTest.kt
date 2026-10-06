package dev.alllexey.itmowidgets.feature.schedule.presentation

import androidx.lifecycle.SavedStateHandle
import dev.alllexey.itmowidgets.core.testing.FakeCalendarSync
import dev.alllexey.itmowidgets.core.presentation.RefreshMode
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.schedule.ScheduleChangeKind
import dev.alllexey.itmowidgets.core.testing.FakePendingSportBookingsRepository
import dev.alllexey.itmowidgets.core.testing.FakeSchedulePreferencesRepository
import dev.alllexey.itmowidgets.core.testing.FixedAcademicTime
import dev.alllexey.itmowidgets.core.testing.MainDispatcherRule
import dev.alllexey.itmowidgets.core.testing.scheduleChange
import dev.alllexey.itmowidgets.core.testing.slot
import dev.alllexey.itmowidgets.feature.schedule.FakeScheduleRepository
import dev.alllexey.itmowidgets.feature.schedule.ScheduleRequest
import dev.alllexey.itmowidgets.feature.schedule.domain.ScheduleRepository
import dev.alllexey.itmowidgets.feature.schedule.domain.model.DaySchedule
import dev.alllexey.itmowidgets.feature.schedule.FakeScheduleChangesRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.flowOf
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
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toKotlinLocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class ScheduleViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val timeProvider = FixedAcademicTime(
        LocalDate(2026, 2, 16)
    )

    @Test
    fun `renders empty state after successful initial refresh`() =
        runTest(mainDispatcherRule.dispatcher) {
            val viewModel = createViewModel(FakeScheduleRepository())

            viewModel.refresh(RefreshMode.Silent)
            advanceUntilIdle()

            assertEquals(ScheduleUiState.Empty(null), viewModel.uiState.value)
        }

    @Test
    fun `renders cached content after successful refresh`() =
        runTest(mainDispatcherRule.dispatcher) {
            val repository = FakeScheduleRepository().apply {
                days.value = listOf(daySchedule())
            }
            val viewModel = createViewModel(repository)

            viewModel.refresh(RefreshMode.Silent)
            advanceUntilIdle()

            val state = viewModel.uiState.value as ScheduleUiState.Content
            assertEquals(repository.days.value, state.schedule)
            assertFalse(state.loadingMore)
        }

    @Test
    fun `the first state is content from memory and the entry refresh shows no indicator`() =
        runTest(mainDispatcherRule.dispatcher) {
            val repository = FakeScheduleRepository().apply {
                days.value = listOf(daySchedule())
                memorySnapshot = true
                refreshHandler = { CompletableDeferred<AppResult<Unit>>().await() }
            }
            val viewModel = createViewModel(repository)

            viewModel.refresh(RefreshMode.Silent)

            val state = viewModel.uiState.value as ScheduleUiState.Content
            assertEquals(repository.days.value, state.schedule)
            assertFalse(state.loadingMore)

            viewModel.refresh(RefreshMode.Pull)
            assertTrue((viewModel.uiState.value as ScheduleUiState.Content).loadingMore)
        }

    @Test
    fun `without a memory snapshot the first state is loading until the cache answers`() =
        runTest(mainDispatcherRule.dispatcher) {
            val repository = FakeScheduleRepository().apply { days.value = listOf(daySchedule()) }
            val viewModel = createViewModel(repository)

            viewModel.refresh(RefreshMode.Silent)
            assertEquals(ScheduleUiState.Loading(null), viewModel.uiState.value)
            runCurrent()
            assertTrue(viewModel.uiState.value is ScheduleUiState.Content)
        }

    @Test
    fun `renders typed error when initial refresh fails`() =
        runTest(mainDispatcherRule.dispatcher) {
            val repository = FakeScheduleRepository().apply {
                refreshResult = AppResult.Failure(AppError.Network)
            }
            val viewModel = createViewModel(repository)

            viewModel.refresh(RefreshMode.Silent)
            advanceUntilIdle()

            assertEquals(
                ScheduleUiState.Error(AppError.Network, selectedUser = null),
                viewModel.uiState.value
            )
        }

    @Test
    fun `a successful pull on the own schedule asks for a calendar sync, a failed or friend's one does not`() =
        runTest(mainDispatcherRule.dispatcher) {
            val repository = FakeScheduleRepository().apply {
                days.value = listOf(daySchedule())
                schedulesFor(123456).value = listOf(daySchedule())
            }
            val calendarSync = FakeCalendarSync()
            val viewModel = createViewModel(repository, calendarSync = calendarSync)
            viewModel.refresh(RefreshMode.Silent)
            advanceUntilIdle()
            assertEquals(0, calendarSync.syncRequests)

            viewModel.refresh(RefreshMode.Pull)
            advanceUntilIdle()
            assertEquals(1, calendarSync.syncRequests)

            repository.refreshResult = AppResult.Failure(AppError.Network)
            viewModel.refresh(RefreshMode.Pull)
            advanceUntilIdle()
            repository.refreshResult = AppResult.Success(Unit)
            viewModel.setSelectedUser(SelectedUser(123456, "Иван Иванов", null))
            viewModel.refresh(RefreshMode.Force)
            advanceUntilIdle()
            viewModel.refresh(RefreshMode.Pull)
            advanceUntilIdle()
            assertEquals(1, calendarSync.syncRequests)

            viewModel.setSelectedUser(null)
            viewModel.refresh(RefreshMode.Force)
            advanceUntilIdle()
            assertEquals(1, calendarSync.syncRequests)
            viewModel.refresh(RefreshMode.Force)
            advanceUntilIdle()
            assertEquals(2, calendarSync.syncRequests)
        }

    @Test
    fun `a silent refresh while the range is observed asks for nothing`() =
        runTest(mainDispatcherRule.dispatcher) {
            val repository = FakeScheduleRepository().apply { days.value = listOf(daySchedule()) }
            val viewModel = createViewModel(repository)
            viewModel.refresh(RefreshMode.Silent)
            advanceUntilIdle()
            val state = viewModel.uiState.value

            viewModel.refresh(RefreshMode.Silent)
            advanceUntilIdle()

            assertEquals(listOf(initialRequest()), repository.refreshed)
            assertEquals(listOf(initialRequest()), repository.observed)
            assertEquals(state, viewModel.uiState.value)
        }

    @Test
    fun `a forced retry after a failed first load shows progress and then the schedule`() =
        runTest(mainDispatcherRule.dispatcher) {
            val repository = FakeScheduleRepository().apply { refreshResult = AppResult.Failure(AppError.Network) }
            val viewModel = createViewModel(repository)
            viewModel.refresh(RefreshMode.Silent)
            advanceUntilIdle()
            assertEquals(ScheduleUiState.Error(AppError.Network, null), viewModel.uiState.value)

            val retry = CompletableDeferred<AppResult<Unit>>()
            repository.refreshHandler = { retry.await().also { repository.days.value = listOf(daySchedule()) } }
            viewModel.refresh(RefreshMode.Force)
            runCurrent()
            assertEquals(ScheduleUiState.Loading(null), viewModel.uiState.value)

            retry.complete(AppResult.Success(Unit))
            advanceUntilIdle()
            assertEquals(ScheduleUiState.Content(listOf(daySchedule()), false, null), viewModel.uiState.value)
            assertEquals(listOf(initialRequest(), initialRequest()), repository.refreshed)
        }

    @Test
    fun `a pull during the silent entry refresh replaces it and shows the indicator`() =
        runTest(mainDispatcherRule.dispatcher) {
            val entry = CompletableDeferred<AppResult<Unit>>()
            val pull = CompletableDeferred<AppResult<Unit>>()
            val repository = FakeScheduleRepository().apply {
                days.value = listOf(daySchedule())
                memorySnapshot = true
                refreshHandler = { if (refreshed.size == 1) entry.await() else pull.await() }
            }
            val viewModel = createViewModel(repository)
            viewModel.refresh(RefreshMode.Silent)
            runCurrent()
            assertFalse((viewModel.uiState.value as ScheduleUiState.Content).loadingMore)

            viewModel.refresh(RefreshMode.Pull)
            runCurrent()
            assertTrue((viewModel.uiState.value as ScheduleUiState.Content).loadingMore)
            entry.complete(AppResult.Failure(AppError.Network))
            runCurrent()
            assertTrue((viewModel.uiState.value as ScheduleUiState.Content).loadingMore)

            pull.complete(AppResult.Success(Unit))
            advanceUntilIdle()
            assertFalse((viewModel.uiState.value as ScheduleUiState.Content).loadingMore)
            assertEquals(2, repository.refreshed.size)
        }

    @Test
    fun `a refresh error sent while nobody collects reaches the next collector once`() =
        runTest(mainDispatcherRule.dispatcher) {
            val repository = FakeScheduleRepository().apply { days.value = listOf(daySchedule()) }
            val viewModel = createViewModel(repository)
            viewModel.refresh(RefreshMode.Silent)
            advanceUntilIdle()

            repository.refreshResult = AppResult.Failure(AppError.Network)
            viewModel.refresh(RefreshMode.Pull)
            advanceUntilIdle()

            assertEquals(ScheduleEvent.ShowError(AppError.Network), viewModel.events.first())
            val again = async { viewModel.events.first() }
            runCurrent()
            assertFalse(again.isCompleted)
            again.cancel()
        }

    @Test
    fun `keeps cached content and emits event when refresh fails`() =
        runTest(mainDispatcherRule.dispatcher) {
            val repository = FakeScheduleRepository().apply {
                days.value = listOf(daySchedule())
            }
            val viewModel = createViewModel(repository)
            viewModel.refresh(RefreshMode.Silent)
            advanceUntilIdle()

            val refresh = CompletableDeferred<AppResult<Unit>>()
            repository.refreshHandler = { refresh.await() }
            val event = async { viewModel.events.first() }
            viewModel.refresh(RefreshMode.Pull)
            runCurrent()

            val loadingState = viewModel.uiState.value as ScheduleUiState.Content
            assertEquals(repository.days.value, loadingState.schedule)
            assertTrue(loadingState.loadingMore)
            assertEquals(0, repository.clears)

            refresh.complete(AppResult.Failure(AppError.Forbidden))
            advanceUntilIdle()

            assertEquals(
                ScheduleEvent.ShowError(AppError.Forbidden),
                event.await()
            )
            val state = viewModel.uiState.value as ScheduleUiState.Content
            assertEquals(repository.days.value, state.schedule)
            assertFalse(state.loadingMore)
            assertEquals(0, repository.clears)
        }

    @Test
    fun `forced refresh keeps cached content until successful replacement`() =
        runTest(mainDispatcherRule.dispatcher) {
            val cached = listOf(daySchedule())
            val repository = FakeScheduleRepository().apply {
                days.value = cached
            }
            val viewModel = createViewModel(repository)
            viewModel.refresh(RefreshMode.Silent)
            advanceUntilIdle()

            val refresh = CompletableDeferred<AppResult<Unit>>()
            val updated = listOf(daySchedule().copy(note = "Обновлено"))
            repository.refreshHandler = {
                refresh.await().also { result ->
                    if (result is AppResult.Success) repository.days.value = updated
                }
            }
            viewModel.refresh(RefreshMode.Pull)
            runCurrent()

            assertEquals(ScheduleUiState.Content(cached, true, null), viewModel.uiState.value)
            assertEquals(cached, repository.days.value)
            assertEquals(0, repository.clears)

            refresh.complete(AppResult.Success(Unit))
            advanceUntilIdle()

            assertEquals(ScheduleUiState.Content(updated, false, null), viewModel.uiState.value)
            assertEquals(0, repository.clears)
        }

    @Test
    fun `foreign access denial immediately hides cached and late rows until a new authorized load`() =
        runTest(mainDispatcherRule.dispatcher) {
            val user = SelectedUser(123456, "Иван Иванов", null)
            val repository = FakeScheduleRepository().apply {
                schedulesFor(user.isu).value = listOf(daySchedule())
            }
            val viewModel = createViewModel(repository)
            viewModel.setSelectedUser(user)
            viewModel.refresh(RefreshMode.Force)
            advanceUntilIdle()
            assertTrue(viewModel.uiState.value is ScheduleUiState.Content)

            repository.refreshResult = AppResult.Failure(AppError.Forbidden)
            viewModel.refresh(RefreshMode.Pull)
            advanceUntilIdle()
            assertEquals(ScheduleUiState.Error(AppError.Forbidden, user), viewModel.uiState.value)
            repository.schedulesFor(user.isu).value = listOf(daySchedule().copy(note = "Late old cache"))
            advanceUntilIdle()
            viewModel.updateTimeState()
            assertEquals(ScheduleUiState.Error(AppError.Forbidden, user), viewModel.uiState.value)

            repository.refreshResult = AppResult.Success(Unit)
            viewModel.refresh(RefreshMode.Force)
            advanceUntilIdle()
            assertTrue(viewModel.uiState.value is ScheduleUiState.Content)
        }

    @Test
    fun `forced refresh requests the entire loaded range without restarting observation`() =
        runTest(mainDispatcherRule.dispatcher) {
            val loadedDays = daysIncludingNextPage()
            val repository = FakeScheduleRepository().apply {
                days.value = loadedDays
            }
            val viewModel = createViewModel(repository)
            viewModel.refresh(RefreshMode.Silent)
            advanceUntilIdle()
            viewModel.fetchNextDays()
            advanceUntilIdle()
            val observations = repository.observed.toList()

            viewModel.refresh(RefreshMode.Pull)
            advanceUntilIdle()

            assertEquals(
                ScheduleRequest(null, timeProvider.today().minus(1, DateTimeUnit.DAY), timeProvider.today().plus(28, DateTimeUnit.DAY)),
                repository.refreshed.last()
            )
            assertEquals(observations, repository.observed)
            assertEquals(ScheduleUiState.Content(loadedDays, false, null), viewModel.uiState.value)
            assertEquals(0, repository.clears)
        }

    @Test
    fun `selecting a user again resets the paginated range`() =
        runTest(mainDispatcherRule.dispatcher) {
            val repository = FakeScheduleRepository().apply {
                days.value = daysIncludingNextPage()
            }
            val viewModel = createViewModel(repository)
            viewModel.refresh(RefreshMode.Silent)
            advanceUntilIdle()
            viewModel.fetchNextDays()
            advanceUntilIdle()

            viewModel.setSelectedUser(null)
            viewModel.refresh(RefreshMode.Force)
            advanceUntilIdle()

            assertEquals(initialRequest(), repository.refreshed.last())
            assertEquals(initialRequest(), repository.observed.last())
            assertEquals(16, (viewModel.uiState.value as ScheduleUiState.Content).schedule.size)
        }

    @Test
    fun `switching user resets the range and does not retain another users content`() =
        runTest(mainDispatcherRule.dispatcher) {
            val user = SelectedUser(123456, "Иван Иванов", null)
            val ownDays = daysIncludingNextPage()
            val friendDays = ownDays.map { it.copy(note = "Расписание друга") }
            val repository = FakeScheduleRepository().apply {
                days.value = ownDays
                schedulesFor(user.isu).value = friendDays
            }
            val viewModel = createViewModel(repository)
            viewModel.refresh(RefreshMode.Silent)
            advanceUntilIdle()
            viewModel.fetchNextDays()
            advanceUntilIdle()

            viewModel.setSelectedUser(user)
            viewModel.refresh(RefreshMode.Pull)

            assertEquals(ScheduleUiState.Loading(user), viewModel.uiState.value)
            advanceUntilIdle()

            assertEquals(initialRequest(user.isu), repository.refreshed.last())
            assertEquals(initialRequest(user.isu), repository.observed.last())
            assertEquals(
                ScheduleUiState.Content(friendDays.take(16), false, user),
                viewModel.uiState.value
            )
            assertEquals(ownDays, repository.days.value)
            assertEquals(0, repository.clears)
        }

    @Test
    fun `canceled refresh cannot finish loading a newly selected user`() =
        runTest(mainDispatcherRule.dispatcher) {
            val user = SelectedUser(123456, "Иван Иванов", null)
            val repository = FakeScheduleRepository().apply {
                days.value = listOf(daySchedule())
            }
            val viewModel = createViewModel(repository)
            viewModel.refresh(RefreshMode.Silent)
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
            viewModel.refresh(RefreshMode.Pull)
            runCurrent()
            viewModel.setSelectedUser(user)
            viewModel.refresh(RefreshMode.Force)
            runCurrent()

            oldRefresh.complete(AppResult.Failure(AppError.Forbidden))
            runCurrent()

            assertEquals(ScheduleUiState.Loading(user), viewModel.uiState.value)
            assertEquals(initialRequest(user.isu), repository.refreshed.last())

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

    @Test
    fun `a change marks its lesson only on its own day`() =
        runTest(mainDispatcherRule.dispatcher) {
            val (tomorrow, later) = timeProvider.today().plus(1, DateTimeUnit.DAY) to timeProvider.today().plus(2, DateTimeUnit.DAY)
            val repository = FakeScheduleRepository().apply { days.value = listOf(daySchedule(tomorrow), daySchedule(later)) }
            val changes = FakeScheduleChangesRepository(
                scheduleChange(kind = ScheduleChangeKind.ADDED, after = slot(1, tomorrow))
            )
            val viewModel = createViewModel(repository, changesRepository = changes)

            viewModel.refresh(RefreshMode.Silent)
            advanceUntilIdle()

            assertEquals(mapOf(tomorrow to setOf(1L), later to emptySet()), viewModel.changedPairIdsByDay())
        }

    @Test
    fun `a cancelled lesson still in the cache is marked by its old slot`() =
        runTest(mainDispatcherRule.dispatcher) {
            val day = timeProvider.today().plus(2, DateTimeUnit.DAY)
            val repository = FakeScheduleRepository().apply { days.value = listOf(daySchedule(day)) }
            val changes = FakeScheduleChangesRepository(
                scheduleChange(kind = ScheduleChangeKind.CANCELLED, before = slot(2, day))
            )
            val viewModel = createViewModel(repository, changesRepository = changes)

            viewModel.refresh(RefreshMode.Silent)
            advanceUntilIdle()

            assertEquals(mapOf(day to setOf(2L)), viewModel.changedPairIdsByDay())
        }

    @Test
    fun `a friend's schedule with the same lesson shows the mark too`() =
        runTest(mainDispatcherRule.dispatcher) {
            val user = SelectedUser(123456, "Иван Иванов", null)
            val day = timeProvider.today().plus(1, DateTimeUnit.DAY)
            val repository = FakeScheduleRepository().apply { schedulesFor(user.isu).value = listOf(daySchedule(day)) }
            val changes = FakeScheduleChangesRepository(scheduleChange(before = slot(3, day), after = slot(3, day.plus(1, DateTimeUnit.DAY))))
            val viewModel = createViewModel(repository, changesRepository = changes)

            viewModel.setSelectedUser(user)
            viewModel.refresh(RefreshMode.Pull)
            advanceUntilIdle()

            assertEquals(user, (viewModel.uiState.value as ScheduleUiState.Content).selectedUser)
            assertEquals(mapOf(day to setOf(3L)), viewModel.changedPairIdsByDay())
        }

    @Test
    fun `a change leaving the store removes the mark without asking for the schedule again`() =
        runTest(mainDispatcherRule.dispatcher) {
            val day = timeProvider.today().plus(1, DateTimeUnit.DAY)
            val repository = FakeScheduleRepository().apply { days.value = listOf(daySchedule(day)) }
            val changes = FakeScheduleChangesRepository(scheduleChange(kind = ScheduleChangeKind.ADDED, after = slot(1, day)))
            val viewModel = createViewModel(repository, changesRepository = changes)
            viewModel.refresh(RefreshMode.Silent)
            advanceUntilIdle()
            val requests = repository.refreshed.size
            assertEquals(mapOf(day to setOf(1L)), viewModel.changedPairIdsByDay())

            changes.changes.value = emptyList()
            advanceUntilIdle()

            assertEquals(mapOf(day to emptySet<Long>()), viewModel.changedPairIdsByDay())
            assertEquals(requests, repository.refreshed.size)
        }

    private fun ScheduleViewModel.changedPairIdsByDay(): Map<LocalDate, Set<Long>> =
        (uiState.value as ScheduleUiState.Content).displayDays.associate { it.date to it.changedPairIds }

    private fun createViewModel(
        repository: ScheduleRepository,
        savedStateHandle: SavedStateHandle = SavedStateHandle(),
        changesRepository: FakeScheduleChangesRepository = FakeScheduleChangesRepository(),
        calendarSync: FakeCalendarSync = FakeCalendarSync()
    ): ScheduleViewModel {
        return ScheduleViewModel(
            repository = repository,
            timeProvider = timeProvider,
            savedStateHandle = savedStateHandle,
            preferences = FakeSchedulePreferencesRepository(),
            pendingRepository = FakePendingSportBookingsRepository(),
            changesRepository = changesRepository,
            calendarSync = calendarSync
        )
    }

    private fun initialRequest(userIsu: Int? = null) = ScheduleRequest(
        userIsu,
        timeProvider.today().minus(1, DateTimeUnit.DAY),
        timeProvider.today().plus(14, DateTimeUnit.DAY)
    )

    private fun daysIncludingNextPage(): List<DaySchedule> = (-1L..28L).map { offset ->
        daySchedule(timeProvider.today().plus(offset, DateTimeUnit.DAY))
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
}
