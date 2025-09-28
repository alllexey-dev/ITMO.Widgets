package me.alllexey123.itmowidgets.ui.schedule

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import api.myitmo.model.Schedule
import kotlinx.coroutines.launch
import me.alllexey123.itmowidgets.data.repository.ScheduleRepository
import java.time.LocalDate

sealed class ScheduleUiState {
    object Loading : ScheduleUiState()
    data class Success(val schedule: List<Schedule>) : ScheduleUiState()
    data class Error(val message: String) : ScheduleUiState()
}

class ScheduleViewModel(
    private val scheduleRepository: ScheduleRepository
) : ViewModel() {

    private val _uiState = MutableLiveData<ScheduleUiState>()
    val uiState: LiveData<ScheduleUiState> = _uiState

    private val _scheduleData = MutableLiveData<List<Schedule>>()
    val scheduleData: LiveData<List<Schedule>> = _scheduleData
    private val _error = MutableLiveData<String>()
    val error: LiveData<String> = _error

    fun fetchScheduleData() {
        _uiState.value = ScheduleUiState.Loading
        _error.value = ""

        viewModelScope.launch {
            try {
                val startDate = LocalDate.now().minusDays(7)
                val endDate = LocalDate.now().plusDays(7)

                val periodSchedule = scheduleRepository.getScheduleForRange(startDate, endDate)

                _scheduleData.postValue(periodSchedule)
                _uiState.postValue(ScheduleUiState.Success(periodSchedule))
            } catch (e: Exception) {
                val errorMessage = "Failed to load schedule: ${e.message}"
                _error.postValue(errorMessage)
                _uiState.postValue(ScheduleUiState.Error(errorMessage))
                e.printStackTrace()
            }
        }
    }
}