package me.alllexey123.itmowidgets.ui

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import api.myitmo.model.Schedule
import kotlinx.coroutines.launch
import me.alllexey123.itmowidgets.providers.MyItmoProvider
import retrofit2.awaitResponse
import java.time.LocalDate

class ScheduleViewModel : ViewModel() {
    private val _scheduleData = MutableLiveData<List<Schedule>>()
    val scheduleData: LiveData<List<Schedule>> = _scheduleData

    private val _error = MutableLiveData<String>()
    val error: LiveData<String> = _error

    fun fetchScheduleData(context: Context) {
        viewModelScope.launch {
            try {
                val startDate = LocalDate.now().minusDays(7)
                val endDate = LocalDate.now().plusDays(7)

                val weekSchedule = MyItmoProvider.getMyItmo(context).api()
                    .getPersonalSchedule(startDate, endDate)
                    .awaitResponse()

                _scheduleData.postValue(weekSchedule.body()!!.data)

            } catch (e: Exception) {
                _error.postValue("Failed to load schedule: ${e.message}")
                e.printStackTrace()
            }
        }
    }
}