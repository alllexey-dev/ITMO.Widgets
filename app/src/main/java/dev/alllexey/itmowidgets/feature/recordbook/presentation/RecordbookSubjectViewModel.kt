package dev.alllexey.itmowidgets.feature.recordbook.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.feature.recordbook.domain.RecordbookRepository
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookControl
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface RecordbookSubjectUiState {
    data object Loading : RecordbookSubjectUiState
    data class Content(val controls: List<RecordbookControl>) : RecordbookSubjectUiState
    data object Empty : RecordbookSubjectUiState
    data class Error(val error: AppError) : RecordbookSubjectUiState
}

@HiltViewModel
class RecordbookSubjectViewModel @Inject constructor(
    private val repository: RecordbookRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val entryId = checkNotNull(savedStateHandle.get<Long>(ARG_ENTRY_ID))

    private val _uiState = MutableStateFlow<RecordbookSubjectUiState>(
        RecordbookSubjectUiState.Loading
    )
    val uiState: StateFlow<RecordbookSubjectUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = RecordbookSubjectUiState.Loading
            when (val result = repository.getControls(entryId)) {
                is AppResult.Success -> {
                    _uiState.value = if (result.value.isEmpty()) {
                        RecordbookSubjectUiState.Empty
                    } else {
                        RecordbookSubjectUiState.Content(result.value)
                    }
                }
                is AppResult.Failure -> {
                    _uiState.value = RecordbookSubjectUiState.Error(result.error)
                }
            }
        }
    }

    companion object {
        const val ARG_ENTRY_ID = "entry_id"
    }
}
