package dev.alllexey.itmowidgets.data.repository

import dev.alllexey.itmowidgets.domain.model.schedule.DaySchedule
import dev.alllexey.itmowidgets.domain.repository.ScheduleRepository
import dev.alllexey.itmowidgets.data.local.ScheduleLocalDataSource
import dev.alllexey.itmowidgets.data.remote.ScheduleRemoteDataSource
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import javax.inject.Inject

class ScheduleRepositoryImpl @Inject constructor(
    private val local: ScheduleLocalDataSource,
    private val remote: ScheduleRemoteDataSource
) : ScheduleRepository {

    override fun observeScheduleForRange(
        userIsu: Int?,
        startDate: LocalDate,
        endDate: LocalDate
    ): Flow<List<DaySchedule>> {
        return local.observeRange(userIsu, startDate, endDate)
    }

    override suspend fun refreshSchedule(
        userIsu: Int?,
        startDate: LocalDate,
        endDate: LocalDate
    ) {
        val remoteData = remote.getSchedule(userIsu, startDate, endDate)

        remoteData.forEach {
            local.save(it, userIsu)
        }
    }

    override fun clearCaches() {
        local.clear()
    }
}
