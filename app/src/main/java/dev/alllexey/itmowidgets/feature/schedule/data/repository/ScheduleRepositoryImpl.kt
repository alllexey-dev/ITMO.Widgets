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
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ScheduleRepositoryImpl @Inject constructor(
    private val local: ScheduleLocalDataSource,
    private val remote: ScheduleRemoteDataSource,
    private val customServices: CustomServicesRepository
) : ScheduleRepository, SessionDataCleaner {

    private val cacheMutation = Mutex()
    private var cacheGeneration = 0L
    private val accessGenerations = mutableMapOf<Int, Long>()

    override fun observeScheduleForRange(
        userIsu: Int?,
        startDate: LocalDate,
        endDate: LocalDate
    ): Flow<List<DaySchedule>> {
        return local.observeRange(userIsu, startDate, endDate)
    }

    override fun peekScheduleForRange(
        userIsu: Int?,
        startDate: LocalDate,
        endDate: LocalDate
    ): List<DaySchedule>? = local.peekRange(userIsu, startDate, endDate)

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
            invalidateUser(userIsu)
            return AppResult.Failure(AppError.CustomServicesDisabled)
        }

        val revision = cacheMutation.withLock { cacheGeneration to accessGenerations[userIsu] }
        return try {
            val remoteData = remote.getSchedule(userIsu, startDate, endDate)

            cacheMutation.withLock {
                // A late success must not restore data cleared by logout or a
                // concurrent request that learned the owner revoked access.
                if (revision != (cacheGeneration to accessGenerations[userIsu])) {
                    AppResult.Failure(if (userIsu == null) AppError.Unauthorized else AppError.Forbidden)
                } else {
                    local.replaceRange(userIsu, startDate, endDate, remoteData)
                    AppResult.Success(Unit)
                }
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            val failure = error.toAppError()
            if (userIsu != null && failure in setOf(AppError.Forbidden, AppError.Unauthorized, AppError.NotFound)) {
                invalidateUser(userIsu)
            }
            AppResult.Failure(failure)
        }
    }

    private suspend fun invalidateUser(userIsu: Int) = withContext(NonCancellable) {
        // Once a denial is known, leaving the screen must not interrupt the
        // bounded local cleanup and leave forbidden rows available offline.
        cacheMutation.withLock {
            accessGenerations[userIsu] = (accessGenerations[userIsu] ?: 0) + 1
            local.clearUser(userIsu)
        }
    }

    override suspend fun clearCaches() {
        cacheMutation.withLock {
            cacheGeneration++
            local.clear()
        }
    }

    override suspend fun clearSessionData() {
        clearCaches()
    }
}
