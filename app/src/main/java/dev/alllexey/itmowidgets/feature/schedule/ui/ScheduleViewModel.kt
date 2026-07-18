package dev.alllexey.itmowidgets.feature.schedule.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.alllexey.itmowidgets.domain.model.schedule.DaySchedule
import dev.alllexey.itmowidgets.domain.repository.ScheduleRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

sealed class ScheduleUiState {

    object Loading : ScheduleUiState()

    data class Success(
        val schedule: List<DaySchedule>, val isLoadingMore: Boolean = false
    ) : ScheduleUiState()

    data class Error(val message: String) : ScheduleUiState()
}

data class SelectedUser(
    val isu: Int?, val name: String?, val avatar: String?
)

@HiltViewModel
class ScheduleViewModel @Inject constructor(
    private val repository: ScheduleRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private var userIsu: Int? = savedStateHandle
        .get<Int>(ARG_USER_ISU)
        ?.takeIf { it != NO_USER_ISU }

    // region UI state

    private val _uiState = MutableStateFlow<ScheduleUiState>(ScheduleUiState.Loading)
    val uiState: StateFlow<ScheduleUiState> = _uiState

    private val _selectedUser = MutableStateFlow<SelectedUser?>(null)
    val selectedUser: StateFlow<SelectedUser?> = _selectedUser

    // endregion

    // region Schedule state

    private var currentStart = LocalDate.now().minusDays(1)
    private var currentEnd = LocalDate.now().plusDays(14)

    private var currentDays = listOf<DaySchedule>()

    private var observeJob: Job? = null
    private var isLoadingMore = false

    // endregion

    // region API

    fun ensureDataLoaded() {
        if (_uiState.value is ScheduleUiState.Loading && currentDays.isEmpty()) {
            loadInitialSchedule()
        }
    }

    fun loadInitialSchedule(forceRefresh: Boolean = false) {

        resetRange()
        observeRange()

        viewModelScope.launch {

            if (forceRefresh) {
                repository.clearCaches()
            }

            setLoading(true)

            try {
                repository.refreshSchedule(userIsu, currentStart, currentEnd)
            } catch (e: Exception) {
                emitError(e)
            } finally {
                setLoading(false)
            }
        }
    }

    fun fetchNextDays() {

        if (isLoadingMore) return

        val newStart = currentEnd.plusDays(1)
        val newEnd = currentEnd.plusDays(14)

        currentEnd = newEnd

        observeRange()

        viewModelScope.launch {

            setLoading(true)

            try {
                repository.refreshSchedule(userIsu, newStart, newEnd)
            } catch (e: Exception) {
                emitError(e)
            } finally {
                setLoading(false)
            }
        }
    }

    fun setSelectedUser(user: SelectedUser?) {
        userIsu = user?.isu
        _selectedUser.value = user
    }

    private fun resetRange() {
        currentStart = LocalDate.now().minusDays(1)
        currentEnd = LocalDate.now().plusDays(14)
    }

    @OptIn(FlowPreview::class)
    private fun observeRange() {

        observeJob?.cancel()

        observeJob = viewModelScope.launch {

            repository
                .observeScheduleForRange(userIsu, currentStart, currentEnd)
                .debounce(50)
                .collect {
                    currentDays = it
                    emitCurrentState()
                }
        }
    }

    // endregion

    // region State helpers

    private fun emitCurrentState() {
        _uiState.value = ScheduleUiState.Success(
            schedule = currentDays, isLoadingMore = isLoadingMore
        )
    }

    private fun setLoading(value: Boolean) {
        isLoadingMore = value
        emitCurrentState()
    }

    private fun emitError(e: Exception) {
        _uiState.value = ScheduleUiState.Error(e.message ?: "Error")
    }

    // endregion

    companion object {
        const val ARG_USER_ISU = "user_isu"
        private const val NO_USER_ISU = -1
    }
}
