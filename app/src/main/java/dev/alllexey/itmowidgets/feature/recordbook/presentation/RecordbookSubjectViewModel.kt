package dev.alllexey.itmowidgets.feature.recordbook.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.schedule.ScheduleRefreshGateway
import dev.alllexey.itmowidgets.core.schedule.SubjectLesson
import dev.alllexey.itmowidgets.core.schedule.SubjectLessonsGateway
import dev.alllexey.itmowidgets.core.schedule.subjectsIn
import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import dev.alllexey.itmowidgets.feature.recordbook.domain.BarsRecordbookRepository
import dev.alllexey.itmowidgets.feature.recordbook.domain.RecordbookRepository
import dev.alllexey.itmowidgets.feature.recordbook.domain.RecordbookSportResolver
import dev.alllexey.itmowidgets.feature.recordbook.domain.RecordbookSportState
import dev.alllexey.itmowidgets.feature.recordbook.domain.SubjectBindingStore
import dev.alllexey.itmowidgets.feature.recordbook.domain.SubjectContext
import dev.alllexey.itmowidgets.feature.recordbook.domain.SubjectContextResolver
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.BarsJournalReference
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookControl
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookPeriod
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookSubject
import dev.alllexey.itmowidgets.feature.recordbook.domain.withBars
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface RecordbookSubjectUiState {
    data object Loading : RecordbookSubjectUiState
    data class Content(
        val subject: RecordbookSubject,
        val controls: List<RecordbookControl>,
        val sport: RecordbookSportState?,
        val controlsError: AppError? = null,
        val refreshing: Boolean = false,
        val refreshError: AppError? = null,
        /** BARS journal failed; subject and controls are MyITMO values. */
        val barsError: AppError? = null,
        /** Schedule-side sections: upcoming lessons, teachers, resources. */
        val hub: SubjectHubState = SubjectHubState()
    ) : RecordbookSubjectUiState
    data class Error(val error: AppError) : RecordbookSubjectUiState
}

