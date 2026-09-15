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
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookPeriod
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookProgram
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
        val barsApplied: Boolean = false
    ) : RecordbookUiState
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
        if (_uiState.value is RecordbookUiState.Loading && loadJob?.isActive != true) refresh()
    }

    fun setBarsEnabled(enabled: Boolean) {
        if (barsLoaded && enabled == _barsEnabled.value) return
        barsLoaded = true
        _barsEnabled.value = enabled
        viewModelScope.launch { barsPreference.setEnabled(enabled) }
        refresh()
    }

    fun selectPeriod(programId: Long, semester: Int) {
        val program = programs.firstOrNull { it.id == programId } ?: return
        val period = program.periods.firstOrNull { it.semester == semester } ?: return
        val selected = RecordbookSelection(program, period)
        if (selection == selected) return
        selection = selected
        savedStateHandle[KEY_PROGRAM_ID] = programId
        savedStateHandle[KEY_SEMESTER] = semester
        refresh()
    }

    fun refresh() {
        loadJob?.cancel()
        val previous = (_uiState.value as? RecordbookUiState.Content)
            ?.takeIf { it.selection == selection }?.copy(refreshing = false)
        _uiState.value = previous?.copy(refreshing = true, refreshError = null)
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
                        _uiState.value = RecordbookUiState.Content(programs, selected, official, sport)
                        return@launch
                    }
                    // MyITMO is on screen at once; the spinner stays until BARS answers.
                    _uiState.value = RecordbookUiState.Content(programs, selected, official, sport, refreshing = true)
                    _uiState.value = when (val overlay = journals.await()) {
                        is AppResult.Success -> RecordbookUiState.Content(programs, selected,
                            RecordbookBarsMerge.apply(official, overlay.value), sport, barsApplied = true)
                        is AppResult.Failure -> RecordbookUiState.Content(programs, selected, official, sport, barsError = overlay.error)
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
