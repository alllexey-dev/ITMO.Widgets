package me.alllexey123.itmowidgets.ui.schedule

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import api.myitmo.model.Schedule
import kotlinx.coroutines.launch
import me.alllexey123.itmowidgets.data.repository.ScheduleRepository
import java.time.Duration
import java.time.LocalDate

sealed class ScheduleUiState {
    object Loading : ScheduleUiState()
    data class Success(val schedule: List<Schedule>, val isStillUpdating: Boolean) : ScheduleUiState()
    data class Error(val message: String) : ScheduleUiState()
}

class ScheduleViewModel(
    private val scheduleRepository: ScheduleRepository
) : ViewModel() {

    private val _uiState = MutableLiveData<ScheduleUiState>()
    val uiState: LiveData<ScheduleUiState> = _uiState

    fun fetchScheduleData(forceRefresh: Boolean) {
        viewModelScope.launch {
            try {
                val startDate = LocalDate.now().minusDays(7)
                val endDate = LocalDate.now().plusDays(7)

                val cachedSchedule =
                    scheduleRepository.getCachedScheduleForRange(startDate, endDate)

                if (cachedSchedule.isNotEmpty() && !forceRefresh) {

                    val totalDays = Duration.between(startDate.atStartOfDay(), endDate.atStartOfDay()).toDays() + 1

                    if (cachedSchedule.size < totalDays) {
                        _uiState.postValue(ScheduleUiState.Success(cachedSchedule, true))
                        val remoteSchedule = scheduleRepository.getScheduleForRange(startDate, endDate)
                        _uiState.postValue(ScheduleUiState.Success(remoteSchedule, false))
                    } else {
                        _uiState.postValue(ScheduleUiState.Success(cachedSchedule, false))
                    }
                } else {
                    _uiState.value = ScheduleUiState.Loading
                    val remoteSchedule = scheduleRepository.getScheduleForRange(startDate, endDate)
                    _uiState.postValue(ScheduleUiState.Success(remoteSchedule, false))
                }
            } catch (e: Exception) {
                val errorMessage = "Failed to update schedule: ${e.message}"
                _uiState.postValue(ScheduleUiState.Error(errorMessage))
                e.printStackTrace()
            }
        }
    }
}