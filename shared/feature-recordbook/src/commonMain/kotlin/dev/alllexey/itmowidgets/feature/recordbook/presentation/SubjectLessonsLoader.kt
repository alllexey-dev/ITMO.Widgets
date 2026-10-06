package dev.alllexey.itmowidgets.feature.recordbook.presentation

import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.schedule.ScheduleRefreshGateway
import dev.alllexey.itmowidgets.core.schedule.SubjectLesson
import dev.alllexey.itmowidgets.core.schedule.SubjectLessonsGateway
import dev.alllexey.itmowidgets.core.schedule.subjectsIn
import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import dev.alllexey.itmowidgets.feature.recordbook.domain.SubjectBindingStore
import dev.alllexey.itmowidgets.feature.recordbook.domain.SubjectContext
import dev.alllexey.itmowidgets.feature.recordbook.domain.SubjectContextResolver
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookSubject
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.plus

/** The lessons section of the subject page; [teachers] are `null` when the page keeps the ones it shows. */
data class SubjectLessonsUpdate(val lessons: SubjectLessonsState, val teachers: List<SubjectTeacher>?)

/**
 * The subject's lessons in the next [WINDOW_DAYS] of the own schedule and the schedule subject they belong to: a
 * stored binding, an exact match, or a proposal the viewer confirms or rejects. One instance serves one page.
 */
class SubjectLessonsLoader @Inject constructor(
    private val lessonsGateway: SubjectLessonsGateway,
    private val scheduleRefresh: ScheduleRefreshGateway,
    private val bindings: SubjectBindingStore,
    private val contextResolver: SubjectContextResolver,
    private val time: AcademicTimeProvider,
) {
    /** Bumped after a binding is written so the lesson flow is re-evaluated. */
    private val bindingVersion = MutableStateFlow(0)

    /**
     * Refreshes the own schedule of the window once, then follows its lessons. A binding written through [bind] and
     * a change of [proposalRejected] match them again; [fallbackTeachers] stand in while no own lesson names one.
     */
    fun observe(
        subject: RecordbookSubject,
        proposalRejected: Flow<Boolean>,
        fallbackTeachers: List<SubjectTeacher>,
    ): Flow<SubjectLessonsUpdate> = channelFlow {
        val today = time.today()
        val end = today.plus(WINDOW_DAYS, DateTimeUnit.DAY)
        val refresh = scheduleRefresh.refreshOwnSchedule(today, end)
        combine(lessonsGateway.observeOwnLessons(today, end), bindingVersion, proposalRejected) { lessons, _, rejected ->
            lessons to rejected
        }.collectLatest { (lessons, rejected) ->
            if (lessons.isEmpty() && refresh is AppResult.Failure) {
                send(SubjectLessonsUpdate(SubjectLessonsState.Error(refresh.error), teachers = null))
                return@collectLatest
            }
            send(match(subject, lessons, rejected, fallbackTeachers))
        }
    }

    /** The viewer agrees that [subjectId] in the schedule is [disciplineId]. */
    suspend fun bind(disciplineId: Long, subjectId: Long) {
        bindings.put(disciplineId, subjectId)
        bindingVersion.update { it + 1 }
    }

    private suspend fun match(
        subject: RecordbookSubject,
        lessons: List<SubjectLesson>,
        rejected: Boolean,
        fallbackTeachers: List<SubjectTeacher>,
    ): SubjectLessonsUpdate {
        val context = contextResolver.resolve(subject, subjectsIn(lessons), bindings.get(subject.disciplineId))
        val own = (context as? SubjectContext.Bound)?.let { bound -> lessons.filter { it.subjectId == bound.subjectId } }
        val teachers = own?.let(::teachersOf)?.takeIf { it.isNotEmpty() } ?: fallbackTeachers
        val state = when (context) {
            is SubjectContext.Bound ->
                if (own.isNullOrEmpty()) SubjectLessonsState.Unmatched
                else SubjectLessonsState.Content(own, context.source)
            is SubjectContext.Proposed ->
                if (rejected) SubjectLessonsState.Unmatched else SubjectLessonsState.Proposed(context.candidate)
            is SubjectContext.Ambiguous ->
                if (rejected) SubjectLessonsState.Unmatched else SubjectLessonsState.Ambiguous(context.candidates)
            SubjectContext.Unmatched -> SubjectLessonsState.Unmatched
            SubjectContext.NotApplicable -> SubjectLessonsState.Hidden
        }
        return SubjectLessonsUpdate(state, teachers)
    }

    /** Distinct people by ISU (or name without one), each with the lesson types they run. */
    private fun teachersOf(lessons: List<SubjectLesson>): List<SubjectTeacher> =
        lessons.filter { !it.teacherFio.isNullOrBlank() }
            .groupBy { it.teacherIsu?.toString() ?: it.teacherFio!!.trim() }
            .values
            .map { group ->
                val roles = group.groupingBy { it.typeId }.eachCount().entries.sortedByDescending { it.value }.map { it.key }
                SubjectTeacher(group.first().teacherFio!!.trim(), group.first().teacherIsu, roles)
            }

    private companion object {
        const val WINDOW_DAYS = 28
    }
}
