package dev.alllexey.itmowidgets.feature.recordbook.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.alllexey.itmowidgets.core.presentation.RefreshMode
import dev.alllexey.itmowidgets.core.resources.ResourceScope
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import dev.alllexey.itmowidgets.feature.recordbook.domain.BarsPreferenceRepository
import dev.alllexey.itmowidgets.feature.recordbook.domain.BarsRecordbookRepository
import dev.alllexey.itmowidgets.feature.recordbook.domain.RecordbookBarsMerge
import dev.alllexey.itmowidgets.feature.recordbook.domain.RecordbookRepository
import dev.alllexey.itmowidgets.feature.recordbook.domain.RecordbookSportResolver
import dev.alllexey.itmowidgets.feature.recordbook.domain.RecordbookSportState
import dev.alllexey.itmowidgets.feature.recordbook.domain.marks.BarsPlanMarks
import dev.alllexey.itmowidgets.feature.recordbook.domain.marks.MarkTrackingRepository
import dev.alllexey.itmowidgets.feature.recordbook.domain.marks.StudyHalf
import dev.alllexey.itmowidgets.feature.recordbook.domain.marks.of
import dev.alllexey.itmowidgets.feature.recordbook.domain.marks.studyHalf
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookControl
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookPeriod
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookProgram
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookSubject
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetScoresRepository
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class RecordbookViewModel @Inject constructor(
    private val repository: RecordbookRepository,
    private val bars: BarsRecordbookRepository,
    private val barsPreference: BarsPreferenceRepository,
    private val savedStateHandle: SavedStateHandle,
    private val sportResolver: RecordbookSportResolver,
    private val time: AcademicTimeProvider,
    private val marks: MarkTrackingRepository,
    private val sheets: SheetScoresRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow<RecordbookUiState>(RecordbookUiState.Loading())
    val uiState: StateFlow<RecordbookUiState> = _uiState.asStateFlow()
    /** Every state written here carries the current [barsEnabled]. */
    private var state: RecordbookUiState
        get() = _uiState.value
        set(value) { _uiState.value = value.withBarsEnabled(barsEnabled) }
    private var barsEnabled = false
    private var barsLoaded = false
    private var programs: List<RecordbookProgram> = emptyList()
    private var selection: RecordbookSelection? = null
    private var loadJob: Job? = null
    private var newsByHalf: Map<StudyHalf, Set<String>> = emptyMap()
    /** Non-empty sheet totals by `ResourceScope.key`. */
    private var sheetValues: Map<String, String> = emptyMap()

    init {
        // Unread marks come from the local store; a new one reaches the open list without a request.
        viewModelScope.launch {
            marks.observeNews().collect { news ->
                newsByHalf = news.groupBy({ it.half }, { it.nameKey }).mapValues { it.value.toSet() }
                (state as? RecordbookUiState.Content)?.let { content ->
                    state = content.copy(newSubjects = newSubjectsIn(content.selection.period))
                }
            }
        }
        // Stored totals only: a changed total reaches the open list, nothing is downloaded here.
        viewModelScope.launch {
            sheets.observe().collect { scores ->
                sheetValues = scores.mapNotNull { score -> score.value?.takeIf(String::isNotBlank)?.let { score.scope.key to it } }.toMap()
                (state as? RecordbookUiState.Content)?.let { content ->
                    state = content.copy(sheetTotals = sheetTotalsIn(content.selection.period, content.subjects))
                }
            }
        }
    }

    /**
     * [RefreshMode.Silent] is the entry: it loads the list once and does nothing while that load runs or after it
     * ended, so coming back to the list does not reload it. A pull or a retry shows the indicator and replaces a load
     * in flight.
     */
    fun refresh(mode: RefreshMode) {
        when {
            mode.showsIndicator -> load(silent = false)
            state is RecordbookUiState.Loading && loadJob?.isActive != true -> load(silent = true)
        }
    }

    fun setBarsEnabled(enabled: Boolean) {
        if (barsLoaded && enabled == barsEnabled) return
        barsLoaded = true
        showBarsEnabled(enabled)
        viewModelScope.launch { barsPreference.setEnabled(enabled) }
        load(silent = true)
    }

    fun selectPeriod(programId: Long, semester: Int) {
        val program = programs.firstOrNull { it.id == programId } ?: return
        val period = program.periods.firstOrNull { it.semester == semester } ?: return
        val selected = RecordbookSelection(program, period)
        if (selection == selected) return
        selection = selected
        savedStateHandle[KEY_PROGRAM_ID] = programId
        savedStateHandle[KEY_SEMESTER] = semester
        load(silent = true)
    }

    /** A pull shows the indicator; loads on entry, period change and the BARS switch stay silent behind the list. */
    private fun load(silent: Boolean) {
        loadJob?.cancel()
        val previous = (state as? RecordbookUiState.Content)
            ?.takeIf { it.selection == selection }?.copy(refreshing = false)
            ?: seedFromCache()
        state = previous?.copy(refreshing = !silent, refreshError = null)
            ?: RecordbookUiState.Loading(programs, selection)
        // Taken before any request: a background check written meanwhile makes this answer stale.
        val stamp = marks.readStarted()
        loadJob = viewModelScope.launch {
            if (!barsLoaded) {
                showBarsEnabled(barsPreference.isEnabled())
                barsLoaded = true
            }
            // Refresh the catalog as well: MyITMO's actual flag and available periods can change.
            when (val result = repository.getPrograms()) {
                is AppResult.Success -> programs = result.value
                is AppResult.Failure -> {
                    state = previous?.copy(refreshError = result.error)
                        ?: RecordbookUiState.Error(result.error, programs, selection)
                    return@launch
                }
            }
            val selected = selection?.let { old ->
                programs.firstOrNull { it.id == old.program.id }?.let { program ->
                    program.periods.firstOrNull { it.semester == old.period.semester }
                        ?.let { RecordbookSelection(program, it) }
                }
            } ?: restoreSelection() ?: defaultSelection()
            if (selected == null) {
                state = RecordbookUiState.Empty()
                return@launch
            }
            selection = selected
            val journals = if (barsEnabled) async { bars.getSubjects(selected.period) } else null
            when (val result = repository.getSubjects(selected.program.id, selected.period.semester)) {
                is AppResult.Success -> {
                    val official = result.value
                    // What the list shows advances the snapshot of the current half-year; children of the load job.
                    val half = selected.period.studyHalf()?.takeIf { it == StudyHalf.of(time.today()) }
                    if (half != null) {
                        launch { marks.recordMyItmoSeen(stamp, half, selected.program.id, selected.period.semester, official) }
                    }
                    val sport = sportResolver.resolve(selected.period, official)
                    if (journals == null) {
                        state = content(selected, official, sport)
                        return@launch
                    }
                    // MyITMO is on screen at once; a pull keeps its indicator until BARS answers.
                    state = content(selected, official, sport).copy(refreshing = !silent)
                    state = when (val overlay = journals.await()) {
                        is AppResult.Success -> {
                            if (half != null) launch { marks.recordBarsSeen(stamp, half, barsPlans(overlay.value)) }
                            content(selected, RecordbookBarsMerge.apply(official, overlay.value), sport).copy(barsApplied = true)
                        }
                        is AppResult.Failure -> content(selected, official, sport).copy(barsError = overlay.error)
                    }
                }
                is AppResult.Failure -> {
                    journals?.cancel()
                    state = previous?.takeIf { it.selection == selected }
                        ?.copy(refreshError = result.error)
                        ?: RecordbookUiState.Error(result.error, programs, selected)
                }
            }
        }
    }

    /** A fresh screen renders the last answers at once; the sport card waits for the refresh. */
    private fun seedFromCache(): RecordbookUiState.Content? {
        val cachedPrograms = repository.cachedPrograms() ?: return null
        programs = cachedPrograms
        val selected = selection?.let { old ->
            cachedPrograms.firstOrNull { it.id == old.program.id }?.let { program ->
                program.periods.firstOrNull { it.semester == old.period.semester }?.let { RecordbookSelection(program, it) }
            }
        } ?: restoreSelection() ?: defaultSelection() ?: return null
        val subjects = repository.cachedSubjects(selected.program.id, selected.period.semester) ?: return null
        selection = selected
        return content(selected, subjects, sport = null)
    }

    private fun content(selected: RecordbookSelection, subjects: List<RecordbookSubject>, sport: RecordbookSportState?) =
        RecordbookUiState.Content(programs, selected, subjects, sport, attention = subjects.mapNotNull { subject ->
            attentionReason(subject, sport, knownControls(subject), time.now())?.let { subject.entryId to it }
        }.toMap(), newSubjects = newSubjectsIn(selected.period), sheetTotals = sheetTotalsIn(selected.period, subjects))

    private fun sheetTotalsIn(period: RecordbookPeriod, subjects: List<RecordbookSubject>): Map<Long, String> {
        val periodKey = period.studyHalf()?.periodKey ?: return emptyMap()
        return subjects.mapNotNull { subject ->
            sheetValues[ResourceScope(subject.disciplineId, subject.name, periodKey).key]?.let { subject.disciplineId to it }
        }.toMap()
    }

    private fun newSubjectsIn(period: RecordbookPeriod): Set<String> =
        period.studyHalf()?.let(newsByHalf::get).orEmpty()

    /** Journals whose checkpoints an answer already parsed; the rest have nothing to compare. */
    private fun barsPlans(journals: List<RecordbookSubject>): List<BarsPlanMarks> = journals.mapNotNull { journal ->
        journal.barsJournal?.let(bars::cachedControls)?.let { BarsPlanMarks.of(journal, it) }
    }

    /** Only controls an earlier answer brought; the list never asks for them. */
    private fun knownControls(subject: RecordbookSubject): List<RecordbookControl>? =
        subject.barsJournal?.let(bars::cachedControls) ?: repository.cachedControls(subject.entryId)

    private fun restoreSelection(): RecordbookSelection? {
        val programId = savedStateHandle.get<Long>(KEY_PROGRAM_ID) ?: return null
        val semester = savedStateHandle.get<Int>(KEY_SEMESTER) ?: return null
        val program = programs.firstOrNull { it.id == programId } ?: return null
        val period = program.periods.firstOrNull { it.semester == semester } ?: return null
        return RecordbookSelection(program, period)
    }

    private fun defaultSelection(): RecordbookSelection? {
        val options = programs.flatMap { program -> program.periods.map { RecordbookSelection(program, it) } }
        val half = StudyHalf.of(time.today())
        return options.firstOrNull { it.period.studyHalf() == half }
            ?: options.firstOrNull { it.period.actual }
            ?: options.minByOrNull { it.period.semester }
    }

    private fun showBarsEnabled(enabled: Boolean) {
        barsEnabled = enabled
        _uiState.value = _uiState.value.withBarsEnabled(enabled)
    }

    private fun RecordbookUiState.withBarsEnabled(enabled: Boolean): RecordbookUiState = when (this) {
        is RecordbookUiState.Loading -> copy(barsEnabled = enabled)
        is RecordbookUiState.Content -> copy(barsEnabled = enabled)
        is RecordbookUiState.Empty -> copy(barsEnabled = enabled)
        is RecordbookUiState.Error -> copy(barsEnabled = enabled)
    }

    private companion object {
        const val KEY_PROGRAM_ID = "recordbook_program_id"
        const val KEY_SEMESTER = "recordbook_semester"
    }
}
