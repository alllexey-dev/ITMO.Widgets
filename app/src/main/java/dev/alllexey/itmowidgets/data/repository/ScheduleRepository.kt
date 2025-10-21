package dev.alllexey.itmowidgets.data.repository

import api.myitmo.model.Schedule
import dev.alllexey.itmowidgets.data.local.ScheduleLocalDataSource
import dev.alllexey.itmowidgets.data.remote.ScheduleRemoteDataSource
import java.time.LocalDate

class ScheduleRepository(
    private val localDataSource: ScheduleLocalDataSource,
    private val remoteDataSource: ScheduleRemoteDataSource
) {

    suspend fun getDaySchedule(date: LocalDate): Schedule {
        val cached = localDataSource.getSchedule(date)

        if (cached != null && !localDataSource.isExpired(cached.second)) {
            return cached.first
        }

        try {
            val remoteSchedule = remoteDataSource.getSchedule(date)
                ?: throw RuntimeException("API returned empty schedule data")

            localDataSource.saveSchedule(remoteSchedule)
            return remoteSchedule

        } catch (e: Exception) {
            if (cached != null) {
                return cached.first
            } else {
                throw RuntimeException("Could not get schedule and no cache is available.", e)
            }
        }
    }

    suspend fun getScheduleForRange(startDate: LocalDate, endDate: LocalDate): List<Schedule> {
        val remoteSchedule = remoteDataSource.getScheduleForRange(startDate, endDate)
            ?: throw RuntimeException("API returned empty schedule data for range")

        remoteSchedule.forEach { localDataSource.saveSchedule(it) }

        return remoteSchedule
    }

    fun getCachedScheduleForRange(startDate: LocalDate, endDate: LocalDate): List<Schedule> {
        return localDataSource.getSchedulesForRange(startDate, endDate)
    }

    fun clearCache() {
        localDataSource.clearCache()
    }
}