package dev.alllexey.itmowidgets.feature.schedule.presentation.details

import androidx.lifecycle.SavedStateHandle
import dev.alllexey.itmowidgets.core.model.UserSharing
import dev.alllexey.itmowidgets.core.model.UserSummary
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.reviews.TeacherLevel
import dev.alllexey.itmowidgets.core.schedule.ScheduleChangeField
import dev.alllexey.itmowidgets.core.schedule.ScheduleChangeKind
import dev.alllexey.itmowidgets.core.testing.FakeCustomServicesRepository
import dev.alllexey.itmowidgets.core.testing.MainDispatcherRule
import dev.alllexey.itmowidgets.core.testing.scheduleChange
import dev.alllexey.itmowidgets.core.testing.slot
import dev.alllexey.itmowidgets.core.testing.FakeTeacherLevelsRepository
import dev.alllexey.itmowidgets.feature.schedule.domain.LessonFriendsRepository
import dev.alllexey.itmowidgets.feature.schedule.FakeScheduleChangesRepository
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LessonDetailsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val services = FakeCustomServicesRepository(enabled = true)
    private val repository = FakeLessonFriendsRepository()
    private val levels = FakeTeacherLevelsRepository()

    @Test
    fun `without the opt-in the block is disabled and Backend is never asked`() = runTest(mainDispatcherRule.dispatcher) {
        services.enabled.value = false
        val viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals(LessonFriendsState.Disabled, viewModel.friends.value)
        assertEquals(emptyList<Pair<Long, LocalDate>>(), repository.requests)
    }

    @Test
    fun `friends arrive in server order for the pair and date the sheet was opened with`() =
        runTest(mainDispatcherRule.dispatcher) {
            val first = friend(200002, "Первый")
            val second = friend(300003, "Второй")
            repository.result = AppResult.Success(listOf(second, first))
            val viewModel = createViewModel()
            advanceUntilIdle()

            assertEquals(LessonFriendsState.Content(listOf(second, first)), viewModel.friends.value)
            assertEquals(listOf(42L to LocalDate.of(2026, 9, 8)), repository.requests)
        }

    @Test
    fun `a failure is reported and retry asks again`() = runTest(mainDispatcherRule.dispatcher) {
        repository.result = AppResult.Failure(AppError.Network)
        val viewModel = createViewModel()
        advanceUntilIdle()
        assertEquals(LessonFriendsState.Error(AppError.Network), viewModel.friends.value)

        repository.result = AppResult.Success(emptyList())
        viewModel.retry()
        advanceUntilIdle()

        assertEquals(LessonFriendsState.Content(emptyList()), viewModel.friends.value)
        assertEquals(2, repository.requests.size)
    }

    @Test
    fun `an opt-in revoked between the check and the call still reads as disabled`() =
        runTest(mainDispatcherRule.dispatcher) {
            repository.result = AppResult.Failure(AppError.CustomServicesDisabled)
            val viewModel = createViewModel()
            advanceUntilIdle()

            assertEquals(LessonFriendsState.Disabled, viewModel.friends.value)
        }

    @Test
    fun `the teacher's tone arrives with the opt-in`() = runTest(mainDispatcherRule.dispatcher) {
        levels.levels[123456] = TeacherLevel.POSITIVE
        val viewModel = createViewModel(teacherIsu = 123456)
        advanceUntilIdle()

        assertEquals(TeacherLevel.POSITIVE, viewModel.teacherLevel.value)
        assertEquals(listOf(setOf(123456)), levels.calls)
    }

    @Test
    fun `a teacher without a tone has no level`() = runTest(mainDispatcherRule.dispatcher) {
        val viewModel = createViewModel(teacherIsu = 123456)
        advanceUntilIdle()

        assertNull(viewModel.teacherLevel.value)
        assertEquals(listOf(setOf(123456)), levels.calls)
    }

    @Test
    fun `without a teacher ISU or the opt-in there is no level and no request`() = runTest(mainDispatcherRule.dispatcher) {
        levels.levels[123456] = TeacherLevel.POSITIVE
        val withoutIsu = createViewModel(teacherIsu = null)
        advanceUntilIdle()
        services.enabled.value = false
        val withoutOptIn = createViewModel(teacherIsu = 123456)
        advanceUntilIdle()

        assertNull(withoutIsu.teacherLevel.value)
        assertNull(withoutOptIn.teacherLevel.value)
        assertEquals(emptyList<Set<Int>>(), levels.calls)
    }

    @Test
    fun `the newest change of this occurrence is shown`() = runTest(mainDispatcherRule.dispatcher) {
        val older = scheduleChange(id = "older", after = slot(42, DATE), detectedAt = Instant.parse("2026-09-06T09:00:00Z"))
        val newer = scheduleChange(
            id = "newer", fields = setOf(ScheduleChangeField.PLACE), before = slot(42, DATE),
            after = slot(42, DATE, room = "2202"), detectedAt = Instant.parse("2026-09-07T09:00:00Z")
        )
        val changes = FakeScheduleChangesRepository(older, newer)

        val viewModel = createViewModel(changes = changes)
        advanceUntilIdle()

        assertEquals(newer, viewModel.change.value)
    }

    @Test
    fun `a change of another lesson or date is not this one's`() = runTest(mainDispatcherRule.dispatcher) {
        val changes = FakeScheduleChangesRepository(
            scheduleChange(id = "other-lesson", after = slot(7, DATE)),
            scheduleChange(id = "other-date", kind = ScheduleChangeKind.ADDED, after = slot(42, DATE.plusDays(1)))
        )

        val viewModel = createViewModel(changes = changes)
        advanceUntilIdle()

        assertNull(viewModel.change.value)
    }

    @Test
    fun `a cancellation is found by its old slot and leaves with the store`() = runTest(mainDispatcherRule.dispatcher) {
        val cancelled = scheduleChange(kind = ScheduleChangeKind.CANCELLED, before = slot(42, DATE))
        val changes = FakeScheduleChangesRepository(cancelled)

        val viewModel = createViewModel(changes = changes)
        advanceUntilIdle()
        assertEquals(cancelled, viewModel.change.value)

        changes.changes.value = emptyList()
        advanceUntilIdle()
        assertNull(viewModel.change.value)
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
        val requests = mutableListOf<Pair<Long, LocalDate>>()
        override suspend fun friendsOnLesson(pairId: Long, date: LocalDate): AppResult<List<UserSummary>> {
            requests += pairId to date
            return result
        }
    }

    private companion object {
        val DATE: LocalDate = LocalDate.of(2026, 9, 8)
    }
}
