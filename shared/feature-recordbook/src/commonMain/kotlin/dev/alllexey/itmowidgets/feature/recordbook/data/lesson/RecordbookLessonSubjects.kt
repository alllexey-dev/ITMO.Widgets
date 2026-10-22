package dev.alllexey.itmowidgets.feature.recordbook.data.lesson

import dev.alllexey.itmowidgets.core.lesson.LessonSubject
import dev.alllexey.itmowidgets.core.lesson.LessonSubjectSource
import dev.alllexey.itmowidgets.core.navigation.AppRoutes
import dev.alllexey.itmowidgets.core.navigation.RecordbookSubjectArgs
import dev.alllexey.itmowidgets.core.resources.ResourceScope
import dev.alllexey.itmowidgets.core.resources.SubjectLinksRepository
import dev.alllexey.itmowidgets.core.resources.SubjectLinksState
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.schedule.ScheduleSubject
import dev.alllexey.itmowidgets.core.services.BackendGate
import dev.alllexey.itmowidgets.feature.recordbook.domain.RecordbookRepository
import dev.alllexey.itmowidgets.feature.recordbook.domain.SubjectBindingStore
import dev.alllexey.itmowidgets.feature.recordbook.domain.SubjectContext
import dev.alllexey.itmowidgets.feature.recordbook.domain.SubjectContextResolver
import dev.alllexey.itmowidgets.feature.recordbook.domain.marks.StudyHalf
import dev.alllexey.itmowidgets.feature.recordbook.domain.marks.studyHalf
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookPeriod
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookSubject
import kotlinx.coroutines.flow.first
import kotlinx.datetime.LocalDate

/**
 * The lesson sheet's `Предмет` and `Ссылки 3`: the record book subject of the lesson's half-year that the subject page
 * binds to the lesson's schedule subject ([SubjectContextResolver]: a confirmed binding or the same id, else the one
 * subject of that name not bound elsewhere), and the number of its links.
 *
 * Cheap and possibly stale on purpose (docs/features/schedule.md): the record book is the repository's memory cache,
 * asked from My ITMO only while the process has none; the links are the ones stored on the device, fetched once
 * through [SubjectLinksRepository.refresh] only for a subject the device has never loaded, and none without the
 * connection, where `Материалы` shows its gate. The demo session gets the demo record book and links.
 */
class RecordbookLessonSubjects(
    private val repository: RecordbookRepository,
    private val bindings: SubjectBindingStore,
    private val resolver: SubjectContextResolver,
    private val links: SubjectLinksRepository,
    private val backendGate: BackendGate,
) : LessonSubjectSource {

    override suspend fun subject(subjectId: Long, subjectName: String, date: LocalDate): LessonSubject? {
        val half = StudyHalf.of(date)
        val lesson = listOf(ScheduleSubject(subjectId, subjectName, emptySet()))
        val bound = mutableListOf<Candidate>()
        val proposed = mutableListOf<Candidate>()
        for (candidate in candidates(half)) {
            val confirmed = bindings.get(candidate.subject.disciplineId)
            when (resolver.resolve(candidate.subject, lesson, confirmed)) {
                is SubjectContext.Bound -> bound += candidate
                is SubjectContext.Proposed -> if (confirmed == null) proposed += candidate
                else -> Unit
            }
        }
        val match = bound.firstOrNull() ?: proposed.singleOrNull() ?: return null
        val args = RecordbookSubjectArgs(
            entryId = match.subject.entryId,
            programId = match.programId,
            semester = match.period.semester,
            studyYear = match.period.studyYear,
            disciplineId = match.subject.disciplineId,
            controlType = match.subject.controlType.trim(),
        ).validOrNull() ?: return null
        return LessonSubject(
            page = AppRoutes.RecordbookSubject(args),
            links = linkCount(match.subject, half),
            materials = AppRoutes.RecordbookSubject(args, materials = true),
        )
    }

    private suspend fun candidates(half: StudyHalf): List<Candidate> {
        val programs = repository.cachedPrograms()
            ?: (repository.getPrograms() as? AppResult.Success)?.value
            ?: return emptyList()
        return programs.flatMap { program ->
            program.periods.filter { it.studyHalf() == half }.flatMap { period ->
                val subjects = repository.cachedSubjects(program.id, period.semester)
                    ?: (repository.getSubjects(program.id, period.semester) as? AppResult.Success)?.value.orEmpty()
                subjects.map { Candidate(program.id, period, it) }
            }
        }
    }

    /** What `Материалы` would list now: the current links, without the previous half-year's. */
    private suspend fun linkCount(subject: RecordbookSubject, half: StudyHalf): Int {
        if (!backendGate.isConnected()) return 0
        val scope = ResourceScope(subject.disciplineId, subject.name, half.periodKey)
        // A subject never loaded on the device stays Loading until someone refreshes it, so fetch it here once.
        val state = links.observe(scope).first().takeUnless { it is SubjectLinksState.Loading }
            ?: links.refresh(scope).let { links.observe(scope).first() }
        val snapshot = (state as? SubjectLinksState.Content)?.snapshot ?: return 0
        return (snapshot.mine + snapshot.shared).distinctBy { it.id }.size
    }

    private data class Candidate(val programId: Long, val period: RecordbookPeriod, val subject: RecordbookSubject)
}
