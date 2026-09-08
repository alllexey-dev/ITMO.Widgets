package dev.alllexey.itmowidgets.feature.schedule.data.repository

import dev.alllexey.itmowidgets.core.network.toAppError
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.services.CustomServicesRepository
import dev.alllexey.itmowidgets.core.session.SessionDataCleaner
import dev.alllexey.itmowidgets.feature.schedule.data.local.ScheduleLocalDataSource
import dev.alllexey.itmowidgets.feature.schedule.data.remote.ScheduleRemoteDataSource
import dev.alllexey.itmowidgets.feature.schedule.domain.ScheduleRepository
import dev.alllexey.itmowidgets.feature.schedule.domain.model.DaySchedule
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import javax.inject.Inject

class ScheduleRepositoryImpl @Inject constructor(
    private val local: ScheduleLocalDataSource,
    private val remote: ScheduleRemoteDataSource,
    private val customServices: CustomServicesRepository
) : ScheduleRepository, SessionDataCleaner {

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
    ): AppResult<Unit> {
        // Another user's schedule only exists on the project backend. Without the
        // opt-in the access token must never leave the device, so the request is
        // refused here rather than in the data source, which cannot be reached
        // without going through this policy.
        if (userIsu != null && !customServices.isEnabled()) {
            return AppResult.Failure(AppError.CustomServicesDisabled)
        }

        return try {
            val remoteData = remote.getSchedule(userIsu, startDate, endDate)

            local.replaceRange(userIsu, startDate, endDate, remoteData)

            AppResult.Success(Unit)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            AppResult.Failure(error.toAppError())
        }
    }

    override suspend fun clearCaches() {
        local.clear()
    }

    override suspend fun clearSessionData() {
        clearCaches()
    }
}
