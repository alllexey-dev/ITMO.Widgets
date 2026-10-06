package dev.alllexey.itmowidgets.feature.sport.presentation.user

import androidx.lifecycle.SavedStateHandle
import dev.alllexey.itmowidgets.core.navigation.UserScreenArgs
import dev.alllexey.itmowidgets.core.presentation.RefreshMode
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.result.LoadState
import dev.alllexey.itmowidgets.feature.sport.domain.model.SectionName
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportBooking
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportFilterCatalog
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportLesson
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportTimeSlot
import dev.alllexey.itmowidgets.feature.sport.domain.repository.SportScheduleRepository
import dev.alllexey.itmowidgets.feature.sport.domain.repository.UserSportBookings
import dev.alllexey.itmowidgets.feature.sport.domain.repository.UserSportRepository
import dev.alllexey.itmowidgets.testkit.TestMainDispatcher
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class UserSportViewModelTest {

    private val main = TestMainDispatcher()

    @BeforeTest
    fun setUpMain() = main.install()

    @AfterTest
    fun tearDownMain() = main.reset()

    @Test
    fun confirmedLessonsResolveAgainstTheCatalogWithoutTheMergedSchedule() = runTest(main.dispatcher) {
        val schedule = CatalogSportScheduleRepository(catalog = AppResult.Success(listOf(lesson(10), lesson(11))))
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
    fun aFailedCatalogStillShowsPendingEntries() = runTest(main.dispatcher) {
        val schedule = CatalogSportScheduleRepository(catalog = AppResult.Failure(AppError.Network))
        val bookings = FakeUserSportRepository(AppResult.Success(UserSportBookings(
            confirmedLessonIds = listOf(11),
            pending = listOf(pending(12))
        )))

        val viewModel = UserSportViewModel(handle(5), bookings, schedule)
        advanceUntilIdle()

        assertEquals(listOf(12L), (viewModel.uiState.value as UserSportUiState.Content).bookings.map { it.lessonId })
    }

    @Test
    fun backendRefusalIsSurfacedAsAnError() = runTest(main.dispatcher) {
        val schedule = CatalogSportScheduleRepository(catalog = AppResult.Success(emptyList()))
        val viewModel = UserSportViewModel(handle(5), FakeUserSportRepository(AppResult.Failure(AppError.Forbidden)), schedule)
        advanceUntilIdle()

        assertEquals(UserSportUiState.Error(AppError.Forbidden), viewModel.uiState.value)
    }

    @Test
    fun aPullKeepsTheListUnderTheIndicator() = runTest(main.dispatcher) {
        val schedule = CatalogSportScheduleRepository(catalog = AppResult.Success(emptyList()))
        val bookings = FakeUserSportRepository(AppResult.Success(UserSportBookings(emptyList(), listOf(pending(12)))))
        val viewModel = UserSportViewModel(handle(5), bookings, schedule)
        advanceUntilIdle()

        bookings.gate = CompletableDeferred()
        viewModel.refresh(RefreshMode.Pull)
        viewModel.refresh(RefreshMode.Pull)
        runCurrent()
        val during = viewModel.uiState.value as UserSportUiState.Content
        assertTrue(during.refreshing)
        assertEquals(listOf(12L), during.bookings.map { it.lessonId })

        bookings.gate.complete(Unit)
        advanceUntilIdle()
        assertFalse((viewModel.uiState.value as UserSportUiState.Content).refreshing)
        assertEquals(listOf(5, 5), bookings.requested)
    }

    @Test
    fun aRetryOverAnErrorShowsProgressUntilTheAnswer() = runTest(main.dispatcher) {
        val schedule = CatalogSportScheduleRepository(catalog = AppResult.Success(emptyList()))
        val bookings = FakeUserSportRepository(AppResult.Failure(AppError.Network))
        val viewModel = UserSportViewModel(handle(5), bookings, schedule)
        advanceUntilIdle()
        assertEquals(UserSportUiState.Error(AppError.Network), viewModel.uiState.value)

        bookings.gate = CompletableDeferred()
        bookings.result = AppResult.Success(UserSportBookings(emptyList(), listOf(pending(12))))
        viewModel.refresh(RefreshMode.Force)
        runCurrent()
        assertEquals(UserSportUiState.Loading, viewModel.uiState.value)

        bookings.gate.complete(Unit)
        advanceUntilIdle()
        assertEquals(listOf(12L), (viewModel.uiState.value as UserSportUiState.Content).bookings.map { it.lessonId })
    }

    private fun handle(isu: Int) = SavedStateHandle(mapOf(UserScreenArgs.ISU to isu, UserScreenArgs.NAME to "Friend"))

    private fun lesson(id: Long) = SportLesson(
        isLessonReal = true,
        lessonId = id,
        start = Instant.parse("2026-09-19T09:00:00+03:00") + id.hours,
        end = Instant.parse("2026-09-19T10:30:00+03:00") + id.hours,
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
        start = Instant.parse("2026-09-19T09:00:00+03:00") + id.hours,
        end = Instant.parse("2026-09-19T10:30:00+03:00") + id.hours,
        roomName = "Зал",
        teacherFio = "Преподаватель",
        teacherIsu = 1,
        sectionLevel = 1,
        lessonLevel = 1,
        signed = false,
        signEntry = null,
        friendsBookings = emptyList()
    )

    private class FakeUserSportRepository(var result: AppResult<UserSportBookings>) : UserSportRepository {
        val requested = mutableListOf<Int>()
        var gate: CompletableDeferred<Unit> = CompletableDeferred(Unit)

        override suspend fun getUserBookings(isu: Int): AppResult<UserSportBookings> {
            requested += isu
            gate.await()
            return result
        }
    }

    /** The merged schedule never emits here, exactly like a process whose sport tab was never opened. */
    private class CatalogSportScheduleRepository(private val catalog: AppResult<List<SportLesson>>) : SportScheduleRepository {
        var refreshCount = 0

        override fun observeSportSchedule(): Flow<LoadState<List<SportLesson>>> = MutableSharedFlow()

        override suspend fun refreshSportSchedule() {
            refreshCount += 1
        }

        override fun observeSportCatalog(): Flow<AppResult<List<SportLesson>>> = flowOf(catalog)

        override fun observeSportFilters(): Flow<AppResult<SportFilterCatalog>> =
            flowOf(AppResult.Success(SportFilterCatalog(emptyList(), emptyList(), emptyList(), emptyList())))

        override suspend fun refreshSportFilters() = Unit

        override fun observeSportTimeSlots(): Flow<AppResult<List<SportTimeSlot>>> = flowOf(AppResult.Success(emptyList()))

        override suspend fun refreshSportTimeSlots() = Unit
    }
}
