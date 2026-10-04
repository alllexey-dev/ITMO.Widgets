package dev.alllexey.itmowidgets.feature.recordbook

import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
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
    var programLoader: (suspend () -> AppResult<List<RecordbookProgram>>)? = null
    var cachedPrograms: List<RecordbookProgram>? = null
    var cachedSubjects: List<RecordbookSubject>? = null
    var cachedControls: List<RecordbookControl>? = null
    var programRequests = 0
    var controlRequests = 0
    val subjectRequests = mutableListOf<Int>()
    override suspend fun getPrograms(): AppResult<List<RecordbookProgram>> { programRequests++; return programLoader?.invoke() ?: programs }
    override fun cachedPrograms() = cachedPrograms
    override fun cachedSubjects(programId: Long, semester: Int) = cachedSubjects
    override fun cachedControls(entryId: Long) = cachedControls
    override suspend fun getSubjects(programId: Long, semester: Int): AppResult<List<RecordbookSubject>> {
        subjectRequests += semester
        return subjectLoader?.invoke(semester) ?: subjects
    }
    override suspend fun getControls(entryId: Long): AppResult<List<RecordbookControl>> { controlRequests++; return controls }
}

class FakeBarsRepository : BarsRecordbookRepository {
    var subjects: AppResult<List<RecordbookSubject>> = AppResult.Success(emptyList())
    var details: AppResult<BarsSubjectDetails> = AppResult.Failure(AppError.NotFound)
    var subjectLoader: (suspend () -> AppResult<List<RecordbookSubject>>)? = null
    val periodRequests = mutableListOf<RecordbookPeriod>()
    val journalRequests = mutableListOf<BarsJournalReference>()
    var cachedControls: Map<BarsJournalReference, List<RecordbookControl>> = emptyMap()
    override fun cachedControls(journal: BarsJournalReference) = cachedControls[journal]
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

class FakeSubjectBindingStore : SubjectBindingStore {
    val bindings = mutableMapOf<Long, Long>()
    override suspend fun get(disciplineId: Long): Long? = bindings[disciplineId]
    override suspend fun put(disciplineId: Long, subjectId: Long) { bindings[disciplineId] = subjectId }
    override suspend fun remove(disciplineId: Long) { bindings.remove(disciplineId) }
}
