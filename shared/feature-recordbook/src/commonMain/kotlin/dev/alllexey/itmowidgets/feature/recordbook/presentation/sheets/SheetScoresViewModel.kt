package dev.alllexey.itmowidgets.feature.recordbook.presentation.sheets

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.alllexey.itmowidgets.core.navigation.SheetScoresArgs
import dev.alllexey.itmowidgets.core.navigation.SheetScoresArgs.Step
import dev.alllexey.itmowidgets.core.presentation.EventQueue
import dev.alllexey.itmowidgets.core.presentation.RefreshMode
import dev.alllexey.itmowidgets.core.resources.ResourceScope
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.core.text.toUiText
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.RowSearch
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetCell
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetCellRef
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetColumnRef
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetColumns
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetFormula
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetFormulas
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetHeaders
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetInspection
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetReading
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetRowMatch
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetRows
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetRule
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetRuleResult
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetRulesRepository
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetScore
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetScoreRules
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetScoresRepository
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetStatus
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetTab
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetTabGrid
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetTotalDecision
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetTotalPick
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetTotals
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetWorkbook
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.TotalChoice
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.TotalConfidence
import dev.alllexey.itmowidgets.shared.feature.recordbook.RecordbookStrings
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Connects the own total of a public Google Sheet, or picks another total of the connected one: a cell, a sum of
 * columns, or Backend's AI rule for a sheet shared with a flow or everybody. The downloaded workbook lives only here,
 * never in the saved state: after process death the sheet is downloaded again.
 */
