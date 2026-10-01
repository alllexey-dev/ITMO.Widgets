package dev.alllexey.itmowidgets.feature.recordbook.presentation.sheets

import androidx.lifecycle.SavedStateHandle
import dev.alllexey.itmowidgets.core.navigation.SheetScoresArgs
import dev.alllexey.itmowidgets.core.navigation.SheetScoresArgs.Step
import dev.alllexey.itmowidgets.core.resources.ResourceScope
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.testing.MainDispatcherRule
import dev.alllexey.itmowidgets.core.ui.toUiText
import dev.alllexey.itmowidgets.feature.recordbook.FakeSheetScoresRepository
import dev.alllexey.itmowidgets.feature.recordbook.TEST_IDENTITY
import dev.alllexey.itmowidgets.feature.recordbook.TEST_SHEET_URL
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.RowSearch
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetCell
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetColumnRef
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetGrid
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetInspection
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetRows
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetStatus
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetTab
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetWorkbook
import dev.alllexey.itmowidgets.feature.recordbook.sheetScore
import dev.alllexey.itmowidgets.feature.recordbook.testWorkbook
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SheetScoresViewModelTest {
    @get:Rule val main = MainDispatcherRule()
    private val repository = FakeSheetScoresRepository()
    private val scope = ResourceScope(1, "Тестовый предмет", "2026-1")
    private val grades = SheetTab(22, "P3110")

    private fun model(step: Step = Step.CONNECT) = SheetScoresViewModel(
        SavedStateHandle(
            mapOf(
                SheetScoresArgs.SUBJECT_ID to scope.subjectId,
                SheetScoresArgs.SUBJECT_NAME to scope.subjectName,
                SheetScoresArgs.PERIOD_KEY to scope.periodKey,
                SheetScoresArgs.URL to TEST_SHEET_URL,
                SheetScoresArgs.STEP to step.name,
            )
        ),
        repository,
    )

    private fun ready(book: SheetWorkbook, search: RowSearch = SheetRows.find(book, TEST_IDENTITY)) =
        SheetInspection.Ready(book, search)

    private fun only(file: String, tab: SheetTab = grades) = testWorkbook(tab to file)

    private fun SheetScoresViewModel.pickTotal() = state.value as SheetScoresUiState.PickTotal

    @Test fun `a total found by its header is connected at once`() = runTest(main.dispatcher) {
        repository.inspections += ready(testWorkbook())

        val vm = model()
        runCurrent()

        assertEquals(SheetScoresUiState.Done, vm.state.value)
        val connected = repository.connected.single()
        assertEquals(scope, connected.scope)
        assertEquals(TEST_SHEET_URL, connected.url)
        assertEquals(grades, connected.row.tab)
        assertEquals("123456", connected.row.key)
        assertEquals(SheetCell(grades, 11, "ИТОГО баллов", "66,3"), connected.total)
        assertEquals(listOf(TEST_SHEET_URL), repository.inspected)
    }

    @Test fun `without a total header the filled cells are offered`() = runTest(main.dispatcher) {
        repository.inspections += ready(only("no_total.csv"))

        val vm = model()
        runCurrent()

        val picker = vm.pickTotal()
        assertNull(picker.selected)
        assertEquals(listOf(2 to "8", 4 to "9"), picker.cells.map { it.column to it.value })
        vm.pickTotal(picker.cells.last())
        runCurrent()
        assertEquals(SheetScoresUiState.Done, vm.state.value)
        assertEquals("9", repository.connected.single().total.value)
    }

    @Test fun `several rows are a choice and the chosen row gives its cells`() = runTest(main.dispatcher) {
        repository.inspections += ready(only("duplicate_rows.csv"))

        val vm = model()
        runCurrent()

        val candidates = (vm.state.value as SheetScoresUiState.PickRow).candidates
        assertEquals(listOf(1, 3), candidates.map { it.row })
        vm.pickRow(candidates[1])
        runCurrent()
        assertEquals(listOf("P3112", "70"), vm.pickTotal().cells.map { it.value })
    }

    @Test fun `no own row offers the tabs with students, then their rows`() = runTest(main.dispatcher) {
        val template = SheetTab(0, "Шаблон")
        val book = testWorkbook(template to "name_only.csv", grades to "grades_multiheader.csv").let { book ->
            val empty = book.tabs[0].copy(grid = SheetGrid(listOf(listOf("ФИО", "Группа"))))
            book.copy(tabs = listOf(empty, book.tabs[1]))
        }
        repository.inspections += ready(book, RowSearch.NotFound)

        val vm = model()
        runCurrent()

        assertEquals(SheetScoresUiState.PickTab(listOf(grades)), vm.state.value)
        vm.pickTab(grades)
        val rows = (vm.state.value as SheetScoresUiState.PickTabRow).rows
        assertEquals(4, rows.size)
        vm.pickRow(rows[1])
        runCurrent()
        assertEquals("123456", repository.connected.single().row.key)
        assertEquals("66,3", repository.connected.single().total.value)
    }

    @Test fun `failures show their status and a retry asks again`() = runTest(main.dispatcher) {
        for (status in listOf(SheetStatus.CLOSED, SheetStatus.TOO_LARGE)) {
            repository.inspections += SheetInspection.Failed(status)
            val vm = model()
            runCurrent()
            assertEquals(SheetScoresUiState.Failed(status), vm.state.value)
        }

        repository.inspections += SheetInspection.Failed(SheetStatus.NETWORK)
        val vm = model()
        runCurrent()
        assertEquals(SheetScoresUiState.Failed(SheetStatus.NETWORK), vm.state.value)

        val gate = CompletableDeferred<Unit>()
        repository.inspectGate = gate
        repository.inspections += ready(only("no_total.csv"))
        vm.retry()
        runCurrent()
        assertEquals(SheetScoresUiState.Loading, vm.state.value)
        gate.complete(Unit)
        runCurrent()
        assertTrue(vm.state.value is SheetScoresUiState.PickTotal)
    }

    @Test fun `another total marks the current one and is saved as a change`() = runTest(main.dispatcher) {
        repository.scores.value = listOf(sheetScore(scope = scope))
        repository.inspections += ready(testWorkbook())

        val vm = model(Step.TOTAL)
        runCurrent()

        val picker = vm.pickTotal()
        assertEquals(SheetCell(grades, 11, "ИТОГО баллов", "66,3"), picker.selected)
        assertTrue(picker.cells.any { it.tab.gid == 11L })
        val grade = picker.cells.single { it.tab == grades && it.column == 12 }
        vm.pickTotal(grade)
        runCurrent()
        assertEquals(SheetScoresUiState.Done, vm.state.value)
        assertEquals(grade, repository.totals.single().total)
        assertTrue(repository.connected.isEmpty())
    }

    @Test fun `another total without the own row anywhere is a failure`() = runTest(main.dispatcher) {
        repository.scores.value = listOf(sheetScore(scope = scope, rowKey = "999999", column = SheetColumnRef("ИТОГО баллов", 11)))
        repository.inspections += ready(testWorkbook())

        val vm = model(Step.TOTAL)
        runCurrent()

        assertEquals(SheetScoresUiState.Failed(SheetStatus.ROW_NOT_FOUND), vm.state.value)
    }

    @Test fun `a failed save keeps the picker and a second tap while saving is ignored`() = runTest(main.dispatcher) {
        repository.inspections += ready(only("no_total.csv"))
        repository.connectResult = AppResult.Failure(AppError.Unknown())
        val vm = model()
        runCurrent()
        val picker = vm.pickTotal()

        vm.pickTotal(picker.cells.first())
        runCurrent()
        assertEquals(SheetScoresEvent.SaveFailed(AppError.Unknown().toUiText()), vm.events.first())
        assertEquals(picker, vm.state.value)

        repository.connectResult = AppResult.Success(Unit)
        val gate = CompletableDeferred<Unit>()
        repository.connectGate = gate
        vm.pickTotal(picker.cells.first())
        vm.pickTotal(picker.cells.last())
        runCurrent()
        gate.complete(Unit)
        runCurrent()
        assertEquals(2, repository.connected.size)
        assertEquals(picker.cells.first(), repository.connected.last().total)
        assertEquals(SheetScoresUiState.Done, vm.state.value)
    }

}
