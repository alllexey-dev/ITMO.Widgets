package dev.alllexey.itmowidgets.feature.sport.data.repository

import dev.alllexey.itmowidgets.core.ItmoWidgetsApi
import dev.alllexey.itmowidgets.core.network.toAppError
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.services.CustomServicesRepository
import dev.alllexey.itmowidgets.feature.sport.data.mapper.toBooking
import dev.alllexey.itmowidgets.feature.sport.data.mapper.toModel
import dev.alllexey.itmowidgets.feature.sport.domain.repository.UserSportBookings
import dev.alllexey.itmowidgets.feature.sport.domain.repository.UserSportRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class UserSportRepositoryImpl @Inject constructor(
    private val customServices: CustomServicesRepository,
    private val widgetsApi: ItmoWidgetsApi
) : UserSportRepository {

    override suspend fun getUserBookings(isu: Int): AppResult<UserSportBookings> {
        if (!customServices.isEnabled()) return AppResult.Failure(AppError.CustomServicesDisabled)
        return try {
            val response = withContext(Dispatchers.IO) { widgetsApi.userSportBookings(isu).data }
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
