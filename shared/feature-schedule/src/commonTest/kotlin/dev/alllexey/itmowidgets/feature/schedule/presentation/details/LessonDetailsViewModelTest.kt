package dev.alllexey.itmowidgets.feature.schedule.presentation.details

import androidx.lifecycle.SavedStateHandle
import dev.alllexey.itmowidgets.core.model.UserSharing
import dev.alllexey.itmowidgets.core.model.UserSummary
import dev.alllexey.itmowidgets.core.presentation.RefreshMode
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.reviews.TeacherLevel
import dev.alllexey.itmowidgets.core.schedule.ScheduleChangeField
import dev.alllexey.itmowidgets.core.schedule.ScheduleChangeKind
import dev.alllexey.itmowidgets.core.testing.FakeCustomServicesRepository
import dev.alllexey.itmowidgets.core.testing.FakeTeacherLevelsRepository
import dev.alllexey.itmowidgets.core.testing.scheduleChange
import dev.alllexey.itmowidgets.core.testing.slot
import dev.alllexey.itmowidgets.feature.schedule.FakeScheduleChangesRepository
import dev.alllexey.itmowidgets.feature.schedule.domain.LessonFriendsRepository
import dev.alllexey.itmowidgets.testkit.TestMainDispatcher
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

@OptIn(ExperimentalCoroutinesApi::class)
class LessonDetailsViewModelTest {

    private val main = TestMainDispatcher()

    @BeforeTest
    fun setUp() = main.install()

    @AfterTest
    fun tearDown() = main.reset()

    private val services = FakeCustomServicesRepository(enabled = true)
    private val repository = FakeLessonFriendsRepository()
    private val levels = FakeTeacherLevelsRepository()

    @Test
    fun withoutTheOptInTheBlockIsDisabledAndBackendIsNeverAsked() = runTest(main.dispatcher) {
        services.enabled.value = false
        val viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals(LessonFriendsState.Disabled, viewModel.uiState.value.friends)
        assertEquals(emptyList<Pair<Long, LocalDate>>(), repository.requests)
    }

    @Test
    fun friendsArriveInServerOrderForThePairAndDateTheSheetWasOpenedWith() =
        runTest(main.dispatcher) {
            val first = friend(200002, "Первый")
            val second = friend(300003, "Второй")
            repository.result = AppResult.Success(listOf(second, first))
            val viewModel = createViewModel()
            advanceUntilIdle()

            assertEquals(LessonFriendsState.Content(listOf(second, first)), viewModel.uiState.value.friends)
            assertEquals(listOf(42L to LocalDate(2026, 9, 8)), repository.requests)
        }

    @Test
    fun theSilentFirstLoadShowsTheBlockLoading() = runTest(main.dispatcher) {
        val answer = CompletableDeferred<AppResult<List<UserSummary>>>()
        repository.handler = { answer.await() }
        val viewModel = createViewModel()
        runCurrent()
        assertEquals(LessonFriendsState.Loading, viewModel.uiState.value.friends)

        answer.complete(AppResult.Success(emptyList()))
        advanceUntilIdle()
        assertEquals(LessonFriendsState.Content(emptyList()), viewModel.uiState.value.friends)
        assertEquals(1, repository.requests.size)
    }

    @Test
    fun aFailureIsReportedAndAForcedRetryShowsProgressAndAsksAgain() =
        runTest(main.dispatcher) {
            repository.result = AppResult.Failure(AppError.Network)
            val viewModel = createViewModel()
            advanceUntilIdle()
            assertEquals(LessonFriendsState.Error(AppError.Network), viewModel.uiState.value.friends)

            val answer = CompletableDeferred<AppResult<List<UserSummary>>>()
            repository.handler = { answer.await() }
            viewModel.refresh(RefreshMode.Force)
            runCurrent()
            assertEquals(LessonFriendsState.Loading, viewModel.uiState.value.friends)

            answer.complete(AppResult.Success(emptyList()))
            advanceUntilIdle()
            assertEquals(LessonFriendsState.Content(emptyList()), viewModel.uiState.value.friends)
            assertEquals(2, repository.requests.size)
        }

    @Test
    fun aPullDuringARefreshJoinsItInsteadOfAskingTwice() = runTest(main.dispatcher) {
        val answer = CompletableDeferred<AppResult<List<UserSummary>>>()
        repository.handler = { answer.await() }
        val viewModel = createViewModel()
        runCurrent()

        viewModel.refresh(RefreshMode.Pull)
        runCurrent()
        answer.complete(AppResult.Success(emptyList()))
        advanceUntilIdle()

        assertEquals(LessonFriendsState.Content(emptyList()), viewModel.uiState.value.friends)
        assertEquals(1, repository.requests.size)
    }

    @Test
    fun aFailedSilentRefreshKeepsTheErrorWithoutAProgressFlash() = runTest(main.dispatcher) {
        repository.result = AppResult.Failure(AppError.Network)
        val viewModel = createViewModel()
        advanceUntilIdle()

        val answer = CompletableDeferred<AppResult<List<UserSummary>>>()
        repository.handler = { answer.await() }
        viewModel.refresh(RefreshMode.Silent)
        runCurrent()
        assertEquals(LessonFriendsState.Error(AppError.Network), viewModel.uiState.value.friends)

        answer.complete(AppResult.Failure(AppError.Network))
        advanceUntilIdle()
        assertEquals(LessonFriendsState.Error(AppError.Network), viewModel.uiState.value.friends)
        assertEquals(2, repository.requests.size)
    }

