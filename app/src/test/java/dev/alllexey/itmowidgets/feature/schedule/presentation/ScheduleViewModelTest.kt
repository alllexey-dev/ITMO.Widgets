package dev.alllexey.itmowidgets.feature.schedule.presentation

import androidx.lifecycle.SavedStateHandle
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.testing.MainDispatcherRule
import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import dev.alllexey.itmowidgets.domain.model.schedule.DaySchedule
import dev.alllexey.itmowidgets.domain.repository.ScheduleRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
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

            repository.refreshResult = AppResult.Failure(AppError.Forbidden)
            val event = async { viewModel.events.first() }
            viewModel.loadInitialSchedule(forceRefresh = true)
            advanceUntilIdle()

            assertEquals(
                ScheduleEvent.ShowError(AppError.Forbidden),
                event.await()
            )
            val state = viewModel.uiState.value as ScheduleUiState.Content
            assertEquals(repository.schedules.value, state.schedule)
            assertFalse(state.loadingMore)
            assertTrue(repository.cachesCleared)
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

    private fun daySchedule(): DaySchedule {
        return DaySchedule(
            dayNumber = 1,
            weekNumber = 1,
            date = LocalDate.of(2026, 2, 16),
            note = null,
            lessons = emptyList()
        )
    }

    private class FakeScheduleRepository : ScheduleRepository {
        val schedules = MutableStateFlow<List<DaySchedule>>(emptyList())
        var refreshResult: AppResult<Unit> = AppResult.Success(Unit)
        var cachesCleared = false

        override fun observeScheduleForRange(
            userIsu: Int?,
            startDate: LocalDate,
            endDate: LocalDate
        ): Flow<List<DaySchedule>> = schedules

        override suspend fun refreshSchedule(
            userIsu: Int?,
            startDate: LocalDate,
            endDate: LocalDate
        ): AppResult<Unit> = refreshResult

        override fun clearCaches() {
            cachesCleared = true
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
