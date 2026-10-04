package dev.alllexey.itmowidgets.feature.schedule.data

import dev.alllexey.itmowidgets.core.ItmoWidgetsApi
import dev.alllexey.itmowidgets.core.coroutines.AppDispatchers
import dev.alllexey.itmowidgets.core.demo.DemoMode
import dev.alllexey.itmowidgets.core.model.ApiResponse
import dev.alllexey.itmowidgets.core.model.UserSummary
import dev.alllexey.itmowidgets.core.model.toUserSummary
import dev.alllexey.itmowidgets.core.network.appResultOf
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.services.BackendGate
import dev.alllexey.itmowidgets.feature.schedule.data.demo.DemoSchedule
import dev.alllexey.itmowidgets.feature.schedule.domain.LessonFriendsRepository
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.withContext

class LessonFriendsRepositoryImpl @Inject constructor(
    private val backend: BackendGate,
    private val widgetsApi: ItmoWidgetsApi,
    private val demo: DemoMode,
    private val dispatchers: AppDispatchers
) : LessonFriendsRepository {

    override suspend fun friendsOnLesson(pairId: Long, date: LocalDate): AppResult<List<UserSummary>> {
        if (demo.isActive()) return AppResult.Success(DemoSchedule.friendsOnLesson(pairId, date))
        // Without the opt-in the access token never leaves the device.
        if (!backend.mayCallBackend()) return AppResult.Failure(AppError.CustomServicesDisabled)
        return call { widgetsApi.friendsOnLesson(pairId, date) }.map { profiles ->
            profiles.map { it.user.toUserSummary() }
        }
    }

    private suspend fun <T> call(request: suspend () -> ApiResponse<T>): AppResult<T> = appResultOf {
        checkNotNull(withContext(dispatchers.io) { request().data }) { "Backend returned no data" }
    }

    private inline fun <T, R> AppResult<T>.map(transform: (T) -> R): AppResult<R> = when (this) {
        is AppResult.Success -> AppResult.Success(transform(value))
        is AppResult.Failure -> AppResult.Failure(error)
    }
}
