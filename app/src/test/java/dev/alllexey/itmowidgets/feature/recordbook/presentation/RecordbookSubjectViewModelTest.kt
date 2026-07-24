package dev.alllexey.itmowidgets.feature.recordbook.presentation

import androidx.lifecycle.SavedStateHandle
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.testing.MainDispatcherRule
import dev.alllexey.itmowidgets.feature.recordbook.domain.RecordbookRepository
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookControl
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookProgram
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookSubject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RecordbookSubjectViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `renders content for subject controls`() =
        runTest(mainDispatcherRule.dispatcher) {
            val control = RecordbookControl(
                id = 1,
                name = "Лабораторная работа",
                score = 10.0,
                minimum = 0.0,
                maximum = 10.0,
                required = true,
                date = null,
                teacherName = null
            )
            val viewModel = createViewModel(AppResult.Success(listOf(control)))

            advanceUntilIdle()

            assertEquals(
                RecordbookSubjectUiState.Content(listOf(control)),
                viewModel.uiState.value
            )
        }

    @Test
    fun `renders empty state for missing controls`() =
        runTest(mainDispatcherRule.dispatcher) {
            val viewModel = createViewModel(AppResult.Success(emptyList()))

            advanceUntilIdle()

            assertEquals(RecordbookSubjectUiState.Empty, viewModel.uiState.value)
        }

    @Test
    fun `renders typed error for failed controls request`() =
        runTest(mainDispatcherRule.dispatcher) {
            val viewModel = createViewModel(AppResult.Failure(AppError.Forbidden))

            advanceUntilIdle()

            assertEquals(
                RecordbookSubjectUiState.Error(AppError.Forbidden),
                viewModel.uiState.value
            )
        }

    private fun createViewModel(
        result: AppResult<List<RecordbookControl>>
    ): RecordbookSubjectViewModel {
        return RecordbookSubjectViewModel(
            repository = FakeRecordbookRepository(result),
            savedStateHandle = SavedStateHandle(
                mapOf(RecordbookSubjectViewModel.ARG_ENTRY_ID to 42L)
            )
        )
    }

    private class FakeRecordbookRepository(
        private val controlsResult: AppResult<List<RecordbookControl>>
    ) : RecordbookRepository {

        override suspend fun getPrograms(): AppResult<List<RecordbookProgram>> {
            return AppResult.Success(emptyList())
        }

        override suspend fun getSubjects(
            programId: Long,
            semester: Int
        ): AppResult<List<RecordbookSubject>> {
            return AppResult.Success(emptyList())
        }

        override suspend fun getControls(entryId: Long): AppResult<List<RecordbookControl>> {
            return controlsResult
        }
    }
}
