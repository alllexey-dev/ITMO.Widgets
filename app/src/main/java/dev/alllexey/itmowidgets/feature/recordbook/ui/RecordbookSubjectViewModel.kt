package dev.alllexey.itmowidgets.feature.recordbook.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.alllexey.itmowidgets.domain.model.recordbook.RecordbookControl
import dev.alllexey.itmowidgets.domain.repository.RecordbookRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface RecordbookSubjectUiState {
    data object Loading : RecordbookSubjectUiState
    data class Success(val controls: List<RecordbookControl>) : RecordbookSubjectUiState
    data class Error(val message: String) : RecordbookSubjectUiState
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
            runCatching { repository.getControls(entryId) }
                .onSuccess { controls ->
                    _uiState.value = RecordbookSubjectUiState.Success(controls)
                }
                .onFailure { error ->
                    _uiState.value = RecordbookSubjectUiState.Error(
                        error.message ?: "Не удалось загрузить результаты"
                    )
                }
        }
    }

    companion object {
        const val ARG_ENTRY_ID = "entry_id"
    }
}
