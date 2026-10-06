package dev.alllexey.itmowidgets.feature.recordbook.presentation.sheets

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.alllexey.itmowidgets.core.navigation.SheetScoresArgs
import dev.alllexey.itmowidgets.core.navigation.SheetScoresArgs.Step
import dev.alllexey.itmowidgets.core.presentation.EventQueue
import dev.alllexey.itmowidgets.core.presentation.RefreshMode
import dev.alllexey.itmowidgets.core.resources.ResourceScope
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.text.toUiText
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.RowSearch
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetCell
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetHeaders
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetInspection
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetRowMatch
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetRows
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetScore
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetScoresRepository
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetStatus
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetTab
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetTabGrid
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetTotals
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetWorkbook
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Connects the own total of a public Google Sheet, or picks another total of the connected one. The downloaded
 * workbook lives only here, never in the saved state: after process death the sheet is downloaded again.
 */
@HiltViewModel
class SheetScoresViewModel @Inject constructor(
    handle: SavedStateHandle,
    private val repository: SheetScoresRepository,
) : ViewModel() {
    val scope = ResourceScope(
        checkNotNull(handle[SheetScoresArgs.SUBJECT_ID]),
        checkNotNull(handle[SheetScoresArgs.SUBJECT_NAME]),
        checkNotNull(handle[SheetScoresArgs.PERIOD_KEY]),
    )
    private val url: String = checkNotNull(handle[SheetScoresArgs.URL])
    val step: Step = Step.valueOf(checkNotNull(handle[SheetScoresArgs.STEP]))

    private val _state = MutableStateFlow<SheetScoresUiState>(SheetScoresUiState.Loading)
    val uiState: StateFlow<SheetScoresUiState> = _state.asStateFlow()
    private val eventQueue = EventQueue<SheetScoresEvent>()
    val events: Flow<SheetScoresEvent> = eventQueue.events

    private var workbook: SheetWorkbook? = null
    /** The own row in every tab where it is, for the cells being picked from. */
    private var matches: List<SheetRowMatch> = emptyList()
    /** One action at a time: a second tap while one runs is ignored. */
    private var job: Job? = null

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

    fun pickTotal(cell: SheetCell) = act {
        val row = matches.firstOrNull { it.tab == cell.tab } ?: return@act
        save(row, cell, fallback = _state.value)
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

    private suspend fun connect() {
        val ready = when (val inspection = repository.inspect(url)) {
            is SheetInspection.Failed -> return fail(inspection.status)
            is SheetInspection.Ready -> inspection
        }
        val book = ready.workbook.also { workbook = it }
        when (val search = ready.search) {
            is RowSearch.Found -> choose(book, search.matches)
            is RowSearch.Ambiguous -> _state.value = SheetScoresUiState.PickRow(search.candidates)
            RowSearch.NotFound -> _state.value = SheetScoresUiState.PickTab(
                book.tabs.filter { SheetRows.options(it).isNotEmpty() }.map { it.tab }
            )
        }
    }

    private suspend fun changeTotal() {
        val score = repository.observe().first().firstOrNull { it.scope.key == scope.key }
            ?: return run { _state.value = SheetScoresUiState.Done }
        val book = when (val inspection = repository.inspect(score.url)) {
            is SheetInspection.Failed -> return fail(inspection.status)
            is SheetInspection.Ready -> inspection.workbook.also { workbook = it }
        }
        matches = book.tabs.mapNotNull { SheetRows.locate(it, score.rowKey, score.keyKind, score.keyColumn) }
        if (matches.isEmpty()) return fail(SheetStatus.ROW_NOT_FOUND)
        val cells = cellsOf(book, matches)
        val selected = current(book, score, cells)
        _state.value = SheetScoresUiState.PickTotal(cells.filter { it.value.isNotEmpty() || it == selected }, selected)
    }

    /** The cells of [rows]; a total found by its header is saved at once, otherwise the viewer picks it. */
    private suspend fun choose(book: SheetWorkbook, rows: List<SheetRowMatch>) {
        matches = rows
        val cells = cellsOf(book, rows)
        val filled = cells.filter { it.value.isNotEmpty() }
        val total = SheetTotals.detect(cells)
        if (total == null || step == Step.TOTAL) {
            _state.value = SheetScoresUiState.PickTotal(filled, null)
            return
        }
        val row = rows.first { it.tab == total.tab }
        save(row, total, fallback = SheetScoresUiState.PickTotal(filled, total))
    }

    /** A failed write keeps [fallback] on screen and says so. */
    private suspend fun save(row: SheetRowMatch, total: SheetCell, fallback: SheetScoresUiState) {
        val result = when (step) {
            Step.CONNECT -> repository.connect(scope, url, row, total)
            Step.TOTAL -> repository.changeTotal(scope, row, total)
        }
        when (result) {
            is AppResult.Success -> _state.value = SheetScoresUiState.Done
            is AppResult.Failure -> {
                _state.value = fallback
                eventQueue.send(SheetScoresEvent.SaveFailed(result.error.toUiText()))
            }
        }
    }

    private fun cellsOf(book: SheetWorkbook, rows: List<SheetRowMatch>): List<SheetCell> =
        book.tabs.flatMap { tab -> rows.filter { it.tab == tab.tab }.flatMap { SheetTotals.cells(tab, it) } }

    /** The connected total in its tab, found by its header path as a reading would. */
    private fun current(book: SheetWorkbook, score: SheetScore, cells: List<SheetCell>): SheetCell? {
        val tab: SheetTabGrid = book.tabs.firstOrNull { it.tab.gid == score.tabGid } ?: return null
        val row = matches.firstOrNull { it.tab == tab.tab } ?: return null
        val first = SheetHeaders.firstDataRow(tab.grid, row.row, row.keyColumn, row.kind)
        val column = SheetTotals.find(SheetHeaders.paths(tab.grid, first, row.keyColumn), score.column, tab.grid.width)
        return cells.firstOrNull { it.tab == tab.tab && it.column == column }
    }

    private fun fail(status: SheetStatus) {
        _state.value = SheetScoresUiState.Failed(status)
    }
}
