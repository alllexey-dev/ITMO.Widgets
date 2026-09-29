package dev.alllexey.itmowidgets.feature.schedule.data

import api.myitmo.MyItmoApi
import dev.alllexey.itmowidgets.core.network.toAppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.schedule.TeacherLessons
import dev.alllexey.itmowidgets.core.schedule.TeacherLessonsGateway
import dev.alllexey.itmowidgets.core.session.SessionDataCleaner
import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import dev.alllexey.itmowidgets.feature.schedule.domain.StudyPeriods
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import retrofit2.awaitResponse

/** Past periods never change and stay in memory until the session ends; the current one is read every time. */
@Singleton
class TeacherLessonsGatewayImpl @Inject constructor(
    private val api: MyItmoApi,
    private val time: AcademicTimeProvider,
) : TeacherLessonsGateway, SessionDataCleaner {

    private val pastPeriods = ConcurrentHashMap<LocalDate, List<TaughtLesson>>()
    private val generation = AtomicLong()

    override suspend fun taughtBy(teacherIsu: Int): AppResult<TeacherLessons> {
        val periods = StudyPeriods.recent(time.today(), HISTORY_PERIODS)
        val lessons = mutableListOf<TaughtLesson>()
        var firstError: Exception? = null
        var loaded = 0
        for ((index, period) in periods.withIndex()) {
            try {
                lessons += lessonsOf(period, current = index == 0)
                loaded += 1
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                if (firstError == null) firstError = error
            }
        }
        if (loaded == 0) return AppResult.Failure(checkNotNull(firstError).toAppError())
        val taught = lessons.filter { it.teacherIsu == teacherIsu.toLong() }
        return AppResult.Success(TeacherLessons(
            flowIds = taught.mapTo(linkedSetOf(), TaughtLesson::flowId),
            subjects = taught.map(TaughtLesson::subject).filter(String::isNotEmpty).distinct(),
        ))
    }

    override suspend fun clearSessionData() {
        generation.incrementAndGet()
        pastPeriods.clear()
    }

    private suspend fun lessonsOf(period: ClosedRange<LocalDate>, current: Boolean): List<TaughtLesson> {
        if (!current) pastPeriods[period.start]?.let { return it }
        val started = generation.get()
        val lessons = request(period)
        // An answer requested for the previous account must not fill the next account's cache.
        if (!current && generation.get() == started) pastPeriods[period.start] = lessons
        return lessons
    }

    private suspend fun request(period: ClosedRange<LocalDate>): List<TaughtLesson> = withContext(Dispatchers.IO) {
        val response = api.getPersonalSchedule(period.start, period.endInclusive).awaitResponse()
        if (!response.isSuccessful) throw HttpException(response)
        response.body()?.data.orEmpty()
            .sortedByDescending { it.date }
            .flatMap { day ->
                day.lessons.orEmpty()
                    .filter { it.flowTypeId == ACADEMIC_FLOW }
                    .map { TaughtLesson(it.teacherId, it.flowId.toLong(), it.subject?.trim().orEmpty()) }
            }
    }

    /** [subject] is trimmed and may be empty when My ITMO sent none. */
    private data class TaughtLesson(val teacherIsu: Long?, val flowId: Long, val subject: String)

    private companion object {
        const val HISTORY_PERIODS = 8

        /** MyITMO flow types: 2 academic pairs, 3 sport, 5 room bookings. */
        const val ACADEMIC_FLOW = 2
    }
}
