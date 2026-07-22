package dev.alllexey.itmowidgets.feature.recordbook.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.alllexey.itmowidgets.domain.model.recordbook.RecordbookPeriod
import dev.alllexey.itmowidgets.domain.model.recordbook.RecordbookProgram
import dev.alllexey.itmowidgets.domain.model.recordbook.RecordbookSubject
import dev.alllexey.itmowidgets.domain.model.recordbook.RecordbookSubjectStatus
import dev.alllexey.itmowidgets.domain.repository.RecordbookRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class RecordbookFilter {
    ALL,
    EXAMS,
    CREDITS,
    ATTENTION
}

data class RecordbookSelection(
    val program: RecordbookProgram,
    val period: RecordbookPeriod
)

sealed interface RecordbookUiState {
    data object Loading : RecordbookUiState
    data class Success(
        val programs: List<RecordbookProgram>,
        val selection: RecordbookSelection,
        val allSubjects: List<RecordbookSubject>,
        val subjects: List<RecordbookSubject>,
        val filter: RecordbookFilter
    ) : RecordbookUiState
    data class Error(val message: String) : RecordbookUiState
}

@HiltViewModel
class RecordbookViewModel @Inject constructor(
    private val repository: RecordbookRepository,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _uiState = MutableStateFlow<RecordbookUiState>(RecordbookUiState.Loading)
    val uiState: StateFlow<RecordbookUiState> = _uiState.asStateFlow()

    private var programs: List<RecordbookProgram> = emptyList()
    private var selection: RecordbookSelection? = null
    private var subjects: List<RecordbookSubject> = emptyList()
    private var filter = RecordbookFilter.ALL

    fun ensureDataLoaded() {
        if (_uiState.value is RecordbookUiState.Loading && programs.isEmpty()) {
            refresh()
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = RecordbookUiState.Loading
            runCatching {
                if (programs.isEmpty()) {
                    programs = repository.getPrograms()
                }
                val selected = selection ?: restoreSelection() ?: defaultSelection()
                selection = selected
                subjects = repository.getSubjects(selected.program.id, selected.period.semester)
            }.onSuccess {
                emitSuccess()
            }.onFailure { error ->
                _uiState.value = RecordbookUiState.Error(
                    error.message ?: "Не удалось загрузить зачётку"
                )
            }
        }
    }

    fun selectPeriod(programId: Long, semester: Int) {
        val program = programs.firstOrNull { it.id == programId } ?: return
        val period = program.periods.firstOrNull { it.semester == semester } ?: return
        selection = RecordbookSelection(program, period)
        savedStateHandle[KEY_PROGRAM_ID] = programId
        savedStateHandle[KEY_SEMESTER] = semester
        refreshSubjects()
    }

    fun setFilter(value: RecordbookFilter) {
        filter = value
        emitSuccess()
    }

    private fun refreshSubjects() {
        val selected = selection ?: return
        viewModelScope.launch {
            _uiState.value = RecordbookUiState.Loading
            runCatching {
                subjects = repository.getSubjects(selected.program.id, selected.period.semester)
            }.onSuccess {
                emitSuccess()
            }.onFailure { error ->
                _uiState.value = RecordbookUiState.Error(
                    error.message ?: "Не удалось загрузить зачётку"
                )
            }
        }
    }

    private fun emitSuccess() {
        val selected = selection ?: return
        val filtered = subjects.filter { subject ->
            when (filter) {
                RecordbookFilter.ALL -> true
                RecordbookFilter.EXAMS -> subject.controlType.contains("экзамен", true)
                RecordbookFilter.CREDITS -> subject.controlType.contains("зач", true)
                RecordbookFilter.ATTENTION -> {
                    subject.status == RecordbookSubjectStatus.ATTENTION
                }
            }
        }
        _uiState.value = RecordbookUiState.Success(
            programs = programs,
            selection = selected,
            allSubjects = subjects,
            subjects = filtered,
            filter = filter
        )
    }

    private fun restoreSelection(): RecordbookSelection? {
        val programId = savedStateHandle.get<Long>(KEY_PROGRAM_ID) ?: return null
        val semester = savedStateHandle.get<Int>(KEY_SEMESTER) ?: return null
        val program = programs.firstOrNull { it.id == programId } ?: return null
        val period = program.periods.firstOrNull { it.semester == semester } ?: return null
        return RecordbookSelection(program, period)
    }

    private fun defaultSelection(): RecordbookSelection {
        val program = programs.firstOrNull()
            ?: error("Для пользователя не найдена образовательная программа")
        val period = program.periods.firstOrNull(RecordbookPeriod::actual)
            ?: program.periods.maxByOrNull(RecordbookPeriod::semester)
            ?: error("Для программы не найдены семестры")
        return RecordbookSelection(program, period)
    }

    private companion object {
        const val KEY_PROGRAM_ID = "recordbook_program_id"
        const val KEY_SEMESTER = "recordbook_semester"
    }
}
