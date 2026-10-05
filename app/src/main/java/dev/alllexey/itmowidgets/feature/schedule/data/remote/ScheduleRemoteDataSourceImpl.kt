package dev.alllexey.itmowidgets.feature.schedule.data.remote

import api.myitmo.MyItmoApi
import dev.alllexey.itmowidgets.core.ItmoWidgetsApi
import dev.alllexey.itmowidgets.core.coroutines.AppDispatchers
import dev.alllexey.itmowidgets.core.demo.DemoMode
import dev.alllexey.itmowidgets.core.model.LessonSyncRequest
import dev.alllexey.itmowidgets.core.schedule.ScheduleUtil
import dev.alllexey.itmowidgets.core.services.BackendGate
import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import dev.alllexey.itmowidgets.core.utils.toDto
import dev.alllexey.itmowidgets.feature.schedule.data.demo.DemoSchedule
import dev.alllexey.itmowidgets.feature.schedule.data.mapper.toModel
import dev.alllexey.itmowidgets.feature.schedule.domain.model.DaySchedule
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import retrofit2.awaitResponse
import javax.inject.Inject
import kotlinx.datetime.LocalDate
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.toJavaLocalDate
import kotlinx.datetime.toKotlinLocalDate

class ScheduleRemoteDataSourceImpl @Inject constructor(
    private val backend: BackendGate,
    private val api: MyItmoApi,
    private val widgetsApi: ItmoWidgetsApi,
    private val time: AcademicTimeProvider,
    private val demo: DemoMode,
    private val dispatchers: AppDispatchers
) : ScheduleRemoteDataSource {

    override suspend fun getSchedule(
        userIsu: Int?,
        start: LocalDate,
        end: LocalDate
    ): List<DaySchedule> = withContext(dispatchers.io) {
        if (demo.isActive()) {
            return@withContext if (userIsu == null) {
                DemoSchedule.ownDays(start, end, time.today())
            } else {
                DemoSchedule.userDays(userIsu, start, end)
            }
        }

        return@withContext if (userIsu == null) {
            val response = api
                .getPersonalSchedule(start.toJavaLocalDate(), end.toJavaLocalDate())
                .awaitResponse()

            if (!response.isSuccessful) {
                throw HttpException(response)
            }

            val days = response.body()?.data ?: return@withContext emptyList()

            if (backend.mayCallBackend()) {
                try {
                    widgetsApi.syncLessons(
                        LessonSyncRequest(
                            lessons = days.flatMap { day ->
                                day.lessons.map { lesson -> lesson.toDto(day.date) }
                            },
                            from = start.toJavaLocalDate(),
                            to = end.toJavaLocalDate()
                        )
                    )
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (_: Exception) {
                    // Backend synchronization is best-effort and must not hide MyITMO schedule data.
                }
            }

            days.map {
                DaySchedule(
                    dayNumber = it.dayNumber,
                    weekNumber = it.weekNumber,
                    date = it.date.toKotlinLocalDate(),
                    note = it.note,
                    lessons = it.lessons.map { it.toModel() }
                )
            }

        } else {
            val response = widgetsApi.userLessons(
                userIsu,
                start.toJavaLocalDate(),
                end.toJavaLocalDate()
            )

            val days = response.data?.groupBy { it.date.toKotlinLocalDate() } ?: return@withContext emptyList()

            ScheduleUtil.generateDates(start, end).map {
                DaySchedule(
                    dayNumber = it.dayOfWeek.isoDayNumber,
                    weekNumber = -1,
                    date = it,
                    note = null,
                    lessons = days[it]?.map { it.toModel() } ?: emptyList()
                )
            }
        }
    }
}
