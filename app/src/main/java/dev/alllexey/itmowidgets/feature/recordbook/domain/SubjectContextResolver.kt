package dev.alllexey.itmowidgets.feature.recordbook.domain

import dev.alllexey.itmowidgets.core.schedule.ScheduleSubject
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookSubject
import javax.inject.Inject

/** How a recordbook discipline maps onto the schedule; only exact ids and confirmed links bind silently. */
sealed interface SubjectContext {
    enum class Source { EXACT, CONFIRMED }

    data class Bound(val subjectId: Long, val source: Source) : SubjectContext

    /** One subject shares the name; the user decides whether it is the same thing. */
    data class Proposed(val candidate: ScheduleSubject) : SubjectContext

    data class Ambiguous(val candidates: List<ScheduleSubject>) : SubjectContext

    data object Unmatched : SubjectContext

    /** Physical education never appears in the academic schedule. */
    data object NotApplicable : SubjectContext
}

class SubjectContextResolver @Inject constructor() {

    fun resolve(subject: RecordbookSubject, candidates: List<ScheduleSubject>, confirmed: Long?): SubjectContext {
        if (subject.isPhysicalEducation) return SubjectContext.NotApplicable
        val byId = candidates.associateBy { it.subjectId }
        if (confirmed != null && confirmed in byId) return SubjectContext.Bound(confirmed, SubjectContext.Source.CONFIRMED)
        if (subject.disciplineId in byId) return SubjectContext.Bound(subject.disciplineId, SubjectContext.Source.EXACT)
        val key = subjectNameKey(subject.name)
        val byName = candidates.filter { subjectNameKey(it.name) == key }
        return when (byName.size) {
            0 -> SubjectContext.Unmatched
            1 -> SubjectContext.Proposed(byName.single())
            else -> SubjectContext.Ambiguous(byName)
        }
    }
}
