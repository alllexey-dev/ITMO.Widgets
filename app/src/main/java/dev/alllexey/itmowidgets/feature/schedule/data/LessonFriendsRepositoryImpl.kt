package dev.alllexey.itmowidgets.feature.schedule.data

import dev.alllexey.itmowidgets.core.ItmoWidgetsApi
import dev.alllexey.itmowidgets.core.model.ApiResponse
import dev.alllexey.itmowidgets.core.model.UserSummary
import dev.alllexey.itmowidgets.core.model.toUserSummary
import dev.alllexey.itmowidgets.core.network.toAppError
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.services.CustomServicesRepository
import dev.alllexey.itmowidgets.feature.schedule.domain.LessonFriendsRepository
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LessonFriendsRepositoryImpl @Inject constructor(
    private val customServices: CustomServicesRepository,
    private val widgetsApi: ItmoWidgetsApi
) : LessonFriendsRepository {

    override suspend fun friendsOnLesson(pairId: Long, date: LocalDate): AppResult<List<UserSummary>> {
        // Without the opt-in the access token never leaves the device.
        if (!customServices.isEnabled()) return AppResult.Failure(AppError.CustomServicesDisabled)
        return call { widgetsApi.friendsOnLesson(pairId, date) }.map { profiles ->
            profiles.map { it.user.toUserSummary() }
        }
    }

    private suspend fun <T> call(request: suspend () -> ApiResponse<T>): AppResult<T> {
        return try {
            val data = withContext(Dispatchers.IO) { request().data }
            if (data != null) {
                AppResult.Success(data)
            } else {
                AppResult.Failure(IllegalStateException("Backend returned no data").toAppError())
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            AppResult.Failure(error.toAppError())
        }
    }

    private inline fun <T, R> AppResult<T>.map(transform: (T) -> R): AppResult<R> = when (this) {
        is AppResult.Success -> AppResult.Success(transform(value))
        is AppResult.Failure -> AppResult.Failure(error)
    }
}
