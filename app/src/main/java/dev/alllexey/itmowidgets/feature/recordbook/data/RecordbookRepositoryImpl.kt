package dev.alllexey.itmowidgets.feature.recordbook.data

import dev.alllexey.itmoapi.core.ResultResponse
import dev.alllexey.itmoapi.core.requireResult
import dev.alllexey.itmoapi.myitmo.MyItmoClient
import dev.alllexey.itmoapi.myitmo.recordbook.ControlEntry
import dev.alllexey.itmoapi.myitmo.recordbook.RecordBookEntry
import dev.alllexey.itmoapi.myitmo.recordbook.RecordBookTeacher
import dev.alllexey.itmoapi.myitmo.recordbook.Semester
import dev.alllexey.itmoapi.myitmo.recordbook.Specialization
import dev.alllexey.itmowidgets.core.coroutines.AppDispatchers
import dev.alllexey.itmowidgets.core.demo.DemoMode
import dev.alllexey.itmowidgets.core.network.appResultOf
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.session.SessionDataCleaner
import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import dev.alllexey.itmowidgets.core.time.javaNow
import dev.alllexey.itmowidgets.core.time.javaToday
import dev.alllexey.itmowidgets.core.time.javaZone
import dev.alllexey.itmowidgets.feature.recordbook.data.demo.DemoRecordbook
import dev.alllexey.itmowidgets.feature.recordbook.domain.RecordbookRepository
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookControl
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookPeriod
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookProgram
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookSubject
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.withContext

/**
 * One instance per process: the memory cache is what the study screens render first. Requests go through the
 * MyItmoApi 2.x recordbook area; the client's auth plugin owns the single 401 refresh, failures map in [appResultOf].
 */
@Singleton
class RecordbookRepositoryImpl @Inject constructor(
    private val client: MyItmoClient,
    private val time: AcademicTimeProvider,
    private val demo: DemoMode,
    private val dispatchers: AppDispatchers
) : RecordbookRepository, SessionDataCleaner {

    private val api get() = client.recordBook
    private val cache = RecordbookMemoryCache()

    override suspend fun getPrograms(): AppResult<List<RecordbookProgram>> = if (demo.isActive()) {
        AppResult.Success(DemoRecordbook.programs(time.javaToday()))
    } else request(
        call = { api.getSpecializations() },
        transform = { programs -> programs.map { it.toModel() } }
    ).also { result -> if (result is AppResult.Success) cache.programs = result.value }

    override suspend fun getSubjects(
        programId: Long,
        semester: Int
    ): AppResult<List<RecordbookSubject>> = if (demo.isActive()) {
        DemoRecordbook.subjects(programId, semester, time.javaToday(), time.javaZone())?.let { AppResult.Success(it) }
            ?: AppResult.Failure(AppError.NotFound)
    } else request(
        call = { api.getRecordBook(programId, semester) },
        transform = { subjects -> subjects.map { it.toModel() } }
    ).also { result -> if (result is AppResult.Success) cache.subjects[programId to semester] = result.value }

    override suspend fun getControls(entryId: Long): AppResult<List<RecordbookControl>> = if (demo.isActive()) {
        AppResult.Success(DemoRecordbook.controls(entryId, time.javaNow()).orEmpty())
    } else request(
        call = { api.getControlEntries(entryId) },
        transform = { controls -> controls.map { it.toModel() } }
    ).also { result -> if (result is AppResult.Success) cache.controls[entryId] = result.value }

    override fun cachedPrograms(): List<RecordbookProgram>? = cache.programs

    override fun cachedSubjects(programId: Long, semester: Int): List<RecordbookSubject>? =
        cache.subjects[programId to semester]

    override fun cachedControls(entryId: Long): List<RecordbookControl>? = cache.controls[entryId]

    override suspend fun clearSessionData() = cache.clear()

    private class RecordbookMemoryCache {
        @Volatile var programs: List<RecordbookProgram>? = null
        val subjects = ConcurrentHashMap<Pair<Long, Int>, List<RecordbookSubject>>()
        val controls = ConcurrentHashMap<Long, List<RecordbookControl>>()

        fun clear() {
            programs = null
            subjects.clear()
            controls.clear()
        }
    }

    private suspend fun <T : Any, R> request(
        call: suspend () -> ResultResponse<T>,
        transform: (T) -> R
    ): AppResult<R> = appResultOf {
        val result = withContext(dispatchers.io) {
            call().requireResult()
        }
        transform(result)
    }

    private fun Specialization.toModel() = RecordbookProgram(
        id = mainPlan,
        name = specializationName.trim(),
        periods = semesters.map { it.toModel() }
    )

    private fun Semester.toModel() = RecordbookPeriod(
        studyYear = studyYear.trim(),
        semester = semester,
        course = course,
        actual = actual
    )

    private fun RecordBookEntry.toModel() = RecordbookSubject(
        name = name.trim(),
        disciplineId = disciplineId,
        entryId = estId,
        controlType = controlType.trim(),
        score = currentScore,
        rate = rate?.trim(),
        attempt = attempt,
        examDate = examDate,
        hasDetails = haveTree,
        teacherName = teacher?.displayName(),
        lmsLink = lmsLink?.trim()?.takeIf { it.isNotBlank() }
    )

    private fun ControlEntry.toModel() = RecordbookControl(
        id = id,
        name = controlName.trim(),
        score = rate,
        minimum = minValue,
        maximum = maxValue,
        required = required,
        date = date,
        teacherName = teacher?.displayName(),
        parentId = parentId
    )

    private fun RecordBookTeacher.displayName(): String? {
        return listOfNotNull(surname, name, patronymic)
            .map(String::trim)
            .filter(String::isNotBlank)
            .joinToString(" ")
            .takeIf(String::isNotBlank)
    }
}
