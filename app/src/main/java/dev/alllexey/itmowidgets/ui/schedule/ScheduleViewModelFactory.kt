package dev.alllexey.itmowidgets.ui.schedule

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import dev.alllexey.itmowidgets.data.repository.ScheduleRepository

class ScheduleViewModelFactory(
    private val scheduleRepository: ScheduleRepository
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ScheduleViewModel::class.java)) {
            return ScheduleViewModel(scheduleRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}