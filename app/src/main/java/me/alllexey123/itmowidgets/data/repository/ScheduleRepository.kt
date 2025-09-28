package me.alllexey123.itmowidgets.data.repository

import api.myitmo.model.Schedule
import me.alllexey123.itmowidgets.data.local.ScheduleLocalDataSource
import me.alllexey123.itmowidgets.data.remote.ScheduleRemoteDataSource
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
}