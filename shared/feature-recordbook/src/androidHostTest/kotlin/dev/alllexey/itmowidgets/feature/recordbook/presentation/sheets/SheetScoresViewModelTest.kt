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
        assertNull(picker.selection)
        assertNull(picker.hero)
        assertNull(picker.action)
        assertEquals(listOf(2 to "8", 4 to "9"), picker.cells.filter { it.value.isNotEmpty() }.map { it.column to it.value })
        assertNull(picker.decision)
        vm.openColumns()
        assertEquals(TotalPage.COLUMNS, vm.pickTotal().page)
        vm.pickColumn(picker.cells.last())
        runCurrent()
        assertTrue(repository.connected.isEmpty())
        assertEquals(TotalOption.Column(picker.cells.last()), vm.pickTotal().selection)
        assertEquals(TotalPage.OVERVIEW, vm.pickTotal().page)
        assertEquals(TotalAction.SAVE, vm.pickTotal().action)
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

    @Test fun `the current total is on top, done closes and another one is saved as a change`() = runTest(main.dispatcher) {
        repository.scores.value = listOf(sheetScore(scope = scope))
        repository.inspections += ready(testWorkbook())

        val vm = model(Step.TOTAL)
        runCurrent()

        val picker = vm.pickTotal()
        val total = TotalOption.Column(SheetCell(grades, 11, "ИТОГО баллов", "66,3"))
        assertEquals(total, picker.current)
        assertEquals(total, picker.selection)
        assertEquals("66,3", picker.hero?.value)
        assertNull(picker.hero?.note)
        assertTrue(picker.variants.none { it.option == total })
        assertEquals(TotalAction.DONE, picker.action)
        assertTrue(picker.cells.any { it.tab.gid == 11L })
        val grade = picker.cells.single { it.tab == grades && it.column == 12 }
        vm.select(TotalOption.Column(grade))
        assertEquals(TotalAction.SAVE, vm.pickTotal().action)
        vm.select(total)
        assertEquals(TotalAction.DONE, vm.pickTotal().action)
        vm.select(TotalOption.Column(grade))
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

        assertEquals(TotalOption.Column(vm.pickTotal().decision!!.total), vm.pickTotal().selection)
        assertEquals(TotalAction.SAVE, vm.pickTotal().action)
        vm.save()
        runCurrent()

        assertEquals(TotalChoice.AUTO, repository.totals.single().choice)
    }

    @Test fun `the automatic total stays a variant beside a hand-picked one and is saved as automatic`() = runTest(main.dispatcher) {
        val grade = SheetColumnRef("Оценка · 60-100", 12)
        repository.scores.value = listOf(sheetScore(scope = scope, column = grade).copy(choice = TotalChoice.MANUAL))
        repository.inspections += ready(testWorkbook())
        val vm = model(Step.TOTAL)
        runCurrent()
        val picker = vm.pickTotal()
        assertEquals(12, (picker.selection as TotalOption.Column).cell.column)
        val auto = picker.variants.first()
        assertEquals(TotalOption.Column(picker.decision!!.total), auto.option)
        assertEquals(UiText.Res(RecordbookStrings.sheet_total_auto), auto.subtitle)

        vm.select(auto.option)
        assertEquals(TotalAction.SAVE, vm.pickTotal().action)
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

        vm.pickColumn(picker.cells.first())
        val picked = vm.pickTotal()
        vm.save()
        runCurrent()
        assertEquals(SheetScoresEvent.SaveFailed(AppError.Unknown().toUiText()), vm.events.first())
        assertEquals(picked, vm.uiState.value)

        repository.connectResult = AppResult.Success(Unit)
        val gate = CompletableDeferred<Unit>()
        repository.connectGate = gate
        vm.save()
        vm.pickColumn(picker.cells.last())
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

        vm.openSum()
        assertEquals(TotalAction.FINISH_SUM, vm.pickTotal().action)
        vm.toggleSum(first)
        assertEquals("2", vm.pickTotal().sum)
        assertFalse(vm.pickTotal().canFinishSum)
        vm.toggleSum(lab)
        assertEquals("14", vm.pickTotal().sum)
        vm.toggleSum(first)
        assertEquals(listOf(lab), vm.pickTotal().summed)
        vm.toggleSum(first)
        vm.finishSum()
        val made = vm.pickTotal()
        assertEquals(TotalPage.OVERVIEW, made.page)
        assertEquals("14", made.hero?.value)
        assertTrue(made.selection is TotalOption.Sum)
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
        // Only points can be added: not the group, a percentage, a grade or a «Y».
        assertEquals(listOf(3, 4, 5, 6, 10, 11), picker.sumColumns(grades).map { it.cell.column })
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
        assertEquals(TotalOption.Ai, vm.pickTotal().selection)
        assertEquals("18", vm.pickTotal().hero?.value)
        assertEquals(UiText.Res(RecordbookStrings.sheet_scores_official, listOf("66,3")), vm.pickTotal().hero?.official)
        assertEquals(TotalAction.SAVE, vm.pickTotal().action)
        assertTrue(repository.totals.isEmpty())

        vm.save()
        runCurrent()
        val saved = repository.totals.single()
        assertEquals(TotalChoice.AI, saved.choice)
        assertEquals("18", saved.value)
        assertEquals("66,3", saved.pick.reading.official)
        assertEquals(SheetFormula.Direct(ref(11, "ИТОГО баллов")), saved.pick.official)
        assertEquals(linkId, saved.linkId)
        assertEquals(SheetScoresUiState.Done, vm.uiState.value)
    }

    @Test fun `another variant over the AI result saves nothing of it`() = runTest(main.dispatcher) {
        repository.scores.value = listOf(sheetScore(scope = scope))
        repository.inspections += ready(testWorkbook())
        rules.requestResult = SheetRuleResult.Ready(rule)
        val vm = model(Step.TOTAL, link = linkId)
        runCurrent()

        vm.analyze()
        runCurrent()
        assertTrue(vm.pickTotal().ai is SheetAiState.Proposal)
        assertEquals(TotalOption.Ai, vm.pickTotal().selection)
        vm.select(vm.pickTotal().current!!)
        assertTrue(vm.pickTotal().variants.any { it.option == TotalOption.Ai })
        vm.save()
        runCurrent()

        assertEquals(SheetScoresUiState.Done, vm.uiState.value)
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
            vm.select(TotalOption.Column(vm.pickTotal().cells.first { it.column == 12 }))
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
        assertEquals(TotalOption.Saved, picker.selection)
        assertEquals(TotalOption.Saved, picker.current)
        assertEquals("14", picker.hero?.value)
        assertEquals(TotalAction.DONE, picker.action)
        assertEquals(SheetAiState.Offer, picker.ai)
        // The hand-made sum opens again on the sum page.
        assertEquals(listOf(3, 10), picker.summed.map { it.column })
        assertEquals("14", picker.sum)
    }

    @Test fun `back leaves a column page and a tab's rows`() = runTest(main.dispatcher) {
        repository.scores.value = listOf(sheetScore(scope = scope))
        repository.inspections += ready(testWorkbook())
        val vm = model(Step.TOTAL)
        runCurrent()
        assertFalse(vm.uiState.value.canGoBack)
        vm.openColumns()
        assertTrue(vm.uiState.value.canGoBack)
        vm.back()
        assertEquals(TotalPage.OVERVIEW, vm.pickTotal().page)

        repository.inspections += ready(testWorkbook(grades to "grades_multiheader.csv"), RowSearch.NotFound)
        val connecting = model()
        runCurrent()
        connecting.pickTab(grades)
        assertTrue(connecting.uiState.value.canGoBack)
        connecting.back()
        assertEquals(SheetScoresUiState.PickTab(listOf(grades)), connecting.uiState.value)
    }

    @Test fun `an unsure automatic total says what to check`() = runTest(main.dispatcher) {
        val grid = CsvGrid.parse("ИСУ,ФИО,ЛР1,Score\n123456,Тестов Тест Тестович,5,5")
        repository.scores.value = listOf(sheetScore(scope = scope, column = SheetColumnRef("Score", 3)).copy(choice = TotalChoice.AUTO_CHECK))
        repository.inspections += ready(SheetWorkbook(testWorkbook().url, listOf(SheetTabGrid(grades, grid))))
        val vm = model(Step.TOTAL)
        runCurrent()

        val hero = vm.pickTotal().hero!!
        assertEquals(UiText.Res(RecordbookStrings.sheet_total_check_weak), hero.note)
        assertTrue(hero.warning)
        assertEquals(TotalAction.SAVE, vm.pickTotal().action)
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
