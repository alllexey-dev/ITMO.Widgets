package dev.alllexey.itmowidgets.feature.recordbook.presentation

import androidx.lifecycle.SavedStateHandle
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.testing.MainDispatcherRule
import dev.alllexey.itmowidgets.feature.recordbook.domain.RecordbookRepository
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookControl
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookPeriod
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookProgram
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookSubject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RecordbookViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `loads actual period and renders subjects`() =
        runTest(mainDispatcherRule.dispatcher) {
            val repository = FakeRecordbookRepository(
                programsResult = AppResult.Success(listOf(program())),
                subjectsResult = AppResult.Success(listOf(subject()))
            )
            val viewModel = RecordbookViewModel(repository, SavedStateHandle())

            viewModel.ensureDataLoaded()
            advanceUntilIdle()

            val state = viewModel.uiState.value as RecordbookUiState.Content
            assertEquals(2, state.selection.period.semester)
            assertEquals(repository.subjectsResult.valueOrEmpty(), state.subjects)
        }

    @Test
    fun `filters subjects without reloading repository`() =
        runTest(mainDispatcherRule.dispatcher) {
            val exam = subject(controlType = "Экзамен")
            val credit = subject(entryId = 2, controlType = "Зачёт")
            val repository = FakeRecordbookRepository(
                programsResult = AppResult.Success(listOf(program())),
                subjectsResult = AppResult.Success(listOf(exam, credit))
            )
            val viewModel = RecordbookViewModel(repository, SavedStateHandle())
            viewModel.ensureDataLoaded()
            advanceUntilIdle()

            viewModel.setFilter(RecordbookFilter.EXAMS)

            val state = viewModel.uiState.value as RecordbookUiState.Content
            assertEquals(listOf(exam), state.subjects)
            assertEquals(1, repository.subjectRequests)
        }

    @Test
    fun `renders typed repository error`() =
        runTest(mainDispatcherRule.dispatcher) {
            val viewModel = RecordbookViewModel(
                FakeRecordbookRepository(
                    programsResult = AppResult.Failure(AppError.Network)
                ),
                SavedStateHandle()
            )

            viewModel.ensureDataLoaded()
            advanceUntilIdle()

            assertEquals(
                RecordbookUiState.Error(AppError.Network),
                viewModel.uiState.value
            )
        }

    @Test
    fun `renders not found when programs have no periods`() =
        runTest(mainDispatcherRule.dispatcher) {
            val repository = FakeRecordbookRepository(
                programsResult = AppResult.Success(
                    listOf(RecordbookProgram(1, "Программа", emptyList()))
                )
            )
            val viewModel = RecordbookViewModel(repository, SavedStateHandle())

            viewModel.ensureDataLoaded()
            advanceUntilIdle()

            assertEquals(
                RecordbookUiState.Error(AppError.NotFound),
                viewModel.uiState.value
            )
        }

    private fun program(): RecordbookProgram {
        return RecordbookProgram(
            id = 1,
            name = "Программа",
            periods = listOf(
                RecordbookPeriod("2024/2025", 1, 1, actual = false),
                RecordbookPeriod("2025/2026", 2, 1, actual = true)
            )
        )
    }

    private fun subject(
        entryId: Long = 1,
        controlType: String = "Экзамен"
    ): RecordbookSubject {
        return RecordbookSubject(
            name = "Архитектура ПО",
            disciplineId = 10,
            entryId = entryId,
            controlType = controlType,
            score = 95.0,
            rate = "5A",
            attempt = 1,
            examDate = null,
            hasDetails = true,
            teacherName = "Иванов И. И."
        )
    }

    private fun AppResult<List<RecordbookSubject>>.valueOrEmpty(): List<RecordbookSubject> {
        return (this as? AppResult.Success)?.value.orEmpty()
    }

    private class FakeRecordbookRepository(
        var programsResult: AppResult<List<RecordbookProgram>> =
            AppResult.Success(emptyList()),
        var subjectsResult: AppResult<List<RecordbookSubject>> =
            AppResult.Success(emptyList()),
        var controlsResult: AppResult<List<RecordbookControl>> =
            AppResult.Success(emptyList())
    ) : RecordbookRepository {

        var subjectRequests = 0

        override suspend fun getPrograms(): AppResult<List<RecordbookProgram>> {
            return programsResult
        }

        override suspend fun getSubjects(
            programId: Long,
            semester: Int
        ): AppResult<List<RecordbookSubject>> {
            subjectRequests += 1
            return subjectsResult
        }

        override suspend fun getControls(entryId: Long): AppResult<List<RecordbookControl>> {
            return controlsResult
        }
    }
}
