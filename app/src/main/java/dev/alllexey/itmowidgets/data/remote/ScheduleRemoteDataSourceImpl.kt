package dev.alllexey.itmowidgets.data.remote

import api.myitmo.MyItmoApi
import dev.alllexey.itmowidgets.core.ItmoWidgetsApi
import dev.alllexey.itmowidgets.core.model.LessonSyncRequest
import dev.alllexey.itmowidgets.core.storage.AppSettingsStorage
import dev.alllexey.itmowidgets.core.util.ScheduleUtil
import dev.alllexey.itmowidgets.core.utils.toDto
import dev.alllexey.itmowidgets.data.mapper.toModel
import dev.alllexey.itmowidgets.domain.model.schedule.DaySchedule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.awaitResponse
import java.time.LocalDate
import javax.inject.Inject

class ScheduleRemoteDataSourceImpl @Inject constructor(
    private val settings: AppSettingsStorage,
    private val api: MyItmoApi,
    private val widgetsApi: ItmoWidgetsApi
) : ScheduleRemoteDataSource {

    override suspend fun getSchedule(
        userIsu: Int?,
        start: LocalDate,
        end: LocalDate
    ): List<DaySchedule> = withContext(Dispatchers.IO) {

        return@withContext if (userIsu == null) {
            val response = api
                .getPersonalSchedule(start, end)
                .awaitResponse()

            if (!response.isSuccessful) {
                throw RuntimeException("API error ${response.code()}")
            }

            val days = response.body()?.data ?: return@withContext emptyList()

            // sync lessons
            if (settings.getCustomServicesEnabled()) {
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        widgetsApi.syncLessons(LessonSyncRequest(
                            lessons = days.flatMap { it.lessons.map { lesson -> lesson.toDto(it.date) } },
                            from = start,
                            to = end
                        ))
                    } catch (e: Exception) {
                        // todo: proper exception handling
                        println("couldn't sync lessons")
                        e.printStackTrace()
                    }
                }
            }

            days.map {
                DaySchedule(
                    dayNumber = it.dayNumber,
                    weekNumber = it.weekNumber,
                    date = it.date,
                    note = it.note,
                    lessons = it.lessons.map { it.toModel() }
                )
            }

        } else {
            val response = widgetsApi.userLessons(
                userIsu,
                start,
                end
            )

            val days = response.data?.groupBy { it.date } ?: return@withContext emptyList()

            val dates = ScheduleUtil.generateDates(start, end)
            dates.map {
                DaySchedule(
                    dayNumber = it.dayOfWeek.value,
                    weekNumber = -1,
                    date = it,
                    note = null,
                    lessons = days[it]?.map { it.toModel() } ?: emptyList()
                )
            }
        }
    }
}