@HiltViewModel
class RecordbookSubjectViewModel @Inject constructor(
    private val repository: RecordbookRepository,
    private val bars: BarsRecordbookRepository,
    savedStateHandle: SavedStateHandle,
    private val sportResolver: RecordbookSportResolver,
    private val lessonsGateway: SubjectLessonsGateway,
    private val scheduleRefresh: ScheduleRefreshGateway,
    private val bindings: SubjectBindingStore,
    private val contextResolver: SubjectContextResolver,
    private val time: AcademicTimeProvider
) : ViewModel() {
    private val entryId = checkNotNull(savedStateHandle.get<Long>(ARG_ENTRY_ID))
    private val programId = checkNotNull(savedStateHandle.get<Long>(ARG_PROGRAM_ID))
    private val period = RecordbookPeriod(
        studyYear = checkNotNull(savedStateHandle.get<String>(ARG_STUDY_YEAR)),
        semester = checkNotNull(savedStateHandle.get<Int>(ARG_SEMESTER)),
        course = 0,
        actual = false
    )
    private val barsJournal: BarsJournalReference? = savedStateHandle.get<Long>(ARG_BARS_PLAN)?.let { plan ->
        BarsJournalReference(plan, checkNotNull(savedStateHandle.get<String>(ARG_BARS_TYPE)),
            checkNotNull(savedStateHandle.get<String>(ARG_BARS_IDENTIFIER)),
            period.studyYear.substringBefore('/').toInt(), period.semesterInCourse)
    }
    private val _uiState = MutableStateFlow<RecordbookSubjectUiState>(RecordbookSubjectUiState.Loading)
    val uiState = _uiState.asStateFlow()
    private val _tab = MutableStateFlow(savedStateHandle.get<String>(KEY_TAB)?.let(SubjectTab::valueOf) ?: SubjectTab.SCORES)
    val tab = _tab.asStateFlow()
    private val handle = savedStateHandle
    private var loadJob: Job? = null
    private var hubJob: Job? = null
    /** Bumped after a binding is written so the lesson flow is re-evaluated. */
    private val bindingVersion = MutableStateFlow(0)
    private var proposalRejected = false

    init { refresh() }

    fun refresh() {
        loadJob?.cancel()
        val previous = (_uiState.value as? RecordbookSubjectUiState.Content)?.copy(refreshing = false)
        _uiState.value = previous?.copy(refreshing = true, refreshError = null)
            ?: RecordbookSubjectUiState.Loading
        loadJob = viewModelScope.launch {
            val journal = barsJournal?.let { async { bars.getSubject(it) } }
            val subjects = repository.getSubjects(programId, period.semester)
            val official = (subjects as? AppResult.Success)?.value?.firstOrNull { it.entryId == entryId } ?: run {
                journal?.cancel()
                val error = (subjects as? AppResult.Failure)?.error ?: AppError.NotFound
                _uiState.value = previous?.copy(refreshError = error)
                    ?: RecordbookSubjectUiState.Error(error)
                return@launch
            }
            val sport = async { sportResolver.resolve(period, listOf(official)) }
            var subject = official
            var barsError: AppError? = null
            val controls = when (val details = journal?.await()) {
                is AppResult.Success -> {
                    subject = subject.withBars(details.value.subject)
                    AppResult.Success(details.value.controls)
                }
                is AppResult.Failure -> { barsError = details.error; myItmoControls(official) }
                null -> myItmoControls(official)
            }
            _uiState.value = RecordbookSubjectUiState.Content(
                subject = subject,
                controls = (controls as? AppResult.Success)?.value.orEmpty(),
                sport = sport.await(),
                controlsError = (controls as? AppResult.Failure)?.error,
                barsError = barsError,
                hub = previous?.hub ?: SubjectHubState()
            )
            loadHub(official)
        }
    }

    fun selectTab(tab: SubjectTab) {
        _tab.value = tab
        handle[KEY_TAB] = tab.name
    }

    /** The user agrees that [subjectId] in the schedule is this discipline. */
    fun confirmBinding(subjectId: Long) {
        val subject = (_uiState.value as? RecordbookSubjectUiState.Content)?.subject ?: return
        viewModelScope.launch {
            bindings.put(subject.disciplineId, subjectId)
            proposalRejected = false
            bindingVersion.update { it + 1 }
        }
    }

    /** Not the same thing: nothing is stored, the proposal is gone for this screen. */
    fun rejectProposal() {
        proposalRejected = true
        bindingVersion.update { it + 1 }
    }

    fun retryLessons() {
        (_uiState.value as? RecordbookSubjectUiState.Content)?.subject?.let(::loadHub)
    }

    private fun loadHub(subject: RecordbookSubject) {
        hubJob?.cancel()
        val fallbackTeachers = listOfNotNull(subject.teacherName?.let { SubjectTeacher(it, isu = null, roles = emptyList()) })
        val resources = listOfNotNull(subject.lmsLink?.let(::SubjectResource))
        if (!period.isCurrent() || subject.isPhysicalEducation) {
            updateHub { SubjectHubState(SubjectLessonsState.Hidden, fallbackTeachers, resources) }
            return
        }
        updateHub { SubjectHubState(SubjectLessonsState.Loading, fallbackTeachers, resources) }
        hubJob = viewModelScope.launch {
            val today = time.today()
            val end = today.plusDays(WINDOW_DAYS)
            val refresh = scheduleRefresh.refreshOwnSchedule(today, end)
            combine(lessonsGateway.observeOwnLessons(today, end), bindingVersion) { lessons, _ -> lessons }
                .collectLatest { lessons ->
                    if (lessons.isEmpty() && refresh is AppResult.Failure) {
                        updateHub { copy(lessons = SubjectLessonsState.Error(refresh.error)) }
                        return@collectLatest
                    }
                    val context = contextResolver.resolve(subject, subjectsIn(lessons), bindings.get(subject.disciplineId))
                    val own = (context as? SubjectContext.Bound)?.let { bound -> lessons.filter { it.subjectId == bound.subjectId } }
                    val teachers = own?.let(::teachersOf)?.takeIf { it.isNotEmpty() } ?: fallbackTeachers
                    val state = when (context) {
                        is SubjectContext.Bound ->
                            if (own.isNullOrEmpty()) SubjectLessonsState.Unmatched
                            else SubjectLessonsState.Content(own.take(MAX_LESSONS), context.source)
                        is SubjectContext.Proposed ->
                            if (proposalRejected) SubjectLessonsState.Unmatched else SubjectLessonsState.Proposed(context.candidate)
                        is SubjectContext.Ambiguous ->
                            if (proposalRejected) SubjectLessonsState.Unmatched else SubjectLessonsState.Ambiguous(context.candidates)
                        SubjectContext.Unmatched -> SubjectLessonsState.Unmatched
                        SubjectContext.NotApplicable -> SubjectLessonsState.Hidden
                    }
                    updateHub { SubjectHubState(state, teachers, resources) }
                }
        }
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

    private fun updateHub(transform: SubjectHubState.() -> SubjectHubState) {
        _uiState.update { state ->
            if (state is RecordbookSubjectUiState.Content) state.copy(hub = state.hub.transform()) else state
        }
    }

    /** Same rule as the study root's default selection: the academic year rolls in September. */
    private fun RecordbookPeriod.isCurrent(): Boolean {
        val today = time.today()
        val yearStart = today.year - if (today.monthValue < 9) 1 else 0
        val half = if (today.monthValue in 2..8) 2 else 1
        return studyYear == "$yearStart/${yearStart + 1}" && semesterInCourse == half
    }

    private suspend fun myItmoControls(subject: RecordbookSubject): AppResult<List<RecordbookControl>> =
        if (subject.hasDetails) repository.getControls(entryId) else AppResult.Success(emptyList())

    companion object {
        private const val WINDOW_DAYS = 28L
        private const val KEY_TAB = "subject_tab"
        private const val MAX_LESSONS = 10
        const val ARG_ENTRY_ID = "entry_id"
        const val ARG_PROGRAM_ID = "program_id"
        const val ARG_SEMESTER = "semester"
        const val ARG_STUDY_YEAR = "study_year"
        const val ARG_BARS_PLAN = "bars_plan"
        const val ARG_BARS_TYPE = "bars_type"
        const val ARG_BARS_IDENTIFIER = "bars_identifier"
    }
}
