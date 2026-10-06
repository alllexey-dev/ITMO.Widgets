package dev.alllexey.itmowidgets.feature.schedule.data.remote

import dev.alllexey.itmoapi.core.requireResult
import dev.alllexey.itmoapi.myitmo.MyItmoClient
import dev.alllexey.itmowidgets.client.schedule.LessonSyncRequest
import dev.alllexey.itmowidgets.client.schedule.ScheduleApi
import dev.alllexey.itmowidgets.core.coroutines.AppDispatchers
import dev.alllexey.itmowidgets.core.demo.DemoMode
import dev.alllexey.itmowidgets.core.schedule.ScheduleUtil
import dev.alllexey.itmowidgets.core.services.BackendGate
import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import dev.alllexey.itmowidgets.feature.schedule.data.demo.DemoSchedule
import dev.alllexey.itmowidgets.feature.schedule.data.mapper.toModel
import dev.alllexey.itmowidgets.feature.schedule.data.mapper.toSyncDto
import dev.alllexey.itmowidgets.feature.schedule.domain.model.DaySchedule
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate
import kotlinx.datetime.isoDayNumber

/**
 * The own schedule comes from MyITMO and, with the opt-in, is uploaded to Backend best-effort; another user's
 * schedule exists only on Backend, and `ScheduleRepositoryImpl` refuses it without the opt-in.
 */
class ScheduleRemoteDataSourceImpl @Inject constructor(
    private val backend: BackendGate,
    private val myItmo: MyItmoClient,
    private val backendSchedule: ScheduleApi,
    private val time: AcademicTimeProvider,
    private val demo: DemoMode,
    private val dispatchers: AppDispatchers
) : ScheduleRemoteDataSource {

    override suspend fun getSchedule(
        userIsu: Int?,
        start: LocalDate,
        end: LocalDate
    ): List<DaySchedule> {
        if (demo.isActive()) {
            return if (userIsu == null) {
                DemoSchedule.ownDays(start, end, time.today())
            } else {
                DemoSchedule.userDays(userIsu, start, end)
            }
        }
        return withContext(dispatchers.io) {
            if (userIsu == null) ownSchedule(start, end) else userSchedule(userIsu, start, end)
        }
    }

    private suspend fun ownSchedule(start: LocalDate, end: LocalDate): List<DaySchedule> {
        val days = myItmo.schedule.getPersonalSchedule(start, end).requireResult()
        if (backend.mayCallBackend()) {
            try {
                backendSchedule.syncLessons(
                    LessonSyncRequest(
                        lessons = days.flatMap { day -> day.lessons.map { lesson -> lesson.toSyncDto(day.date) } },
                        from = start,
                        to = end
                    )
                )
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                // Backend synchronization is best-effort and must not hide MyITMO schedule data.
            }
        }
        return days.map { it.toModel() }
    }

    private suspend fun userSchedule(userIsu: Int, start: LocalDate, end: LocalDate): List<DaySchedule> {
        val lessons = backendSchedule.userLessons(userIsu, start, end).groupBy { it.date }
        return ScheduleUtil.generateDates(start, end).map { date ->
            DaySchedule(
                dayNumber = date.dayOfWeek.isoDayNumber,
                weekNumber = -1,
                date = date,
                note = null,
                lessons = lessons[date]?.map { it.toModel() } ?: emptyList()
            )
        }
    }
}
