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
import dev.alllexey.itmowidgets.feature.recordbook.FakeSheetRulesRepository
import dev.alllexey.itmowidgets.feature.recordbook.FakeSheetScoresRepository
import dev.alllexey.itmowidgets.feature.recordbook.MainDispatcherRule
import dev.alllexey.itmowidgets.feature.recordbook.TEST_IDENTITY
import dev.alllexey.itmowidgets.feature.recordbook.TEST_SHEET_URL
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.BlankPolicy
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.CsvGrid
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.RowSearch
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetCell
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetCellRef
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetColumnRef
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetColumnStats
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetFormula
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetGrid
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetInspection
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetOp
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetRows
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetRule
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetRuleNode
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetRulePurpose
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetRuleResult
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetRuleRoute
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetRuleTab
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetStatus
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetTab
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetTabGrid
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetWorkbook
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.TotalChoice
import dev.alllexey.itmowidgets.feature.recordbook.sheetScore
import dev.alllexey.itmowidgets.feature.recordbook.testWorkbook
import dev.alllexey.itmowidgets.shared.feature.recordbook.RecordbookStrings
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceTimeBy
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
    private val rules = FakeSheetRulesRepository()
    private val grades = SheetTab(22, "P3110")
    private val linkId = "00000000-0000-0000-0000-000000000042"

    private fun model(step: Step = Step.CONNECT, link: String? = null) = SheetScoresViewModel(
        SavedStateHandle(
            listOfNotNull(
                SheetScoresArgs.SUBJECT_ID to scope.subjectId,
                SheetScoresArgs.SUBJECT_NAME to scope.subjectName,
                SheetScoresArgs.PERIOD_KEY to scope.periodKey,
                SheetScoresArgs.URL to TEST_SHEET_URL,
                SheetScoresArgs.STEP to step.name,
                link?.let { SheetScoresArgs.LINK_ID to it },
            ).toMap()
        ),
        repository,
        rules,
    )

    private fun ref(column: Int, path: String, gid: Long = 22) = SheetCellRef(gid, SheetColumnRef(path, column))

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
        assertEquals(ref(11, "ИТОГО баллов"), connected.column)
        assertEquals("66,3", connected.value)
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
        assertTrue(repository.connected.isEmpty())
        assertEquals(picker.cells.last(), vm.pickTotal().picked)
        assertTrue(vm.pickTotal().canSave)
        vm.save()
        runCurrent()
        assertEquals(SheetScoresUiState.Done, vm.uiState.value)
        assertEquals("9", repository.connected.single().value)
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
        assertEquals("66,3", repository.connected.single().value)
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
        assertFalse(picker.canSave)
        vm.pickTotal(grade)
        vm.save()
        runCurrent()
        assertEquals(SheetScoresUiState.Done, vm.uiState.value)
        assertEquals(ref(12, grade.headerPath), repository.totals.single().column)
        assertEquals(TotalChoice.MANUAL, repository.totals.single().choice)
        assertTrue(repository.connected.isEmpty())
    }

    @Test fun `the automatic total picked again is confirmed`() = runTest(main.dispatcher) {
        repository.scores.value = listOf(sheetScore(scope = scope).copy(choice = TotalChoice.AUTO_CHECK))
        repository.inspections += ready(testWorkbook())
        val vm = model(Step.TOTAL)
        runCurrent()

        vm.pickTotal(vm.pickTotal().decision!!.total)
        assertTrue(vm.pickTotal().canSave)
        vm.save()
        runCurrent()

        assertEquals(TotalChoice.AUTO, repository.totals.single().choice)
    }

    @Test fun `«Вернуть авто» selects the automatic total instead of a hand-picked one`() = runTest(main.dispatcher) {
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
        assertEquals(picker.decision?.total, vm.pickTotal().picked)
        assertFalse(vm.pickTotal().canReset)
        vm.save()
        runCurrent()

        assertEquals(ref(11, "ИТОГО баллов"), repository.totals.single().column)
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
        val picked = vm.pickTotal()
        vm.save()
        runCurrent()
        assertEquals(SheetScoresEvent.SaveFailed(AppError.Unknown().toUiText()), vm.events.first())
        assertEquals(picked, vm.uiState.value)

        repository.connectResult = AppResult.Success(Unit)
        val gate = CompletableDeferred<Unit>()
        repository.connectGate = gate
        vm.save()
        vm.pickTotal(picker.cells.last())
        vm.save()
        runCurrent()
        gate.complete(Unit)
        runCurrent()
        assertEquals(2, repository.connected.size)
        assertEquals(picker.cells.first().column, repository.connected.last().column?.column?.index)
        assertEquals(SheetScoresUiState.Done, vm.uiState.value)
    }

    @Test fun `the sum mode adds the picked columns live and saves a sum`() = runTest(main.dispatcher) {
        repository.scores.value = listOf(sheetScore(scope = scope))
        repository.inspections += ready(testWorkbook())
        val vm = model(Step.TOTAL)
        runCurrent()
        val cells = vm.pickTotal().cells.filter { it.tab == grades }
        val first = cells.single { it.column == 3 }
        val lab = cells.single { it.column == 10 }

        vm.setSumming(true)
        vm.pickTotal(first)
        assertEquals("2", vm.pickTotal().sum)
        assertFalse(vm.pickTotal().canSave)
        vm.pickTotal(lab)
        assertEquals("14", vm.pickTotal().sum)
        vm.pickTotal(first)
        assertEquals(listOf(lab), vm.pickTotal().summed)
        vm.pickTotal(first)
        assertTrue(repository.totals.isEmpty())
        vm.save()
        runCurrent()

        val saved = repository.totals.single()
        assertEquals(TotalChoice.MANUAL, saved.choice)
        assertEquals("14", saved.value)
        assertEquals(SheetFormula.sum(listOf(ref(10, lab.headerPath), ref(3, first.headerPath))), saved.pick.formula)
    }

    @Test fun `the column list summarises every column and folds the empty ones`() = runTest(main.dispatcher) {
        repository.scores.value = listOf(sheetScore(scope = scope))
        repository.inspections += ready(testWorkbook())
        val vm = model(Step.TOTAL)
        runCurrent()

        val picker = vm.pickTotal()
        val total = picker.filledColumns(grades).single { it.cell.column == 11 }
        assertEquals(SheetColumnStats(filled = 4, students = 4, max = 66.3), total.stats)
        assertEquals(listOf(grades, SheetTab(11, "All")), picker.tabs)
        assertTrue(picker.emptyColumns(grades).isEmpty())
    }

    @Test fun `no AI offer without a shared link or with the services off`() = runTest(main.dispatcher) {
        repository.scores.value = listOf(sheetScore(scope = scope))
        repository.inspections += ready(testWorkbook())
        repository.inspections += ready(testWorkbook())
        assertEquals(SheetAiState.Hidden, model(Step.TOTAL).also { runCurrent() }.pickTotal().ai)

        rules.available = false
        val off = model(Step.TOTAL, link = linkId)
        runCurrent()

        assertEquals(SheetAiState.Hidden, off.pickTotal().ai)
        off.analyze()
        runCurrent()
        assertTrue(rules.requests.isEmpty())
    }

    @Test fun `a shared link offers AI and a pending analysis is polled until its rule is proposed`() = runTest(main.dispatcher) {
        repository.scores.value = listOf(sheetScore(scope = scope))
        repository.inspections += ready(testWorkbook())
        rules.states += SheetRuleResult.Pending
        rules.states += SheetRuleResult.Ready(rule)
        val vm = model(Step.TOTAL, link = linkId)
        runCurrent()
        assertEquals(SheetAiState.Offer, vm.pickTotal().ai)

        vm.analyze()
        runCurrent()
        assertEquals(SheetAiState.Running, vm.pickTotal().ai)
        assertEquals(listOf(linkId), rules.requests)
        advanceTimeBy(9.seconds)
        runCurrent()

        val proposal = vm.pickTotal().ai as SheetAiState.Proposal
        assertEquals(2, rules.polls)
        assertEquals("18", proposal.value)
        assertEquals("66,3", proposal.official)
        assertEquals("Сумма работ", proposal.summary)
        assertEquals(listOf("Итог может быть 0"), proposal.warnings)
        assertTrue(repository.totals.isEmpty())

        vm.useAi()
        runCurrent()
        val saved = repository.totals.single()
        assertEquals(TotalChoice.AI, saved.choice)
        assertEquals("18", saved.value)
        assertEquals("66,3", saved.pick.reading.official)
        assertEquals(SheetFormula.Direct(ref(11, "ИТОГО баллов")), saved.pick.official)
        assertEquals(linkId, saved.linkId)
        assertEquals(SheetScoresUiState.Done, vm.uiState.value)
    }

    @Test fun `«Нет» drops the proposal and saves nothing`() = runTest(main.dispatcher) {
        repository.scores.value = listOf(sheetScore(scope = scope))
        repository.inspections += ready(testWorkbook())
        rules.requestResult = SheetRuleResult.Ready(rule)
        val vm = model(Step.TOTAL, link = linkId)
        runCurrent()

        vm.analyze()
        runCurrent()
        assertTrue(vm.pickTotal().ai is SheetAiState.Proposal)
        vm.dismissAi()
        vm.useAi()
        runCurrent()

        assertEquals(SheetAiState.Hidden, vm.pickTotal().ai)
        assertTrue(repository.totals.isEmpty())
    }

    @Test fun `a failed, limited or offline analysis says why and the manual pick stays`() = runTest(main.dispatcher) {
        val cases = mapOf(
            SheetRuleResult.Failed to RecordbookStrings.sheet_ai_failed,
            SheetRuleResult.Insufficient to RecordbookStrings.sheet_ai_insufficient,
            SheetRuleResult.RateLimited to RecordbookStrings.sheet_ai_rate_limited,
            SheetRuleResult.Unavailable to RecordbookStrings.sheet_ai_unavailable,
            SheetRuleResult.Offline to RecordbookStrings.sheet_scores_offline,
        )
        for ((result, text) in cases) {
            repository.scores.value = listOf(sheetScore(scope = scope))
            repository.inspections += ready(testWorkbook())
            rules.requestResult = result
            val vm = model(Step.TOTAL, link = linkId)
            runCurrent()

            vm.analyze()
            runCurrent()

            assertEquals(SheetAiState.Failed(UiText.Res(text)), vm.pickTotal().ai)
            vm.pickTotal(vm.pickTotal().cells.first { it.column == 12 })
            vm.save()
            runCurrent()
            assertEquals(SheetScoresUiState.Done, vm.uiState.value)
        }
    }

    @Test fun `an analysis still pending after three minutes stops polling`() = runTest(main.dispatcher) {
        repository.scores.value = listOf(sheetScore(scope = scope))
        repository.inspections += ready(testWorkbook())
        val vm = model(Step.TOTAL, link = linkId)
        runCurrent()

        vm.analyze()
        advanceTimeBy(10.minutes)
        runCurrent()

        assertEquals(SheetAiState.Failed(UiText.Res(RecordbookStrings.sheet_ai_timeout)), vm.pickTotal().ai)
        assertEquals(45, rules.polls)
    }

    @Test fun `a rule with a route per group tab proposes the route of the own tab`() = runTest(main.dispatcher) {
        repository.scores.value = listOf(sheetScore(scope = scope))
        repository.inspections += ready(testWorkbook())
        val other = SheetRuleTab("Потеряшки", 999)
        val otherRoute = rule.routes.first().copy(tab = other, node = SheetRuleNode.Cell(other, 3))
        rules.requestResult = SheetRuleResult.Ready(rule.copy(routes = listOf(otherRoute) + rule.routes))
        val vm = model(Step.TOTAL, link = linkId)
        runCurrent()

        vm.analyze()
        runCurrent()

        val proposal = vm.pickTotal().ai as SheetAiState.Proposal
        assertEquals("18", proposal.value)
        assertEquals("66,3", proposal.official)
    }

    @Test fun `a rule naming a tab this sheet lacks is no proposal`() = runTest(main.dispatcher) {
        repository.scores.value = listOf(sheetScore(scope = scope))
        repository.inspections += ready(testWorkbook())
        val elsewhere = SheetRuleTab("Нет такого", 999)
        rules.requestResult = SheetRuleResult.Ready(
            rule.copy(routes = listOf(rule.routes.first().copy(tab = elsewhere, node = SheetRuleNode.Cell(elsewhere, 3)))),
        )
        val vm = model(Step.TOTAL, link = linkId)
        runCurrent()

        vm.analyze()
        runCurrent()

        assertEquals(SheetAiState.Failed(UiText.Res(RecordbookStrings.sheet_ai_not_applicable)), vm.pickTotal().ai)
    }

    @Test fun `a saved computed total shows in words with its value`() = runTest(main.dispatcher) {
        val sum = SheetFormula.sum(listOf(ref(3, "x"), ref(10, "КР Итог")))
        repository.scores.value = listOf(sheetScore(scope = scope).copy(formula = sum, choice = TotalChoice.MANUAL, linkId = linkId))
        repository.inspections += ready(testWorkbook())
        val vm = model(Step.TOTAL)
        runCurrent()

        val picker = vm.pickTotal()
        assertNull(picker.selected)
        assertNull(picker.shown)
        assertEquals("14", picker.saved?.value)
        assertEquals(SheetAiState.Offer, picker.ai)
    }

    private val rule: SheetRule by lazy {
        val tab = SheetRuleTab("P3110", 22)
        SheetRule(
            summary = "Сумма работ",
            routes = listOf(
                SheetRuleRoute(
                    SheetRulePurpose.CURRENT, tab, direct = false,
                    node = SheetRuleNode.Apply(SheetOp.SUM, listOf(3, 4, 5, 10).map { SheetRuleNode.Cell(tab, it) }),
                    blanks = BlankPolicy.IGNORE_BLANK_INPUTS, explanation = "Работы", warnings = emptyList(),
                ),
                SheetRuleRoute(
                    SheetRulePurpose.OFFICIAL, SheetRuleTab("P3110", null), direct = true,
                    node = SheetRuleNode.Cell(SheetRuleTab("P3110", null), 11),
                    blanks = BlankPolicy.PRESERVE, explanation = "Итог", warnings = listOf("Итог может быть 0"),
                ),
            ),
        )
    }
}
