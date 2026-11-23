package dev.alllexey.itmowidgets.feature.recordbook.data

import dev.alllexey.itmoapi.myitmo.MyItmoClient
import dev.alllexey.itmoapi.myitmo.recordbook.ControlEntry
import dev.alllexey.itmoapi.myitmo.recordbook.RecordBookEntry
import dev.alllexey.itmoapi.myitmo.recordbook.RecordBookTeacher
import dev.alllexey.itmoapi.myitmo.recordbook.Semester
import dev.alllexey.itmoapi.myitmo.recordbook.Specialization
import dev.alllexey.itmowidgets.core.coroutines.AppDispatchers
import dev.alllexey.itmowidgets.core.demo.DemoMode
import dev.alllexey.itmowidgets.core.network.toMyItmoAppError
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.result.appResultOf
import dev.alllexey.itmowidgets.core.session.SessionDataCleaner
import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import dev.alllexey.itmowidgets.feature.recordbook.data.demo.DemoRecordbook
import dev.alllexey.itmowidgets.feature.recordbook.domain.RecordbookRepository
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookControl
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookPeriod
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookProgram
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookSubject
import kotlin.concurrent.Volatile
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * One instance per process (the `recordbookModule` single): the memory cache is what the study screens render first.
 * Requests go through the MyItmoApi 2.x recordbook area; the client's auth plugin owns the single 401 refresh,
 * failures map through the core `toMyItmoAppError`.
 */
class RecordbookRepositoryImpl(
    private val client: MyItmoClient,
    private val time: AcademicTimeProvider,
    private val demo: DemoMode,
    private val dispatchers: AppDispatchers
) : RecordbookRepository, SessionDataCleaner {

    private val api get() = client.recordBook
    private val cache = RecordbookMemoryCache()

    override suspend fun getPrograms(): AppResult<List<RecordbookProgram>> = if (demo.isActive()) {
        AppResult.Success(DemoRecordbook.programs(time.today()))
    } else request(
        call = { api.getSpecializations() },
        transform = { programs -> programs.map { it.toModel() } }
    ).also { result -> if (result is AppResult.Success) cache.putPrograms(result.value) }

    override suspend fun getSubjects(
        programId: Long,
        semester: Int
    ): AppResult<List<RecordbookSubject>> = if (demo.isActive()) {
        DemoRecordbook.subjects(programId, semester, time.today(), time.timeZone)?.let { AppResult.Success(it) }
            ?: AppResult.Failure(AppError.NotFound)
    } else request(
        call = { api.getRecordBook(programId, semester) },
        transform = { subjects -> subjects.map { it.toModel() } }
    ).also { result -> if (result is AppResult.Success) cache.putSubjects(programId to semester, result.value) }

    override suspend fun getControls(entryId: Long): AppResult<List<RecordbookControl>> = if (demo.isActive()) {
        AppResult.Success(DemoRecordbook.controls(entryId, time.now()).orEmpty())
    } else request(
        call = { api.getControlEntries(entryId) },
        transform = { controls -> controls.map { it.toModel() } }
    ).also { result -> if (result is AppResult.Success) cache.putControls(entryId, result.value) }

    override fun cachedPrograms(): List<RecordbookProgram>? = cache.programs

    override fun cachedSubjects(programId: Long, semester: Int): List<RecordbookSubject>? =
        cache.subjects[programId to semester]

    override fun cachedControls(entryId: Long): List<RecordbookControl>? = cache.controls[entryId]

    override suspend fun clearSessionData() = cache.clear()

    /**
     * Readers take the current immutable maps without a lock (the cached getters are not suspending); writers replace
     * them under [lock], so two answers arriving together never drop each other's entry.
     */
    private class RecordbookMemoryCache {
        private val lock = Mutex()
        @Volatile var programs: List<RecordbookProgram>? = null
            private set
        @Volatile var subjects: Map<Pair<Long, Int>, List<RecordbookSubject>> = emptyMap()
            private set
        @Volatile var controls: Map<Long, List<RecordbookControl>> = emptyMap()
            private set

        suspend fun putPrograms(value: List<RecordbookProgram>) = lock.withLock { programs = value }

        suspend fun putSubjects(key: Pair<Long, Int>, value: List<RecordbookSubject>) =
            lock.withLock { subjects = subjects + (key to value) }

        suspend fun putControls(entryId: Long, value: List<RecordbookControl>) =
            lock.withLock { controls = controls + (entryId to value) }

        suspend fun clear() = lock.withLock {
            programs = null
            subjects = emptyMap()
            controls = emptyMap()
        }
    }

    private suspend fun <T : Any, R> request(
        call: suspend () -> T,
        transform: (T) -> R
    ): AppResult<R> = appResultOf(Exception::toMyItmoAppError) {
        val result = withContext(dispatchers.io) {
            call()
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
