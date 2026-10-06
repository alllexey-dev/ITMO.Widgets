package dev.alllexey.itmowidgets.feature.schedule.data

import dev.alllexey.itmowidgets.client.schedule.ScheduleApi
import dev.alllexey.itmowidgets.core.coroutines.AppDispatchers
import dev.alllexey.itmowidgets.core.demo.DemoMode
import dev.alllexey.itmowidgets.core.model.UserSummary
import dev.alllexey.itmowidgets.core.model.toUserSummary
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.services.BackendGate
import dev.alllexey.itmowidgets.feature.schedule.data.demo.DemoSchedule
import dev.alllexey.itmowidgets.feature.schedule.domain.LessonFriendsRepository
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate

class LessonFriendsRepositoryImpl(
    private val backend: BackendGate,
    private val schedule: ScheduleApi,
    private val demo: DemoMode,
    private val dispatchers: AppDispatchers
) : LessonFriendsRepository {

    override suspend fun friendsOnLesson(pairId: Long, date: LocalDate): AppResult<List<UserSummary>> {
        if (demo.isActive()) return AppResult.Success(DemoSchedule.friendsOnLesson(pairId, date))
        // Without the opt-in the access token never leaves the device.
        if (!backend.mayCallBackend()) return AppResult.Failure(AppError.CustomServicesDisabled)
        return scheduleResultOf {
            withContext(dispatchers.io) { schedule.friendsOnLesson(pairId, date) }.map { it.user.toUserSummary() }
        }
    }
}
