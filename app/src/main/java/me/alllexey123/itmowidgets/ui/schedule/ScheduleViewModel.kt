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

    private var isLoading = false
    private var dateRange: ClosedRange<LocalDate>? = null

    fun fetchScheduleData(forceRefresh: Boolean) {
        if (isLoading) return
        isLoading = true

        viewModelScope.launch {
            try {
                val startDate = dateRange?.start ?: LocalDate.now().minusDays(1)
                val endDate = dateRange?.endInclusive ?: LocalDate.now().plusDays(7)

                val cachedSchedule = scheduleRepository.getCachedScheduleForRange(startDate, endDate)

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
                dateRange = startDate..endDate
            } catch (e: Exception) {
                val errorMessage = "Failed to update schedule: ${e.message}"
                _uiState.postValue(ScheduleUiState.Error(errorMessage))
                e.printStackTrace()
            } finally {
                isLoading = false
            }
        }
    }

    fun fetchPreviousDays() {
        if (isLoading) return
        isLoading = true

        viewModelScope.launch {
            val currentSchedule = (_uiState.value as? ScheduleUiState.Success)?.schedule ?: emptyList()
            try {
                val currentStartDate = dateRange?.start ?: LocalDate.now()
                val newStartDate = currentStartDate.minusDays(7)
                val newEndDate = currentStartDate.minusDays(1)

                val cachedSchedule = scheduleRepository.getCachedScheduleForRange(newStartDate, newEndDate)
                val remoteSchedule = if (cachedSchedule.isNotEmpty()) {
                    val totalDays = Duration.between(newStartDate.atStartOfDay(), newEndDate.atStartOfDay()).toDays() + 1

                    if (cachedSchedule.size < totalDays) {
                        _uiState.postValue(ScheduleUiState.Loading)
                        scheduleRepository.getScheduleForRange(newStartDate, newEndDate)
                    } else {
                        cachedSchedule
                    }
                } else {
                    _uiState.value = ScheduleUiState.Loading
                    scheduleRepository.getScheduleForRange(newStartDate, newEndDate)
                }

                val updatedSchedule = (remoteSchedule + currentSchedule).distinctBy { it.date }.sortedBy { it.date }
                _uiState.postValue(ScheduleUiState.Success(updatedSchedule, false))
                dateRange = newStartDate..(dateRange?.endInclusive ?: LocalDate.now())
            } catch (e: Exception) {
                val errorMessage = "Failed to update schedule: ${e.message}"
                _uiState.postValue(ScheduleUiState.Error(errorMessage))
                _uiState.postValue(ScheduleUiState.Success(currentSchedule, false))
                e.printStackTrace()
            } finally {
                isLoading = false
            }
        }
    }

    fun fetchNextDays() {
        if (isLoading) return
        isLoading = true

        viewModelScope.launch {
            val currentSchedule =
                (_uiState.value as? ScheduleUiState.Success)?.schedule ?: emptyList()
            try {

                val currentEndDate = dateRange?.endInclusive ?: LocalDate.now()
                val newStartDate = currentEndDate.plusDays(1)
                val newEndDate = currentEndDate.plusDays(7)

                val cachedSchedule =
                    scheduleRepository.getCachedScheduleForRange(newStartDate, newEndDate)
                val remoteSchedule = if (cachedSchedule.isNotEmpty()) {
                    val totalDays =
                        Duration.between(newStartDate.atStartOfDay(), newEndDate.atStartOfDay())
                            .toDays() + 1

                    if (cachedSchedule.size < totalDays) {
                        _uiState.postValue(ScheduleUiState.Loading)
                        scheduleRepository.getScheduleForRange(newStartDate, newEndDate)
                    } else {
                        cachedSchedule
                    }
                } else {
                    _uiState.value = ScheduleUiState.Loading
                    scheduleRepository.getScheduleForRange(newStartDate, newEndDate)
                }

                val updatedSchedule =
                    (currentSchedule + remoteSchedule).distinctBy { it.date }.sortedBy { it.date }
                _uiState.postValue(ScheduleUiState.Success(updatedSchedule, false))

                dateRange = (dateRange?.start ?: LocalDate.now())..newEndDate
            } catch (e: Exception) {
                val errorMessage = "Failed to update schedule: ${e.message}"
                _uiState.postValue(ScheduleUiState.Error(errorMessage))
                _uiState.postValue(ScheduleUiState.Success(currentSchedule, false))
                e.printStackTrace()
            } finally {
                isLoading = false
            }
        }
    }
}