class SheetScoresViewModel(
    handle: SavedStateHandle,
    private val repository: SheetScoresRepository,
    private val rules: SheetRulesRepository,
) : ViewModel() {
    val scope = ResourceScope(
        checkNotNull(handle[SheetScoresArgs.SUBJECT_ID]),
        checkNotNull(handle[SheetScoresArgs.SUBJECT_NAME]),
        checkNotNull(handle[SheetScoresArgs.PERIOD_KEY]),
    )
    private val url: String = checkNotNull(handle[SheetScoresArgs.URL])
    val step: Step = Step.valueOf(checkNotNull(handle[SheetScoresArgs.STEP]))
    private var linkId: String? = handle[SheetScoresArgs.LINK_ID]

    private val _state = MutableStateFlow<SheetScoresUiState>(SheetScoresUiState.Loading)
    val uiState: StateFlow<SheetScoresUiState> = _state.asStateFlow()
    private val eventQueue = EventQueue<SheetScoresEvent>()
    val events: Flow<SheetScoresEvent> = eventQueue.events

    private var workbook: SheetWorkbook? = null
    /** The own row in every tab where it is, for the cells being picked from. */
    private var matches: List<SheetRowMatch> = emptyList()
    /** One action at a time: a second tap while one runs is ignored. */
    private var job: Job? = null
    /** The AI request and its polling; it runs beside the manual pick and stops with the view model. */
    private var aiJob: Job? = null
    /** The AI rule's own row and totals over this workbook while its proposal shows. */
    private var proposal: Pair<SheetRowMatch, SheetTotalPick>? = null

    init {
        start()
    }

    /**
     * Downloads the sheet again (the retry button; every mode does the same). Ignored while an action runs: sheet
     * actions go one at a time, so a refresh never replaces a pick or a save in flight.
     */
    fun refresh(mode: RefreshMode) {
        if (job?.isActive == true) return
        start()
    }

    fun pickRow(match: SheetRowMatch) = act {
        val book = workbook ?: return@act
        val others = book.tabs.filter { it.tab != match.tab }
            .mapNotNull { SheetRows.locate(it, match.key, match.kind, match.keyColumn) }
        choose(book, listOf(match) + others)
    }

    fun pickTab(tab: SheetTab) {
        if (job?.isActive == true) return
        val grid = workbook?.tabs?.firstOrNull { it.tab == tab } ?: return
        _state.value = SheetScoresUiState.PickTabRow(tab, SheetRows.options(grid))
    }

    /** A variant of the overview becomes the total on top; nothing is saved before [save]. */
    fun select(option: TotalOption) = review { it.copy(selection = option) }

    /** `Выбрать столбец`: every column of the own row. */
    fun openColumns() = review { it.copy(page = TotalPage.COLUMNS) }

    /** `Сложить столбцы`: the columns with points to add up. */
    fun openSum() = review { it.copy(page = TotalPage.SUM) }

    /** A column of the list becomes the total on top and the overview returns. */
    fun pickColumn(cell: SheetCell) = review {
        it.copy(selection = TotalOption.Column(cell), picked = cell, page = TotalPage.OVERVIEW)
    }

    /** Adds [cell] to the sum being made or takes it out; the own sum follows live. */
    fun toggleSum(cell: SheetCell) = review { review ->
        review.withSum(if (cell in review.summed) review.summed - cell else review.summed + cell)
    }

    /** `Готово` of the sum page: the sum becomes the total on top and the overview returns. */
    fun finishSum() = review { review ->
        val text = review.sumText
        if (!review.canFinishSum || text == null) return@review review
        val made = TotalOption.Sum(review.summed, review.sum, text)
        review.copy(selection = made, made = made, page = TotalPage.OVERVIEW)
    }

    /** Back inside the sheet ([canGoBack]): a column page to the overview, a tab's rows to the tabs. */
    fun back() {
        if (job?.isActive == true) return
        _state.update { state ->
            when {
                state is SheetScoresUiState.PickTotal && state.page != TotalPage.OVERVIEW -> state.copy(page = TotalPage.OVERVIEW)
                state is SheetScoresUiState.PickTabRow -> workbook?.let { SheetScoresUiState.PickTab(tabsWithStudents(it)) } ?: state
                else -> state
            }
        }
    }

    /** The footer's button: saves the total on top, or closes when it is what is saved already. */
    fun save() = act {
        val review = _state.value as? SheetScoresUiState.PickTotal ?: return@act
        when (review.action) {
            TotalAction.DONE -> _state.value = SheetScoresUiState.Done
            TotalAction.SAVE -> when (val option = review.selection) {
                is TotalOption.Column -> {
                    val row = matches.firstOrNull { it.tab == option.cell.tab } ?: return@act
                    val choice = if (option.cell == review.decision?.total) TotalChoice.AUTO else TotalChoice.MANUAL
                    save(row, directPick(option.cell, choice), fallback = review)
                }
                is TotalOption.Sum -> {
                    val row = matches.firstOrNull { it.tab == option.cells.first().tab } ?: return@act
                    val formula = SheetFormula.sum(option.cells.map(::refOf))
                    save(row, SheetTotalPick(formula, TotalChoice.MANUAL, reading(formula)), fallback = review)
                }
                TotalOption.Ai -> proposal?.let { (row, pick) -> save(row, pick, fallback = review) }
                TotalOption.Saved, null -> Unit
            }
            TotalAction.FINISH_SUM, null -> Unit
        }
    }

    /** `Посчитать с ИИ`: asks Backend, then polls until the rule is ready, it fails or [AI_TIMEOUT] passes. */
    fun analyze() {
        val link = linkId ?: return
        val review = _state.value as? SheetScoresUiState.PickTotal ?: return
        if (aiJob?.isActive == true || (review.ai !is SheetAiState.Offer && review.ai !is SheetAiState.Failed)) return
        setAi(SheetAiState.Running)
        aiJob = viewModelScope.launch {
            var result = rules.request(link)
            var waited = 0.seconds
            while (result == SheetRuleResult.Pending && waited < AI_TIMEOUT) {
                delay(AI_POLL)
                waited += AI_POLL
                result = rules.state(link)
            }
            setAi(outcome(result))
        }
    }

    private fun start() = act {
        _state.value = SheetScoresUiState.Loading
        when (step) {
            Step.CONNECT -> connect()
            Step.TOTAL -> changeTotal()
        }
    }

    private fun act(block: suspend () -> Unit) {
        if (job?.isActive == true) return
        job = viewModelScope.launch { block() }
    }

    /** Changes the review while no action runs. */
    private fun review(change: (SheetScoresUiState.PickTotal) -> SheetScoresUiState.PickTotal) {
        if (job?.isActive == true) return
        _state.update { if (it is SheetScoresUiState.PickTotal) change(it) else it }
    }

    /** A ready AI result goes on top at once: the viewer asked for it. */
    private fun setAi(ai: SheetAiState) = _state.update {
        if (it !is SheetScoresUiState.PickTotal) return@update it
        it.copy(ai = ai, selection = if (ai is SheetAiState.Proposal) TotalOption.Ai else it.selection)
    }

    private fun SheetScoresUiState.PickTotal.withSum(summed: List<SheetCell>) = copy(
        summed = summed,
        sum = sumOf(summed),
        sumText = summed.takeIf { it.isNotEmpty() }?.let { SheetFormulaText.describe(SheetFormula.sum(it.map(::refOf))) },
    )

    private fun tabsWithStudents(book: SheetWorkbook): List<SheetTab> =
        book.tabs.filter { SheetRows.options(it).isNotEmpty() }.map { it.tab }

    private suspend fun connect() {
        val ready = when (val inspection = repository.inspect(url)) {
            is SheetInspection.Failed -> return fail(inspection.status)
            is SheetInspection.Ready -> inspection
        }
        val book = ready.workbook.also { workbook = it }
        when (val search = ready.search) {
            is RowSearch.Found -> choose(book, search.matches)
            is RowSearch.Ambiguous -> _state.value = SheetScoresUiState.PickRow(search.candidates)
            RowSearch.NotFound -> _state.value = SheetScoresUiState.PickTab(tabsWithStudents(book))
        }
    }

    private suspend fun changeTotal() {
        val score = repository.observe().first().firstOrNull { it.scope.key == scope.key }
            ?: return run { _state.value = SheetScoresUiState.Done }
        if (linkId == null) linkId = score.linkId
        val book = when (val inspection = repository.inspect(score.url)) {
            is SheetInspection.Failed -> return fail(inspection.status)
            is SheetInspection.Ready -> inspection.workbook.also { workbook = it }
        }
        matches = book.tabs.mapNotNull { SheetRows.locate(it, score.rowKey, score.keyKind, score.keyColumn) }
        if (matches.isEmpty()) return fail(SheetStatus.ROW_NOT_FOUND)
        val cells = cellsOf(book, matches)
        val computed = score.formula as? SheetFormula.Computed
        val saved = computed?.let {
            val reading = reading(it, score.official)
            SheetSavedTotal(SheetFormulaText.describe(it), reading.value, reading.official)
        }
        val current = current(book, score, cells)?.let(TotalOption::Column) ?: saved?.let { TotalOption.Saved }
        // A hand-made sum opens its columns again on the sum page.
        val summed = computed?.takeIf { score.choice == TotalChoice.MANUAL }?.cells.orEmpty()
            .mapNotNull { ref ->
                val tab = cells.filter { it.tab.gid == ref.gid }
                tab.firstOrNull { it.headerPath == ref.column.headerPath } ?: tab.firstOrNull { it.column == ref.column.index }
            }
        _state.value = review(book, cells, current, decide(book, matches), score.choice, saved).withSum(summed)
    }

    /** The cells of [rows]; an automatic total is saved at once, otherwise the viewer picks it. */
    private suspend fun choose(book: SheetWorkbook, rows: List<SheetRowMatch>) {
        matches = rows
        val cells = cellsOf(book, rows)
        val decision = decide(book, rows)
        if (decision == null || step == Step.TOTAL) {
            _state.value = review(book, cells, null, decision, null, null)
            return
        }
        val row = rows.first { it.tab == decision.total.tab }
        save(row, directPick(decision.total, autoChoice(decision)), fallback = review(book, cells, null, decision, null, null))
    }

    private suspend fun review(
        book: SheetWorkbook,
        cells: List<SheetCell>,
        current: TotalOption?,
        decision: SheetTotalDecision?,
        choice: TotalChoice?,
        saved: SheetSavedTotal?,
    ) = SheetScoresUiState.PickTotal(
        cells = cells,
        selection = current ?: decision?.total?.let(TotalOption::Column),
        current = current,
        decision = decision,
        choice = choice,
        saved = saved,
        columns = columnsOf(book, cells),
        ai = if (linkId != null && rules.available()) SheetAiState.Offer else SheetAiState.Hidden,
    )

    private fun decide(book: SheetWorkbook, rows: List<SheetRowMatch>): SheetTotalDecision? =
        SheetTotals.decide(book.tabs.filter { tab -> rows.any { it.tab == tab.tab } }, rows)

    private fun autoChoice(decision: SheetTotalDecision): TotalChoice =
        if (decision.confidence == TotalConfidence.LOW) TotalChoice.AUTO_CHECK else TotalChoice.AUTO

    /** One cell as the total, its value as the sheet shows it. */
    private fun directPick(cell: SheetCell, choice: TotalChoice) =
        SheetTotalPick(SheetFormula.Direct(refOf(cell)), choice, SheetReading(cell.value.ifEmpty { null }, SheetStatus.OK))

    /** A failed write keeps [fallback] on screen and says so. */
    private suspend fun save(row: SheetRowMatch, total: SheetTotalPick, fallback: SheetScoresUiState) {
        val result = when (step) {
            Step.CONNECT -> repository.connect(scope, url, row, total, linkId)
            Step.TOTAL -> repository.changeTotal(scope, row, total, linkId)
        }
        when (result) {
            is AppResult.Success -> _state.value = SheetScoresUiState.Done
            is AppResult.Failure -> {
                _state.value = fallback
                eventQueue.send(SheetScoresEvent.SaveFailed(result.error.toUiText()))
            }
        }
    }

    private fun outcome(result: SheetRuleResult): SheetAiState = when (result) {
        is SheetRuleResult.Ready -> propose(result.rule)
        SheetRuleResult.Pending -> failed(RecordbookStrings.sheet_ai_timeout)
        SheetRuleResult.Insufficient -> failed(RecordbookStrings.sheet_ai_insufficient)
        SheetRuleResult.Failed -> failed(RecordbookStrings.sheet_ai_failed)
        SheetRuleResult.Unavailable -> failed(RecordbookStrings.sheet_ai_unavailable)
        SheetRuleResult.RateLimited -> failed(RecordbookStrings.sheet_ai_rate_limited)
        SheetRuleResult.Offline -> failed(RecordbookStrings.sheet_scores_offline)
    }

    private fun failed(key: String) = SheetAiState.Failed(UiText.Res(key))

    /** The rule over this workbook and the own row; a rule naming a tab, row or column that is not here is no proposal. */
    private fun propose(rule: SheetRule): SheetAiState {
        val book = workbook ?: return failed(RecordbookStrings.sheet_ai_not_applicable)
        val route = rule.mainFor(book, matches) ?: return failed(RecordbookStrings.sheet_ai_not_applicable)
        val row = matches.firstOrNull { it.tab == route.tabIn(book) } ?: return failed(RecordbookStrings.sheet_ai_not_applicable)
        val main = route.formula(book, matches) ?: return failed(RecordbookStrings.sheet_ai_not_applicable)
        val officialRoute = rule.officialFor(route, book)
        val official = officialRoute?.formula(book, matches)
        val reading = reading(main, official)
        if (reading.status != SheetStatus.OK) return failed(RecordbookStrings.sheet_ai_not_applicable)
        val summary = rule.summary.ifBlank { route.explanation }
        proposal = row to SheetTotalPick(main, TotalChoice.AI, reading, official, summary)
        return SheetAiState.Proposal(
            formula = SheetFormulaText.describe(main),
            value = reading.value,
            official = reading.official,
            hasOfficial = official != null,
            summary = summary,
            warnings = (route.warnings + officialRoute?.warnings.orEmpty()).distinct(),
        )
    }

    /** [formula] (and [official]) over the downloaded workbook, as a later reading of the connection would read it. */
    private fun reading(formula: SheetFormula, official: SheetFormula? = null): SheetReading {
        val book = workbook ?: return SheetReading(null, SheetStatus.ROW_NOT_FOUND)
        val own = matches.firstOrNull() ?: return SheetReading(null, SheetStatus.ROW_NOT_FOUND)
        val cells = SheetScoreRules.OwnCells(book.tabs.associate { it.tab.gid to it.grid }, own.key, own.kind, own.keyColumn)
        val main = SheetFormulas.evaluate(formula, cells::read)
        return main.copy(official = official?.let { SheetFormulas.evaluate(it, cells::read).value })
    }

    private fun sumOf(cells: List<SheetCell>): String? =
        if (cells.isEmpty()) null else reading(SheetFormula.sum(cells.map(::refOf))).value

    private fun refOf(cell: SheetCell) = SheetCellRef(cell.tab.gid, SheetColumnRef(cell.headerPath, cell.column))

    private fun cellsOf(book: SheetWorkbook, rows: List<SheetRowMatch>): List<SheetCell> =
        book.tabs.flatMap { tab -> rows.filter { it.tab == tab.tab }.flatMap { SheetTotals.cells(tab, it) } }

    private fun columnsOf(book: SheetWorkbook, cells: List<SheetCell>): List<SheetColumnOption> {
        val stats = book.tabs.mapNotNull { tab ->
            matches.firstOrNull { it.tab == tab.tab }?.let { tab.tab to SheetColumns.stats(tab, it) }
        }.toMap()
        return cells.mapNotNull { cell -> stats[cell.tab]?.getOrNull(cell.column)?.let { SheetColumnOption(cell, it) } }
    }

    /** The connected total in its tab, found by its header path as a reading would; null for a computed total. */
    private fun current(book: SheetWorkbook, score: SheetScore, cells: List<SheetCell>): SheetCell? {
        val ref = (score.formula as? SheetFormula.Direct)?.cell ?: return null
        val tab: SheetTabGrid = book.tabs.firstOrNull { it.tab.gid == ref.gid } ?: return null
        val row = matches.firstOrNull { it.tab == tab.tab } ?: return null
        val first = SheetHeaders.firstDataRow(tab.grid, row.row, row.keyColumn, row.kind)
        val column = SheetTotals.find(SheetHeaders.paths(tab.grid, first, row.keyColumn), ref.column, tab.grid.width)
        return cells.firstOrNull { it.tab == tab.tab && it.column == column }
    }

    private fun fail(status: SheetStatus) {
        _state.value = SheetScoresUiState.Failed(status)
    }

    private companion object {
        val AI_POLL = 4.seconds
        val AI_TIMEOUT = 3.minutes
    }
}
