package dev.alllexey.itmowidgets.feature.recordbook.presentation.sheets

import androidx.lifecycle.SavedStateHandle
import dev.alllexey.itmowidgets.core.navigation.SheetScoresArgs
import dev.alllexey.itmowidgets.core.navigation.SheetScoresArgs.Step
import dev.alllexey.itmowidgets.core.presentation.RefreshMode
import dev.alllexey.itmowidgets.core.resources.ResourceScope
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.core.text.toUiText
import dev.alllexey.itmowidgets.feature.recordbook.FakeSheetScoresRepository
import dev.alllexey.itmowidgets.feature.recordbook.MainDispatcherRule
import dev.alllexey.itmowidgets.feature.recordbook.TEST_IDENTITY
import dev.alllexey.itmowidgets.feature.recordbook.TEST_SHEET_URL
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.CsvGrid
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.OwnIdentity
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.RowSearch
import dev.alllexey.itmowidgets.shared.feature.recordbook.RecordbookStrings
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetCell
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetColumnRef
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetGrid
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetInspection
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetRows
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetStatus
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetTab
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetTabGrid
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetTotals
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetWorkbook
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.TotalChoice
import dev.alllexey.itmowidgets.feature.recordbook.sheetScore
import dev.alllexey.itmowidgets.feature.recordbook.testWorkbook
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    private fun SheetScoresViewModel.pickTotal() = uiState.value as SheetScoresUiState.PickTotal

    @Test fun `a total found by its header is connected at once`() = runTest(main.dispatcher) {
        repository.inspections += ready(testWorkbook())

        val vm = model()
        runCurrent()

        assertEquals(SheetScoresUiState.Done, vm.uiState.value)
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
        assertEquals(listOf(2 to "8", 4 to "9"), picker.cells.filter { it.value.isNotEmpty() }.map { it.column to it.value })
        assertNull(picker.decision)
        vm.pickTotal(picker.cells.last())
        runCurrent()
        assertEquals(SheetScoresUiState.Done, vm.uiState.value)
        assertEquals("9", repository.connected.single().total.value)
    }

    @Test fun `several rows are a choice and the chosen row gives its cells`() = runTest(main.dispatcher) {
        repository.inspections += ready(only("duplicate_rows.csv"))

        val vm = model()
        runCurrent()

        val candidates = (vm.uiState.value as SheetScoresUiState.PickRow).candidates
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

        assertEquals(SheetScoresUiState.PickTab(listOf(grades)), vm.uiState.value)
        vm.pickTab(grades)
        val rows = (vm.uiState.value as SheetScoresUiState.PickTabRow).rows
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
            assertEquals(SheetScoresUiState.Failed(status), vm.uiState.value)
        }

        repository.inspections += SheetInspection.Failed(SheetStatus.NETWORK)
        val vm = model()
        runCurrent()
        assertEquals(SheetScoresUiState.Failed(SheetStatus.NETWORK), vm.uiState.value)

        val gate = CompletableDeferred<Unit>()
        repository.inspectGate = gate
        repository.inspections += ready(only("no_total.csv"))
        vm.refresh(RefreshMode.Force)
        runCurrent()
        assertEquals(SheetScoresUiState.Loading, vm.uiState.value)
        gate.complete(Unit)
        runCurrent()
        assertTrue(vm.uiState.value is SheetScoresUiState.PickTotal)
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
        assertEquals(picker.selected, picker.decision?.total)
        assertFalse(picker.canReset)
        vm.pickTotal(grade)
        runCurrent()
        assertEquals(SheetScoresUiState.Done, vm.uiState.value)
        assertEquals(grade, repository.totals.single().total)
        assertEquals(TotalChoice.MANUAL, repository.totals.single().choice)
        assertTrue(repository.connected.isEmpty())
    }

    @Test fun `the automatic total picked again is confirmed`() = runTest(main.dispatcher) {
        repository.scores.value = listOf(sheetScore(scope = scope).copy(choice = TotalChoice.AUTO_CHECK))
        repository.inspections += ready(testWorkbook())
        val vm = model(Step.TOTAL)
        runCurrent()

        vm.pickTotal(vm.pickTotal().decision!!.total)
        runCurrent()

        assertEquals(TotalChoice.AUTO, repository.totals.single().choice)
    }

    @Test fun `«Вернуть авто» replaces a hand-picked total with the automatic one`() = runTest(main.dispatcher) {
        val grade = SheetColumnRef("Оценка · 60-100", 12)
        repository.scores.value = listOf(sheetScore(scope = scope, column = grade).copy(choice = TotalChoice.MANUAL))
        repository.inspections += ready(testWorkbook())
        val vm = model(Step.TOTAL)
        runCurrent()
        val picker = vm.pickTotal()
        assertEquals(12, picker.selected?.column)
        assertTrue(picker.canReset)
        assertEquals("ИТОГО баллов", picker.candidates.first().headerPath)

        vm.resetTotal()
        runCurrent()

        assertEquals(SheetCell(grades, 11, "ИТОГО баллов", "66,3"), repository.totals.single().total)
        assertEquals(TotalChoice.AUTO, repository.totals.single().choice)
    }

    @Test fun `a total worth a look is connected as one`() = runTest(main.dispatcher) {
        val grid = CsvGrid.parse("ИСУ,ФИО,ЛР1,Score\n123456,Тестов Тест Тестович,5,5")
        repository.inspections += ready(SheetWorkbook(testWorkbook().url, listOf(SheetTabGrid(grades, grid))))

        model()
        runCurrent()

        assertEquals(TotalChoice.AUTO_CHECK, repository.connected.single().choice)
    }

    @Test fun `another total without the own row anywhere is a failure`() = runTest(main.dispatcher) {
        repository.scores.value = listOf(sheetScore(scope = scope, rowKey = "999999", column = SheetColumnRef("ИТОГО баллов", 11)))
        repository.inspections += ready(testWorkbook())

        val vm = model(Step.TOTAL)
        runCurrent()

        assertEquals(SheetScoresUiState.Failed(SheetStatus.ROW_NOT_FOUND), vm.uiState.value)
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
        assertEquals(picker, vm.uiState.value)

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
        assertEquals(SheetScoresUiState.Done, vm.uiState.value)
    }

    @Test fun `a sheet without total formula offers activity sum candidate chip and allows manual pick`() = runTest(main.dispatcher) {
        val matanTab = SheetTab(1, "МатАн")
        val book = testWorkbook(matanTab to "matan.csv")
        val student = OwnIdentity(100004, "Тестов Тест Тестович", "Тестов", "Тест", "Тестович")
        repository.inspections += ready(book, SheetRows.find(book, student))

        val vm = model()
        runCurrent()

        assertEquals(SheetScoresUiState.Done, vm.uiState.value)
        val connected = repository.connected.single()
        assertEquals("", connected.total.value)
        assertEquals(TotalChoice.AUTO_CHECK, connected.choice)
        assertEquals(24, connected.total.column)
        assertEquals("Итого", connected.total.headerPath)

        // Now test Step.TOTAL with this score
        repository.scores.value = listOf(
            sheetScore(
                scope = scope,
                url = TEST_SHEET_URL,
                tabGid = matanTab.gid,
                tabName = matanTab.name,
                rowKey = "100004",
                column = SheetColumnRef("Итого", 24),
                value = "",
            ).copy(choice = TotalChoice.AUTO_CHECK)
        )
        repository.inspections += ready(book, SheetRows.find(book, student))
        val vmTotal = model(Step.TOTAL)
        runCurrent()

        val picker = vmTotal.pickTotal()
        assertEquals("", picker.shown?.value)
        assertEquals(
            UiText.Res(RecordbookStrings.sheet_scores_reason_empty, listOf(UiText.Dynamic("Итого"))),
            picker.note,
        )

        // Activity sum chip is present in candidates
        val activityCandidate = picker.candidates.single { it.headerPath == SheetTotals.ACTIVITIES_SUM_PATH }
        assertEquals("3", activityCandidate.value)
        assertEquals(
            UiText.Res(RecordbookStrings.sheet_scores_chip, listOf(UiText.Dynamic("Сумма работ"), "3")),
            picker.chip(activityCandidate),
        )

        // Picking the activity chip saves as MANUAL
        vmTotal.pickTotal(activityCandidate)
        runCurrent()

        assertEquals(SheetScoresUiState.Done, vmTotal.uiState.value)
        val changed = repository.totals.single()
        assertEquals(SheetTotals.ACTIVITIES_SUM_PATH, changed.total.headerPath)
        assertEquals("3", changed.total.value)
        assertEquals(TotalChoice.MANUAL, changed.choice)

        // Reviewing with MANUAL activity sum shows activity note and allows reset
        repository.scores.value = listOf(
            sheetScore(
                scope = scope,
                url = TEST_SHEET_URL,
                tabGid = matanTab.gid,
                tabName = matanTab.name,
                rowKey = "100004",
                column = SheetColumnRef(SheetTotals.ACTIVITIES_SUM_PATH, 24),
                value = "3",
            ).copy(choice = TotalChoice.MANUAL)
        )
        repository.inspections += ready(book, SheetRows.find(book, student))
        val vmManual = model(Step.TOTAL)
        runCurrent()

        val manualPicker = vmManual.pickTotal()
        assertEquals("3", manualPicker.shown?.value)
        assertEquals(SheetTotals.ACTIVITIES_SUM_PATH, manualPicker.shown?.headerPath)
        assertEquals(
            UiText.Res(RecordbookStrings.sheet_scores_reason_activity_sum),
            manualPicker.note,
        )
        assertTrue(manualPicker.unsure)
        assertTrue(manualPicker.canReset)
    }
}
