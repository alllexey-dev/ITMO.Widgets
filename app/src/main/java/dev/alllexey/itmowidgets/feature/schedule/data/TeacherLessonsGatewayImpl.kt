package dev.alllexey.itmowidgets.feature.schedule.data

import api.myitmo.MyItmoApi
import dev.alllexey.itmowidgets.core.network.toAppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.schedule.TeacherLessons
import dev.alllexey.itmowidgets.core.schedule.TeacherLessonsGateway
import dev.alllexey.itmowidgets.core.session.SessionDataCleaner
import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import dev.alllexey.itmowidgets.feature.schedule.domain.StudyWeeks
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import retrofit2.HttpException

/**
 * Asks for every week of [StudyWeeks.sampled] at once. Weeks that ended before today never change: they are kept on
 * disk until the session ends and come without a request; the current week is read every time.
 */
@Singleton
class TeacherLessonsGatewayImpl @Inject constructor(
    private val api: MyItmoApi,
    private val store: TeacherWeeksFileStore,
    private val time: AcademicTimeProvider,
) : TeacherLessonsGateway, SessionDataCleaner {

    private val cacheLock = Mutex()
    /** The stored finished weeks, read from disk once per session; null until then. */
    private var finished: Map<LocalDate, List<WeekLesson>>? = null
    private val generation = AtomicLong()

    override fun taughtBy(teacherIsu: Int): Flow<AppResult<TeacherLessons>> = channelFlow {
        val today = time.today()
        val started = generation.get()
        val weeks = StudyWeeks.sampled(today)
        val past = weeks.filter { it.endInclusive < today }.mapTo(hashSetOf()) { it.start }
        val arrived = finishedWeeks().filterKeys { it in past }.toMutableMap()
        val arrivalLock = Mutex()
        if (arrived.isNotEmpty()) send(AppResult.Success(arrived.lessonsWith(teacherIsu)))
        val failures = weeks.filter { it.start !in arrived }.map { week ->
            async(Dispatchers.IO) {
                val lessons = try {
                    request(week)
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (error: Exception) {
                    return@async error
                }
                if (week.start in past) remember(started, week.start, lessons, past)
                arrivalLock.withLock {
                    arrived[week.start] = lessons
                    send(AppResult.Success(arrived.lessonsWith(teacherIsu)))
                }
                null
            }
        }.awaitAll().filterNotNull()
        if (arrived.isEmpty()) send(AppResult.Failure(failures.first().toAppError()))
    }

    override suspend fun clearSessionData() {
        generation.incrementAndGet()
        cacheLock.withLock {
            finished = null
            withContext(Dispatchers.IO) { store.clear() }
        }
    }

    private suspend fun finishedWeeks(): Map<LocalDate, List<WeekLesson>> = cacheLock.withLock { loaded() }

    /** Keeps only the weeks still sampled, so old academic years drop out of the file. */
    private suspend fun remember(started: Long, monday: LocalDate, lessons: List<WeekLesson>, sampled: Set<LocalDate>) {
        cacheLock.withLock {
            // An answer requested for the previous account must not fill the next account's cache.
            if (generation.get() != started) return
            val next = (loaded() + (monday to lessons)).filterKeys { it in sampled }
            finished = next
            try {
                withContext(Dispatchers.IO) { store.write(next) }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                // The disk copy is an optimisation; this session still has the week in memory.
            }
        }
    }

    /** A corrupt file is ignored and replaced with the next finished week. Must hold [cacheLock]. */
    private suspend fun loaded(): Map<LocalDate, List<WeekLesson>> = finished ?: try {
        withContext(Dispatchers.IO) { store.read() }
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Exception) {
        emptyMap()
    }.also { finished = it }

    private fun request(week: ClosedRange<LocalDate>): List<WeekLesson> {
        val response = api.getPersonalSchedule(week.start, week.endInclusive).execute()
        if (!response.isSuccessful) throw HttpException(response)
        return response.body()?.data.orEmpty()
            .sortedByDescending { it.date }
            .flatMap { day ->
                day.lessons.orEmpty()
                    .filter { it.flowTypeId == ACADEMIC_FLOW }
                    .mapNotNull { lesson ->
                        lesson.teacherId?.let { WeekLesson(it, lesson.flowId.toLong(), lesson.subject?.trim().orEmpty()) }
                    }
            }
            .distinct()
    }

    private fun Map<LocalDate, List<WeekLesson>>.lessonsWith(teacherIsu: Int): TeacherLessons {
        val taught = entries.sortedByDescending { it.key }
            .flatMap { it.value }
            .filter { it.teacherIsu == teacherIsu.toLong() }
        return TeacherLessons(
            flowIds = taught.mapTo(linkedSetOf(), WeekLesson::flowId),
            subjects = taught.map(WeekLesson::subject).filter(String::isNotEmpty).distinct(),
        )
    }

    private companion object {
        /** MyITMO flow types: 2 academic pairs, 3 sport, 5 room bookings. */
        const val ACADEMIC_FLOW = 2
    }
}
