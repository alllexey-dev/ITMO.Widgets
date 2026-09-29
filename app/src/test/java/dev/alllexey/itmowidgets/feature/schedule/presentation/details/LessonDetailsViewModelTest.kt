package dev.alllexey.itmowidgets.feature.schedule.presentation.details

import androidx.lifecycle.SavedStateHandle
import dev.alllexey.itmowidgets.core.model.UserSharing
import dev.alllexey.itmowidgets.core.model.UserSummary
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.reviews.TeacherLevel
import dev.alllexey.itmowidgets.core.services.CustomServicesRepository
import dev.alllexey.itmowidgets.core.testing.MainDispatcherRule
import dev.alllexey.itmowidgets.feature.reviews.presentation.FakeTeacherLevelsRepository
import dev.alllexey.itmowidgets.feature.schedule.domain.LessonFriendsRepository
import java.time.LocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
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

    private val services = FakeCustomServices()
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

    private fun createViewModel(teacherIsu: Int? = null) = LessonDetailsViewModel(
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
    )

    private fun friend(isu: Int, name: String) = UserSummary(isu, name, null, emptyList(), UserSharing(sport = false, schedule = true))

    private class FakeCustomServices : CustomServicesRepository {
        val enabled = MutableStateFlow(true)
        override fun observeEnabled(): Flow<Boolean> = enabled
        override suspend fun isEnabled(): Boolean = enabled.value
        override suspend fun setEnabled(enabled: Boolean) { this.enabled.value = enabled }
    }

    private class FakeLessonFriendsRepository : LessonFriendsRepository {
        var result: AppResult<List<UserSummary>> = AppResult.Success(emptyList())
        val requests = mutableListOf<Pair<Long, LocalDate>>()
        override suspend fun friendsOnLesson(pairId: Long, date: LocalDate): AppResult<List<UserSummary>> {
            requests += pairId to date
            return result
        }
    }
}
