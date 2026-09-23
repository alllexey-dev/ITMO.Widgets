package dev.alllexey.itmowidgets.feature.recordbook.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import dev.alllexey.itmowidgets.feature.recordbook.domain.BarsPreferenceRepository
import dev.alllexey.itmowidgets.feature.recordbook.domain.BarsRecordbookRepository
import dev.alllexey.itmowidgets.feature.recordbook.domain.RecordbookBarsMerge
import dev.alllexey.itmowidgets.feature.recordbook.domain.RecordbookRepository
import dev.alllexey.itmowidgets.feature.recordbook.domain.RecordbookSportResolver
import dev.alllexey.itmowidgets.feature.recordbook.domain.RecordbookSportState
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookControl
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookPeriod
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookProgram
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookRate
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookSubject
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class RecordbookSelection(val program: RecordbookProgram, val period: RecordbookPeriod)

sealed interface RecordbookUiState {
    data class Loading(
        val programs: List<RecordbookProgram> = emptyList(),
        val selection: RecordbookSelection? = null
    ) : RecordbookUiState
    data class Content(
        val programs: List<RecordbookProgram>,
        val selection: RecordbookSelection,
        val subjects: List<RecordbookSubject>,
        val sport: RecordbookSportState? = null,
        val refreshing: Boolean = false,
        val refreshError: AppError? = null,
        /** BARS overlay failed; the list still holds MyITMO values. */
        val barsError: AppError? = null,
        /** BARS answered for this period, so subjects without a journal are genuinely absent there. */
        val barsApplied: Boolean = false,
        /** Subjects for «Требуют внимания» by `entryId`; everything else is the regular list. */
        val attention: Map<Long, RecordbookAttentionReason> = emptyMap()
    ) : RecordbookUiState {
        /** The pass count means something only once a final grade or credit exists. */
        val showSummary: Boolean get() = subjects.any { it.normalizedRate != RecordbookRate.InProgress }
    }
    data object Empty : RecordbookUiState
    data class Error(
        val error: AppError,
        val programs: List<RecordbookProgram> = emptyList(),
        val selection: RecordbookSelection? = null
    ) : RecordbookUiState
}

@HiltViewModel
class RecordbookViewModel @Inject constructor(
    private val repository: RecordbookRepository,
    private val bars: BarsRecordbookRepository,
    private val barsPreference: BarsPreferenceRepository,
    private val savedStateHandle: SavedStateHandle,
    private val sportResolver: RecordbookSportResolver,
    private val time: AcademicTimeProvider
) : ViewModel() {
    private val _uiState = MutableStateFlow<RecordbookUiState>(RecordbookUiState.Loading())
    val uiState: StateFlow<RecordbookUiState> = _uiState.asStateFlow()
    private val _barsEnabled = MutableStateFlow(false)
    val barsEnabled: StateFlow<Boolean> = _barsEnabled.asStateFlow()
    private var barsLoaded = false
    private var programs: List<RecordbookProgram> = emptyList()
    private var selection: RecordbookSelection? = null
    private var loadJob: Job? = null

    fun ensureDataLoaded() {
        if (_uiState.value is RecordbookUiState.Loading && loadJob?.isActive != true) refresh(silent = true)
    }

    fun setBarsEnabled(enabled: Boolean) {
        if (barsLoaded && enabled == _barsEnabled.value) return
        barsLoaded = true
        _barsEnabled.value = enabled
        viewModelScope.launch { barsPreference.setEnabled(enabled) }
        refresh(silent = true)
    }

    fun selectPeriod(programId: Long, semester: Int) {
        val program = programs.firstOrNull { it.id == programId } ?: return
        val period = program.periods.firstOrNull { it.semester == semester } ?: return
        val selected = RecordbookSelection(program, period)
        if (selection == selected) return
        selection = selected
        savedStateHandle[KEY_PROGRAM_ID] = programId
        savedStateHandle[KEY_SEMESTER] = semester
        refresh(silent = true)
    }

    /** A pull shows the indicator; loads on entry, period change and the BARS switch stay silent behind the list. */
    fun refresh(silent: Boolean = false) {
        loadJob?.cancel()
        val previous = (_uiState.value as? RecordbookUiState.Content)
            ?.takeIf { it.selection == selection }?.copy(refreshing = false)
            ?: seedFromCache()
        _uiState.value = previous?.copy(refreshing = !silent, refreshError = null)
            ?: RecordbookUiState.Loading(programs, selection)
        loadJob = viewModelScope.launch {
            if (!barsLoaded) {
                _barsEnabled.value = barsPreference.isEnabled()
                barsLoaded = true
            }
            // Refresh the catalog as well: MyITMO's actual flag and available periods can change.
            when (val result = repository.getPrograms()) {
                is AppResult.Success -> programs = result.value
                is AppResult.Failure -> {
                    _uiState.value = previous?.copy(refreshError = result.error)
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
                _uiState.value = RecordbookUiState.Empty
                return@launch
            }
            selection = selected
            val journals = if (_barsEnabled.value) async { bars.getSubjects(selected.period) } else null
            when (val result = repository.getSubjects(selected.program.id, selected.period.semester)) {
                is AppResult.Success -> {
                    val official = result.value
                    val sport = sportResolver.resolve(selected.period, official)
                    if (journals == null) {
                        _uiState.value = content(selected, official, sport)
                        return@launch
                    }
                    // MyITMO is on screen at once; a pull keeps its indicator until BARS answers.
                    _uiState.value = content(selected, official, sport).copy(refreshing = !silent)
                    _uiState.value = when (val overlay = journals.await()) {
                        is AppResult.Success ->
                            content(selected, RecordbookBarsMerge.apply(official, overlay.value), sport).copy(barsApplied = true)
                        is AppResult.Failure -> content(selected, official, sport).copy(barsError = overlay.error)
                    }
                }
                is AppResult.Failure -> {
                    journals?.cancel()
                    _uiState.value = previous?.takeIf { it.selection == selected }
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
        }.toMap())

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
        val today = time.today()
        val yearStart = today.year - if (today.monthValue < 9) 1 else 0
        val year = "$yearStart/${yearStart + 1}"
        val half = if (today.monthValue in 2..8) 2 else 1
        return options.firstOrNull { it.period.studyYear == year && it.period.semesterInCourse == half }
            ?: options.firstOrNull { it.period.actual }
            ?: options.minByOrNull { it.period.semester }
    }

    private companion object {
        const val KEY_PROGRAM_ID = "recordbook_program_id"
        const val KEY_SEMESTER = "recordbook_semester"
    }
}
