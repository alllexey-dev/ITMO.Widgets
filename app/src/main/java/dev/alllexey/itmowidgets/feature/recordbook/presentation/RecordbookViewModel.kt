package dev.alllexey.itmowidgets.feature.recordbook.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.feature.recordbook.domain.RecordbookRepository
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookAssessmentKind
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookPeriod
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookProgram
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookSubject
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookSubjectStatus
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
    data class Content(
        val programs: List<RecordbookProgram>,
        val selection: RecordbookSelection,
        val allSubjects: List<RecordbookSubject>,
        val subjects: List<RecordbookSubject>,
        val filter: RecordbookFilter
    ) : RecordbookUiState
    data class Error(val error: AppError) : RecordbookUiState
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
            if (programs.isEmpty()) {
                when (val result = repository.getPrograms()) {
                    is AppResult.Success -> programs = result.value
                    is AppResult.Failure -> {
                        _uiState.value = RecordbookUiState.Error(result.error)
                        return@launch
                    }
                }
            }

            val selected = selection ?: restoreSelection() ?: defaultSelection()
            if (selected == null) {
                _uiState.value = RecordbookUiState.Error(AppError.NotFound)
                return@launch
            }
            selection = selected
            loadSubjects(selected)
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
        emitContent()
    }

    private fun refreshSubjects() {
        val selected = selection ?: return
        viewModelScope.launch {
            _uiState.value = RecordbookUiState.Loading
            loadSubjects(selected)
        }
    }

    private suspend fun loadSubjects(selected: RecordbookSelection) {
        when (
            val result = repository.getSubjects(
                selected.program.id,
                selected.period.semester
            )
        ) {
            is AppResult.Success -> {
                subjects = result.value
                emitContent()
            }
            is AppResult.Failure -> {
                _uiState.value = RecordbookUiState.Error(result.error)
            }
        }
    }

    private fun emitContent() {
        val selected = selection ?: return
        val filtered = subjects.filter { subject ->
            when (filter) {
                RecordbookFilter.ALL -> true
                RecordbookFilter.EXAMS ->
                    subject.assessmentKind == RecordbookAssessmentKind.EXAM
                RecordbookFilter.CREDITS ->
                    subject.assessmentKind == RecordbookAssessmentKind.CREDIT
                RecordbookFilter.ATTENTION -> {
                    subject.status == RecordbookSubjectStatus.ATTENTION
                }
            }
        }
        _uiState.value = RecordbookUiState.Content(
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

    private fun defaultSelection(): RecordbookSelection? {
        val program = programs.firstOrNull() ?: return null
        val period = program.periods.firstOrNull(RecordbookPeriod::actual)
            ?: program.periods.maxByOrNull(RecordbookPeriod::semester)
            ?: return null
        return RecordbookSelection(program, period)
    }

    private companion object {
        const val KEY_PROGRAM_ID = "recordbook_program_id"
        const val KEY_SEMESTER = "recordbook_semester"
    }
}
