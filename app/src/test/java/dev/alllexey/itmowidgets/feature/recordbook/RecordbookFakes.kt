package dev.alllexey.itmowidgets.feature.recordbook

import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.schedule.ScheduleRefreshGateway
import dev.alllexey.itmowidgets.core.schedule.SubjectLesson
import dev.alllexey.itmowidgets.core.schedule.SubjectLessonsGateway
import dev.alllexey.itmowidgets.core.sport.SportScorePeriod
import dev.alllexey.itmowidgets.core.sport.SportScoreRepository
import dev.alllexey.itmowidgets.core.sport.SportScoreSummary
import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import dev.alllexey.itmowidgets.feature.recordbook.domain.BarsPreferenceRepository
import dev.alllexey.itmowidgets.feature.recordbook.domain.BarsRecordbookRepository
import dev.alllexey.itmowidgets.feature.recordbook.domain.BarsSubjectDetails
import dev.alllexey.itmowidgets.feature.recordbook.domain.RecordbookRepository
import dev.alllexey.itmowidgets.feature.recordbook.domain.SubjectBindingStore
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.BarsJournalReference
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookControl
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookPeriod
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookProgram
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookSubject
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

fun recordbookSubject(id: Long = 42L, name: String = "Тестовый предмет", details: Boolean = true) = RecordbookSubject(
    name, 1L, id, "Экзамен", 75.0, "4/C", 1, null, details, "Тестовый преподаватель"
)

fun recordbookProgram() = RecordbookProgram(1L, "Тестовая программа", listOf(
    RecordbookPeriod("2026/2027", 3, 2, true),
    RecordbookPeriod("2025/2026", 2, 1, false),
    RecordbookPeriod("2025/2026", 1, 1, false)
))

class FakeRecordbookRepository : RecordbookRepository {
    var programs: AppResult<List<RecordbookProgram>> = AppResult.Success(listOf(recordbookProgram()))
    var subjects: AppResult<List<RecordbookSubject>> = AppResult.Success(listOf(recordbookSubject()))
    var controls: AppResult<List<RecordbookControl>> = AppResult.Success(emptyList())
    var subjectLoader: (suspend (Int) -> AppResult<List<RecordbookSubject>>)? = null
    var programRequests = 0
    var controlRequests = 0
    val subjectRequests = mutableListOf<Int>()
    override suspend fun getPrograms(): AppResult<List<RecordbookProgram>> { programRequests++; return programs }
    override suspend fun getSubjects(programId: Long, semester: Int): AppResult<List<RecordbookSubject>> {
        subjectRequests += semester
        return subjectLoader?.invoke(semester) ?: subjects
    }
    override suspend fun getControls(entryId: Long): AppResult<List<RecordbookControl>> { controlRequests++; return controls }
}

class FakeSportScoreRepository : SportScoreRepository {
    var periods: AppResult<List<SportScorePeriod>> = AppResult.Success(listOf(SportScorePeriod(9, "Осень 2025/2026"), SportScorePeriod(10, "Весна 2025/2026")))
    var score: AppResult<SportScoreSummary> = AppResult.Success(SportScoreSummary(64, 26))
    var periodRequests = 0
    val scoreRequests = mutableListOf<Long>()
    override suspend fun getScorePeriods(): AppResult<List<SportScorePeriod>> { periodRequests++; return periods }
    override suspend fun getScoreSummary(semesterId: Long): AppResult<SportScoreSummary> { scoreRequests += semesterId; return score }
}

class FixedAcademicTime(date: String = "2026-09-07") : AcademicTimeProvider {
    private val date = LocalDate.parse(date)
    override val zoneId: ZoneId = ZoneId.of("Europe/Moscow")
    override fun today() = date
    override fun now() = date.atStartOfDay(zoneId).toOffsetDateTime()
}

class FakeBarsRepository : BarsRecordbookRepository {
    var subjects: AppResult<List<RecordbookSubject>> = AppResult.Success(emptyList())
    var details: AppResult<BarsSubjectDetails> = AppResult.Failure(AppError.NotFound)
    var subjectLoader: (suspend () -> AppResult<List<RecordbookSubject>>)? = null
    val periodRequests = mutableListOf<RecordbookPeriod>()
    val journalRequests = mutableListOf<BarsJournalReference>()
    override suspend fun getSubjects(period: RecordbookPeriod): AppResult<List<RecordbookSubject>> {
        periodRequests += period
        return subjectLoader?.invoke() ?: subjects
    }
    override suspend fun getSubject(journal: BarsJournalReference): AppResult<BarsSubjectDetails> {
        journalRequests += journal
        return details
    }
}

class FakeBarsPreference(var enabled: Boolean = false) : BarsPreferenceRepository {
    var writes = 0
    override suspend fun isEnabled() = enabled
    override suspend fun setEnabled(enabled: Boolean): AppResult<Unit> {
        this.enabled = enabled
        writes++
        return AppResult.Success(Unit)
    }
}

fun barsJournal(id: Long = 8L) = BarsJournalReference(id, "flow", "7", 2025, 2)

fun barsSubject(name: String = "Тестовый предмет", score: Double? = 91.5, rate: String? = "5/A") =
    RecordbookSubject(name, 900L, 8L, "Экзамен", score, rate, 1, null, true, null, barsJournal(), false)

class FakeSubjectLessonsGateway : SubjectLessonsGateway {
    val lessons = MutableStateFlow<List<SubjectLesson>>(emptyList())
    val observedWindows = mutableListOf<Pair<LocalDate, LocalDate>>()
    override fun observeOwnLessons(start: LocalDate, end: LocalDate): Flow<List<SubjectLesson>> {
        observedWindows += start to end
        return lessons.map { list -> list.filter { !it.date.isBefore(start) && !it.date.isAfter(end) } }
    }
}

class FakeScheduleRefreshGateway : ScheduleRefreshGateway {
    var result: AppResult<Unit> = AppResult.Success(Unit)
    val requests = mutableListOf<Pair<LocalDate, LocalDate>>()
    override suspend fun refreshOwnSchedule(startDate: LocalDate, endDate: LocalDate): AppResult<Unit> {
        requests += startDate to endDate
        return result
    }
}

class FakeSubjectBindingStore : SubjectBindingStore {
    val bindings = mutableMapOf<Long, Long>()
    override suspend fun get(disciplineId: Long): Long? = bindings[disciplineId]
    override suspend fun put(disciplineId: Long, subjectId: Long) { bindings[disciplineId] = subjectId }
    override suspend fun remove(disciplineId: Long) { bindings.remove(disciplineId) }
}

fun subjectLesson(
    pairId: Long, date: String, subjectId: Long = 1L, name: String = "Тестовый предмет", typeId: Int = 1,
    teacherIsu: Long? = 300001, teacherFio: String? = "Тестовый преподаватель", flowId: Long = 10L, start: String = "09:30"
) = SubjectLesson(
    pairId = pairId, date = LocalDate.parse(date), start = LocalTime.parse(start), end = LocalTime.parse(start).plusMinutes(90),
    typeId = typeId, type = "Лекция", subjectId = subjectId, subjectName = name, flowId = flowId, teacherIsu = teacherIsu,
    teacherFio = teacherFio, room = "1506", building = "Кронверкский проспект, 49", formatId = 1
)
