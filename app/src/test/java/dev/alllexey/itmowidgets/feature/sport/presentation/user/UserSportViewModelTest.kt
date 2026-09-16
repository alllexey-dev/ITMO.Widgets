package dev.alllexey.itmowidgets.feature.sport.presentation.user

import androidx.lifecycle.SavedStateHandle
import dev.alllexey.itmowidgets.core.navigation.UserScreenArgs
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.testing.MainDispatcherRule
import dev.alllexey.itmowidgets.core.util.DataState
import dev.alllexey.itmowidgets.core.util.MergedDataState
import dev.alllexey.itmowidgets.feature.sport.domain.model.SectionName
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportBooking
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportFilterCatalog
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportLesson
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportTimeSlot
import dev.alllexey.itmowidgets.feature.sport.domain.repository.SportScheduleRepository
import dev.alllexey.itmowidgets.feature.sport.domain.repository.UserSportBookings
import dev.alllexey.itmowidgets.feature.sport.domain.repository.UserSportRepository
import java.time.OffsetDateTime
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class UserSportViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `confirmed lessons resolve against the catalog without the merged schedule`() = runTest(mainDispatcherRule.dispatcher) {
        val schedule = FakeSportScheduleRepository(catalog = DataState.Success(listOf(lesson(10), lesson(11))))
        val bookings = FakeUserSportRepository(AppResult.Success(UserSportBookings(
            confirmedLessonIds = listOf(11),
            pending = listOf(pending(12), pending(11))
        )))

        val viewModel = UserSportViewModel(handle(5), bookings, schedule)
        advanceUntilIdle()

        val content = viewModel.uiState.value as UserSportUiState.Content
        assertEquals(listOf(11L, 12L), content.bookings.map { it.lessonId })
        assertEquals(listOf(true, false), content.bookings.map { it.signed })
        assertEquals(1, schedule.refreshCount)
        assertEquals(listOf(5), bookings.requested)
    }

    @Test
    fun `a failed catalog still shows pending entries`() = runTest(mainDispatcherRule.dispatcher) {
        val schedule = FakeSportScheduleRepository(catalog = DataState.Error(AppError.Network))
        val bookings = FakeUserSportRepository(AppResult.Success(UserSportBookings(
            confirmedLessonIds = listOf(11),
            pending = listOf(pending(12))
        )))

        val viewModel = UserSportViewModel(handle(5), bookings, schedule)
        advanceUntilIdle()

        assertEquals(listOf(12L), (viewModel.uiState.value as UserSportUiState.Content).bookings.map { it.lessonId })
    }

    @Test
    fun `backend refusal is surfaced as an error`() = runTest(mainDispatcherRule.dispatcher) {
        val schedule = FakeSportScheduleRepository(catalog = DataState.Success(emptyList()))
        val viewModel = UserSportViewModel(handle(5), FakeUserSportRepository(AppResult.Failure(AppError.Forbidden)), schedule)
        advanceUntilIdle()

        assertEquals(UserSportUiState.Error(AppError.Forbidden), viewModel.uiState.value)
    }

    private fun handle(isu: Int) = SavedStateHandle(mapOf(UserScreenArgs.ISU to isu, UserScreenArgs.NAME to "Friend"))

    private fun lesson(id: Long) = SportLesson(
        isLessonReal = true,
        lessonId = id,
        start = OffsetDateTime.parse("2026-09-19T09:00:00+03:00").plusHours(id),
        end = OffsetDateTime.parse("2026-09-19T10:30:00+03:00").plusHours(id),
        sectionId = 1,
        sectionName = SectionName("Фитнес"),
        sectionLevel = 1,
        lessonGroupId = 1,
        lessonLevel = 1,
        typeId = 1,
        buildingId = 1,
        roomId = 1,
        roomName = "Зал",
        limit = 20,
        available = 5,
        comment = null,
        timeSlotId = 1,
        timeSlotStart = "09:00",
        timeSlotEnd = "10:30",
        intersection = false,
        canSignIn = true,
        unavailableReasons = emptyList(),
        signed = false,
        teacherIsu = 1,
        teacherFio = "Преподаватель",
        signEntry = null,
        signQueue = null,
        friendsBookings = emptyList()
    )

    private fun pending(id: Long) = SportBooking(
        isLessonReal = true,
        lessonId = id,
        sectionName = SectionName("Фитнес"),
        start = OffsetDateTime.parse("2026-09-19T09:00:00+03:00").plusHours(id),
        end = OffsetDateTime.parse("2026-09-19T10:30:00+03:00").plusHours(id),
        roomName = "Зал",
        teacherFio = "Преподаватель",
        teacherIsu = 1,
        sectionLevel = 1,
        lessonLevel = 1,
        signed = false,
        signEntry = null,
        friendsBookings = emptyList()
    )

    private class FakeUserSportRepository(private val result: AppResult<UserSportBookings>) : UserSportRepository {
        val requested = mutableListOf<Int>()

        override suspend fun getUserBookings(isu: Int): AppResult<UserSportBookings> {
            requested += isu
            return result
        }
    }

    /** The merged schedule never emits here, exactly like a process whose sport tab was never opened. */
    private class FakeSportScheduleRepository(private val catalog: DataState<List<SportLesson>>) : SportScheduleRepository {
        var refreshCount = 0

        override fun observeSportSchedule(): Flow<MergedDataState<List<SportLesson>>> = MutableSharedFlow()

        override suspend fun refreshSportSchedule() {
            refreshCount += 1
        }

        override fun observeSportCatalog(): Flow<DataState<List<SportLesson>>> = flowOf(catalog)

        override fun observeSportFilters(): Flow<DataState<SportFilterCatalog>> =
            flowOf(DataState.Success(SportFilterCatalog(emptyList(), emptyList(), emptyList(), emptyList())))

        override suspend fun refreshSportFilters() = Unit

        override fun observeSportTimeSlots(): Flow<DataState<List<SportTimeSlot>>> = flowOf(DataState.Success(emptyList()))

        override suspend fun refreshSportTimeSlots() = Unit
    }
}
