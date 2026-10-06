package dev.alllexey.itmowidgets.feature.recordbook.data.bars

import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.session.SessionDataCleaner
import dev.alllexey.itmowidgets.feature.recordbook.domain.BarsRecordbookRepository
import dev.alllexey.itmowidgets.feature.recordbook.domain.BarsSubjectDetails
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.BarsJournalReference
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookControl
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookPeriod
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookSubject
import kotlin.concurrent.Volatile
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** One instance per process (the `recordbookModule` single), so the screens and sign-out share the controls cache. */
class BarsRecordbookRepositoryImpl(
    private val client: BarsClient
) : BarsRecordbookRepository, SessionDataCleaner {
    private val mapper = BarsRecordbookMapper()

    /**
     * The list already downloads every journal; keeping its controls costs no request. [cachedControls] reads the
     * current immutable map without a lock; writers replace it under [controlsLock].
     */
    @Volatile private var controls: Map<BarsJournalReference, List<RecordbookControl>> = emptyMap()
    private val controlsLock = Mutex()

    override suspend fun getSubjects(period: RecordbookPeriod): AppResult<List<RecordbookSubject>> = client.account {
        val yearStart = period.studyYear.substringBefore('/').toInt()
        selectPeriod(period.studyYear, period.semesterInCourse == 1)
        val references = journalReferences(yearStart, period.semesterInCourse)
        coroutineScope {
            references.map { reference ->
                async {
                    val journal = execute { getStudentJournal(reference.planId, reference.type, reference.identifier) }
                    val subject = mapper.subject(journal, reference, owner.toString())
                    // A journal whose controls do not map still lists its subject.
                    runCatching { mapper.controls(journal, reference, owner.toString()) }.getOrNull()
                        ?.let { keepControls(reference, it) }
                    subject
                }
            }.awaitAll()
        }
    }

    override suspend fun getSubject(journal: BarsJournalReference): AppResult<BarsSubjectDetails> = client.account {
        selectPeriod("${journal.yearStart}/${journal.yearStart + 1}", journal.semester == 1)
        val response = execute { getStudentJournal(journal.planId, journal.type, journal.identifier) }
        BarsSubjectDetails(mapper.subject(response, journal, owner.toString()), mapper.controls(response, journal, owner.toString()))
            .also { keepControls(journal, it.controls) }
    }

    override fun cachedControls(journal: BarsJournalReference): List<RecordbookControl>? = controls[journal]

    override suspend fun clearSessionData() = controlsLock.withLock { controls = emptyMap() }

    private suspend fun keepControls(journal: BarsJournalReference, value: List<RecordbookControl>) =
        controlsLock.withLock { controls = controls + (journal to value) }
}
