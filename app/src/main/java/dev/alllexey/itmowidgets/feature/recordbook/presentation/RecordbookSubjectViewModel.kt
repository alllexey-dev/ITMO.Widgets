package dev.alllexey.itmowidgets.feature.recordbook.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.feature.recordbook.domain.RecordbookRepository
import dev.alllexey.itmowidgets.feature.recordbook.domain.RecordbookSportResolver
import dev.alllexey.itmowidgets.feature.recordbook.domain.RecordbookSportState
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookControl
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookPeriod
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookSubject
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface RecordbookSubjectUiState {
    data object Loading : RecordbookSubjectUiState
    data class Content(
        val subject: RecordbookSubject,
        val controls: List<RecordbookControl>,
        val sport: RecordbookSportState?,
        val controlsError: AppError? = null,
        val refreshing: Boolean = false,
        val refreshError: AppError? = null
    ) : RecordbookSubjectUiState
    data class Error(val error: AppError) : RecordbookSubjectUiState
}

@HiltViewModel
class RecordbookSubjectViewModel @Inject constructor(
    private val repository: RecordbookRepository,
    savedStateHandle: SavedStateHandle,
    private val sportResolver: RecordbookSportResolver
) : ViewModel() {
    private val entryId = checkNotNull(savedStateHandle.get<Long>(ARG_ENTRY_ID))
    private val programId = checkNotNull(savedStateHandle.get<Long>(ARG_PROGRAM_ID))
    private val period = RecordbookPeriod(
        studyYear = checkNotNull(savedStateHandle.get<String>(ARG_STUDY_YEAR)),
        semester = checkNotNull(savedStateHandle.get<Int>(ARG_SEMESTER)),
        course = 0,
        actual = false
    )
    private val _uiState = MutableStateFlow<RecordbookSubjectUiState>(RecordbookSubjectUiState.Loading)
    val uiState = _uiState.asStateFlow()
    private var loadJob: Job? = null

    init { refresh() }

    fun refresh() {
        loadJob?.cancel()
        val previous = (_uiState.value as? RecordbookSubjectUiState.Content)?.copy(refreshing = false)
        _uiState.value = previous?.copy(refreshing = true, refreshError = null)
            ?: RecordbookSubjectUiState.Loading
        loadJob = viewModelScope.launch {
            val subjects = repository.getSubjects(programId, period.semester)
            val subject = (subjects as? AppResult.Success)?.value?.firstOrNull { it.entryId == entryId }
            if (subject == null) {
                val error = (subjects as? AppResult.Failure)?.error ?: AppError.NotFound
                _uiState.value = previous?.copy(refreshError = error)
                    ?: RecordbookSubjectUiState.Error(error)
                return@launch
            }
            val sport = async { sportResolver.resolve(period, listOf(subject)) }
            val controls = if (subject.hasDetails) repository.getControls(entryId)
                else AppResult.Success(emptyList())
            _uiState.value = RecordbookSubjectUiState.Content(
                subject = subject,
                controls = (controls as? AppResult.Success)?.value.orEmpty(),
                sport = sport.await(),
                controlsError = (controls as? AppResult.Failure)?.error
            )
        }
    }

    companion object {
        const val ARG_ENTRY_ID = "entry_id"
        const val ARG_PROGRAM_ID = "program_id"
        const val ARG_SEMESTER = "semester"
        const val ARG_STUDY_YEAR = "study_year"
    }
}
