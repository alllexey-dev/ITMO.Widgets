package dev.alllexey.itmowidgets.feature.recordbook.data.bars

import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.session.SessionDataCleaner
import dev.alllexey.itmowidgets.feature.recordbook.domain.BarsRecordbookRepository
import dev.alllexey.itmowidgets.feature.recordbook.domain.BarsSubjectDetails
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.BarsJournalReference
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookControl
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookPeriod
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookSubject
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

@Singleton
class BarsRecordbookRepositoryImpl @Inject constructor(
    private val client: BarsClient
) : BarsRecordbookRepository, SessionDataCleaner {
    private val mapper = BarsRecordbookMapper()
    /** The list already downloads every journal; keeping its controls costs no request. */
    private val controls = ConcurrentHashMap<BarsJournalReference, List<RecordbookControl>>()

    override suspend fun getSubjects(period: RecordbookPeriod): AppResult<List<RecordbookSubject>> = client.account {
        val api = client.bars.api
        val yearStart = period.studyYear.substringBefore('/').toInt()
        selectPeriod(period.studyYear, period.semesterInCourse == 1)
        val disciplines = execute { api.getDisciplines(true) }
        val groups = execute { api.getGroupsAndFlows(null) }
        // A student's marks are plan-scoped. Lecture/practice flows can reference the same plan.
        val references = disciplines.flatMap { it.checkpointPlanIds }.distinct().mapNotNull { plan ->
            val group = groups.firstOrNull { plan in it.checkpointPlanIds } ?: return@mapNotNull null
            BarsJournalReference(plan, group.type, group.identifier, yearStart, period.semesterInCourse)
        }
        coroutineScope {
            references.map { reference ->
                async {
                    val journal = execute { api.getStudentJournal(reference.planId, reference.type, reference.identifier) }
                    val subject = mapper.subject(journal, reference, owner.toString())
                    // A journal whose controls do not map still lists its subject.
                    runCatching { mapper.controls(journal, reference, owner.toString()) }.getOrNull()
                        ?.let { controls[reference] = it }
                    subject
                }
            }.awaitAll()
        }
    }

    override suspend fun getSubject(journal: BarsJournalReference): AppResult<BarsSubjectDetails> = client.account {
        selectPeriod("${journal.yearStart}/${journal.yearStart + 1}", journal.semester == 1)
        val response = execute { client.bars.api.getStudentJournal(journal.planId, journal.type, journal.identifier) }
        BarsSubjectDetails(mapper.subject(response, journal, owner.toString()), mapper.controls(response, journal, owner.toString()))
            .also { controls[journal] = it.controls }
    }

    override fun cachedControls(journal: BarsJournalReference): List<RecordbookControl>? = controls[journal]

    override suspend fun clearSessionData() = controls.clear()
}
