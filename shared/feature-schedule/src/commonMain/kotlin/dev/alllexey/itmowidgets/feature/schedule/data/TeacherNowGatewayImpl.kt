package dev.alllexey.itmowidgets.feature.schedule.data

import dev.alllexey.itmoapi.core.requireResult
import dev.alllexey.itmoapi.myitmo.MyItmoClient
import dev.alllexey.itmowidgets.core.coroutines.AppDispatchers
import dev.alllexey.itmowidgets.core.demo.DemoMode
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.schedule.TeacherNowGateway
import dev.alllexey.itmowidgets.core.schedule.TeacherNowLesson
import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import dev.alllexey.itmowidgets.feature.schedule.data.demo.DemoSchedule
import dev.alllexey.itmowidgets.feature.schedule.data.mapper.toModel
import dev.alllexey.itmowidgets.feature.schedule.domain.teacherNow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext

/** Today's MyITMO teacher timetable (`/api/schedule/teacher/{isu}`); demo answers from the demo week. */
class TeacherNowGatewayImpl(
    private val myItmo: MyItmoClient,
    private val time: AcademicTimeProvider,
    private val demo: DemoMode,
    private val dispatchers: AppDispatchers,
) : TeacherNowGateway {

    override suspend fun lessonOf(teacherIsu: Int): AppResult<TeacherNowLesson?> {
        val now = time.localNow()
        if (demo.isActive()) {
            val lessons = DemoSchedule.ownDays(now.date, now.date, now.date).flatMap { it.lessons }
                .filter { it.teacherIsu == teacherIsu.toLong() }
            return AppResult.Success(teacherNow(lessons, now.time))
        }
        return try {
            val days = withContext(dispatchers.io) {
                myItmo.schedule.getTeacherSchedule(teacherIsu.toLong(), now.date, now.date).requireResult()
            }
            val lessons = days.filter { it.date == now.date }.flatMap { it.lessons }.map { it.toModel() }
            AppResult.Success(teacherNow(lessons, now.time))
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            AppResult.Failure(error.toAppError())
        }
    }
}