    @Test
    fun anOptInRevokedBetweenTheCheckAndTheCallStillReadsAsDisabled() =
        runTest(main.dispatcher) {
            repository.result = AppResult.Failure(AppError.CustomServicesDisabled)
            val viewModel = createViewModel()
            advanceUntilIdle()

            assertEquals(LessonFriendsState.Disabled, viewModel.uiState.value.friends)
        }

    @Test
    fun theTeacherSToneArrivesWithTheOptIn() = runTest(main.dispatcher) {
        levels.levels[123456] = TeacherLevel.POSITIVE
        val viewModel = createViewModel(teacherIsu = 123456)
        advanceUntilIdle()

        assertEquals(TeacherLevel.POSITIVE, viewModel.uiState.value.teacherLevel)
        assertEquals(listOf(setOf(123456)), levels.calls)
    }

    @Test
    fun aTeacherWithoutAToneHasNoLevel() = runTest(main.dispatcher) {
        val viewModel = createViewModel(teacherIsu = 123456)
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.teacherLevel)
        assertEquals(listOf(setOf(123456)), levels.calls)
    }

    @Test
    fun withoutATeacherISUOrTheOptInThereIsNoLevelAndNoRequest() = runTest(main.dispatcher) {
        levels.levels[123456] = TeacherLevel.POSITIVE
        val withoutIsu = createViewModel(teacherIsu = null)
        advanceUntilIdle()
        services.enabled.value = false
        val withoutOptIn = createViewModel(teacherIsu = 123456)
        advanceUntilIdle()

        assertNull(withoutIsu.uiState.value.teacherLevel)
        assertNull(withoutOptIn.uiState.value.teacherLevel)
        assertEquals(emptyList<Set<Int>>(), levels.calls)
    }

    @Test
    fun theNewestChangeOfThisOccurrenceIsShown() = runTest(main.dispatcher) {
        val older = scheduleChange(id = "older", after = slot(42, DATE), detectedAt = Instant.parse("2026-09-06T09:00:00Z"))
        val newer = scheduleChange(
            id = "newer", fields = setOf(ScheduleChangeField.PLACE), before = slot(42, DATE),
            after = slot(42, DATE, room = "2202"), detectedAt = Instant.parse("2026-09-07T09:00:00Z")
        )
        val changes = FakeScheduleChangesRepository(older, newer)

        val viewModel = createViewModel(changes = changes)
        advanceUntilIdle()

        assertEquals(newer, viewModel.uiState.value.change)
    }

    @Test
    fun aChangeOfAnotherLessonOrDateIsNotThisOneS() = runTest(main.dispatcher) {
        val changes = FakeScheduleChangesRepository(
            scheduleChange(id = "other-lesson", after = slot(7, DATE)),
            scheduleChange(id = "other-date", kind = ScheduleChangeKind.ADDED, after = slot(42, DATE.plus(1, DateTimeUnit.DAY)))
        )

        val viewModel = createViewModel(changes = changes)
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.change)
    }

    @Test
    fun aCancellationIsFoundByItsOldSlotAndLeavesWithTheStore() = runTest(main.dispatcher) {
        val cancelled = scheduleChange(kind = ScheduleChangeKind.CANCELLED, before = slot(42, DATE))
        val changes = FakeScheduleChangesRepository(cancelled)

        val viewModel = createViewModel(changes = changes)
        advanceUntilIdle()
        assertEquals(cancelled, viewModel.uiState.value.change)

        changes.changes.value = emptyList()
        advanceUntilIdle()
        assertNull(viewModel.uiState.value.change)
    }

    private fun createViewModel(
        teacherIsu: Int? = null,
        changes: FakeScheduleChangesRepository = FakeScheduleChangesRepository()
    ) = LessonDetailsViewModel(
        savedStateHandle = SavedStateHandle(
            buildMap {
                put(LessonDetailsViewModel.ARG_PAIR_ID, 42L)
                put(LessonDetailsViewModel.ARG_DATE, "2026-09-08")
                teacherIsu?.let { put(LessonDetailsViewModel.ARG_TEACHER_ISU, it) }
            }
        ),
        friendsRepository = repository,
        customServices = services,
        teacherLevels = levels,
        changesRepository = changes,
    )

    private fun friend(isu: Int, name: String) = UserSummary(isu, name, null, emptyList(), UserSharing(sport = false, schedule = true))

    private class FakeLessonFriendsRepository : LessonFriendsRepository {
        var result: AppResult<List<UserSummary>> = AppResult.Success(emptyList())
        var handler: suspend () -> AppResult<List<UserSummary>> = { result }
        val requests = mutableListOf<Pair<Long, LocalDate>>()
        override suspend fun friendsOnLesson(pairId: Long, date: LocalDate): AppResult<List<UserSummary>> {
            requests += pairId to date
            return handler()
        }
    }

    private companion object {
        val DATE: LocalDate = LocalDate(2026, 9, 8)
    }
}
