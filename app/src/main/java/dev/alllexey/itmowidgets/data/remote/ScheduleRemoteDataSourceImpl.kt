package dev.alllexey.itmowidgets.data.remote

import api.myitmo.MyItmoApi
import api.myitmo.model.Schedule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.awaitResponse
import java.time.LocalDate

class ScheduleRemoteDataSourceImpl(
    private val myItmoApi: MyItmoApi
) : ScheduleRemoteDataSource {

    override suspend fun getScheduleForRange(
        startDate: LocalDate,
        endDate: LocalDate
    ): List<Schedule>? {
        return withContext(Dispatchers.IO) {
            val response = myItmoApi.getPersonalSchedule(startDate, endDate).awaitResponse()
            if (!response.isSuccessful) {
                throw RuntimeException("Network request failed with code: ${response.code()}")
            }
            response.body()?.data
        }
    }

    override suspend fun getSchedule(date: LocalDate): Schedule? {
        return getScheduleForRange(date, date)?.firstOrNull()
    }
}