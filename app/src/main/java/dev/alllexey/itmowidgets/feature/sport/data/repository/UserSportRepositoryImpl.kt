package dev.alllexey.itmowidgets.feature.sport.data.repository

import dev.alllexey.itmowidgets.core.ItmoWidgetsApi
import dev.alllexey.itmowidgets.core.coroutines.AppDispatchers
import dev.alllexey.itmowidgets.core.demo.DemoMode
import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import dev.alllexey.itmowidgets.feature.sport.data.demo.DemoSport
import dev.alllexey.itmowidgets.core.network.toAppError
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.services.BackendGate
import dev.alllexey.itmowidgets.feature.sport.data.mapper.toBooking
import dev.alllexey.itmowidgets.feature.sport.data.mapper.toModel
import dev.alllexey.itmowidgets.feature.sport.domain.repository.UserSportBookings
import dev.alllexey.itmowidgets.feature.sport.domain.repository.UserSportRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import javax.inject.Inject

class UserSportRepositoryImpl @Inject constructor(
    private val backend: BackendGate,
    private val widgetsApi: ItmoWidgetsApi,
    private val time: AcademicTimeProvider,
    private val demo: DemoMode,
    private val dispatchers: AppDispatchers
) : UserSportRepository {

    override suspend fun getUserBookings(isu: Int): AppResult<UserSportBookings> {
        if (demo.isActive()) {
            return DemoSport.userBookings(isu, time)?.let { AppResult.Success(it) } ?: AppResult.Failure(AppError.Forbidden)
        }
        if (!backend.mayCallBackend()) return AppResult.Failure(AppError.CustomServicesDisabled)
        return try {
            val response = withContext(dispatchers.io) { widgetsApi.userSportBookings(isu).data }
                ?: return AppResult.Failure(IllegalStateException("Backend returned no data").toAppError())
            AppResult.Success(
                UserSportBookings(
                    confirmedLessonIds = response.lessonIds,
                    pending = response.entries.map { it.toModel().toBooking() }
                )
            )
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            AppResult.Failure(error.toAppError())
        }
    }
}
