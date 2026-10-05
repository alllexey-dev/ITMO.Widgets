package dev.alllexey.itmowidgets.feature.recordbook.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.alllexey.itmowidgets.core.navigation.RecordbookSubjectArgs
import dev.alllexey.itmowidgets.core.presentation.BusyKeys
import dev.alllexey.itmowidgets.core.presentation.EventQueue
import dev.alllexey.itmowidgets.core.presentation.RefreshMode
import dev.alllexey.itmowidgets.core.resources.LinkCategory
import dev.alllexey.itmowidgets.core.resources.ResourceScope
import dev.alllexey.itmowidgets.core.resources.SubjectLinkChips
import dev.alllexey.itmowidgets.core.resources.SubjectLinksSnapshot
import dev.alllexey.itmowidgets.core.resources.SubjectLinksState
import dev.alllexey.itmowidgets.core.resources.subjectLinkChips
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.reviews.TeacherLevel
import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import dev.alllexey.itmowidgets.feature.recordbook.domain.BarsRecordbookRepository
import dev.alllexey.itmowidgets.feature.recordbook.domain.RecordbookRepository
import dev.alllexey.itmowidgets.feature.recordbook.domain.RecordbookSportResolver
import dev.alllexey.itmowidgets.feature.recordbook.domain.marks.MarkTrackingRepository
import dev.alllexey.itmowidgets.feature.recordbook.domain.marks.studyHalf
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.BarsJournalReference
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookControl
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookPeriod
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookSubject
import dev.alllexey.itmowidgets.feature.recordbook.domain.subjectNameKey
import dev.alllexey.itmowidgets.feature.recordbook.domain.withBars
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.number

/**
 * One subject page: the MyITMO subject with its controls (BARS values over them when the list had a journal), and the
 * hub below it. The hub's lessons, links, sheet total and teacher tones come from their loaders.
 */
@HiltViewModel
class RecordbookSubjectViewModel @Inject constructor(
    private val repository: RecordbookRepository,
    private val bars: BarsRecordbookRepository,
    savedStateHandle: SavedStateHandle,
    private val sportResolver: RecordbookSportResolver,
    private val time: AcademicTimeProvider,
    private val marks: MarkTrackingRepository,
    private val lessonsLoader: SubjectLessonsLoader,
    private val linksLoader: SubjectLinksLoader,
    private val sheetLoader: SubjectSheetLoader,
    private val levelsLoader: SubjectTeacherLevelsLoader,
) : ViewModel() {
    private val entryId = checkNotNull(savedStateHandle.get<Long>(RecordbookSubjectArgs.ENTRY_ID))
    private val programId = checkNotNull(savedStateHandle.get<Long>(RecordbookSubjectArgs.PROGRAM_ID))
    private val period = RecordbookPeriod(
        studyYear = checkNotNull(savedStateHandle.get<String>(RecordbookSubjectArgs.STUDY_YEAR_KEY)),
        semester = checkNotNull(savedStateHandle.get<Int>(RecordbookSubjectArgs.SEMESTER)),
        course = 0,
        actual = false
    )
    private val barsJournal: BarsJournalReference? = savedStateHandle.get<Long>(RecordbookSubjectArgs.BARS_PLAN)?.let { plan ->
        BarsJournalReference(plan, checkNotNull(savedStateHandle.get<String>(RecordbookSubjectArgs.BARS_TYPE)),
            checkNotNull(savedStateHandle.get<String>(RecordbookSubjectArgs.BARS_IDENTIFIER)),
            period.studyYear.substringBefore('/').toInt(), period.semesterInCourse)
    }
    private val _uiState = MutableStateFlow<RecordbookSubjectUiState>(RecordbookSubjectUiState.Loading)
    val uiState: StateFlow<RecordbookSubjectUiState> = _uiState.asStateFlow()
    private val eventQueue = EventQueue<RecordbookSubjectEvent>()
    val events: Flow<RecordbookSubjectEvent> = eventQueue.events
    private var loadJob: Job? = null
    private var hubJob: Job? = null
    private var linksJob: Job? = null
    private var levelsJob: Job? = null
    private var levelIsus: Set<Int> = emptySet()
    private val proposalRejected = MutableStateFlow(false)
    /** One vote at a time: a tap while one is sent is ignored. */
    private val voting = BusyKeys<Unit>(viewModelScope)

    init {
        refresh(RefreshMode.Silent)
        // Opening the page reads the subject's new marks: once, on the first content from the cache or the answer.
        period.studyHalf()?.let { half ->
            viewModelScope.launch {
                val subject = _uiState.filterIsInstance<RecordbookSubjectUiState.Content>().first().subject
                marks.markRead(half, subjectNameKey(subject.name))
            }
        }
    }

    /**
     * Every mode reloads the page and replaces a load in flight. The load on entry stays silent behind the cached
     * subject; a pull or a retry shows the indicator and ranks the links afresh, while votes in between keep the rows
     * the page shows in their places.
     */
    fun refresh(mode: RefreshMode) {
        loadJob?.cancel()
        if (mode.showsIndicator) linksLoader.rankAfresh()
        val previous = (_uiState.value as? RecordbookSubjectUiState.Content)?.copy(refreshing = false)
            ?: seedFromCache()
        _uiState.value = previous?.copy(refreshing = mode.showsIndicator, refreshError = null)
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
                hub = previous?.hub ?: SubjectHubState(),
                timeZone = time.timeZone
            )
            loadHub(official)
        }
    }

    /**
     * The list's last answer for this subject, so the hub opens without a spinner.
     * Only when the controls are known too: an empty list would read as "no details".
     */
    private fun seedFromCache(): RecordbookSubjectUiState.Content? {
        val subject = repository.cachedSubjects(programId, period.semester)
            ?.firstOrNull { it.entryId == entryId } ?: return null
        val controls = repository.cachedControls(entryId) ?: if (subject.hasDetails) return null else emptyList()
        return RecordbookSubjectUiState.Content(
            subject = subject,
            controls = controls,
            sport = null,
            hub = SubjectHubState(lessons = if (period.isCurrent() && !subject.isPhysicalEducation)
                SubjectLessonsState.Loading else SubjectLessonsState.Hidden),
            timeZone = time.timeZone
        )
    }

    /** «Все пары»: the rest of the window's lessons open under the first two. */
    fun showAllLessons() = updateHub { copy(lessonsExpanded = true) }

    /** The user agrees that [subjectId] in the schedule is this discipline. */
    fun confirmBinding(subjectId: Long) {
        val subject = (_uiState.value as? RecordbookSubjectUiState.Content)?.subject ?: return
        viewModelScope.launch {
            lessonsLoader.bind(subject.disciplineId, subjectId)
            proposalRejected.value = false
        }
    }

    /** Not the same thing: nothing is stored, the proposal is gone for this screen. */
    fun rejectProposal() {
        proposalRejected.value = true
    }

    fun retryLessons() {
        (_uiState.value as? RecordbookSubjectUiState.Content)?.subject?.let(::loadHub)
    }

    private fun loadHub(subject: RecordbookSubject) {
        hubJob?.cancel()
        val fallbackTeachers = listOfNotNull(subject.teacherName?.let { SubjectTeacher(it, isu = null, roles = emptyList()) })
        loadLinks(subject)
        if (!period.isCurrent() || subject.isPhysicalEducation) {
            updateHub { copy(lessons = SubjectLessonsState.Hidden, teachers = fallbackTeachers) }
            return
        }
        updateHub { copy(lessons = SubjectLessonsState.Loading, teachers = fallbackTeachers) }
        hubJob = viewModelScope.launch {
            lessonsLoader.observe(subject, proposalRejected, fallbackTeachers).collect { update ->
                updateHub { copy(lessons = update.lessons, teachers = update.teachers ?: teachers) }
            }
        }
    }

    /** Past periods keep their links; physical education has none, not even the LMS page. */
    private fun loadLinks(subject: RecordbookSubject) {
        linksJob?.cancel()
        if (subject.isPhysicalEducation) {
            updateHub {
                copy(resourceScope = null, links = null, chips = SubjectLinkChips(emptyList(), 0), chats = emptyList(),
                    linkCount = 0, canVote = false, sheet = null)
            }
            return
        }
        val scope = ResourceScope(subject.disciplineId, subject.name,
            ResourceScope.periodKey(period.studyYear, period.semesterInCourse))
        updateHub { withLinks(scope, linksLoader.cached(scope), subject.lmsLink) }
        linksJob = viewModelScope.launch {
            combine(linksLoader.observe(scope), sheetLoader.observe(scope)) { links, scores -> links to scores }
                .collect { (links, scores) ->
                    updateHub {
                        withLinks(scope, links.links, subject.lmsLink).copy(
                            sheet = sheetLoader.state(scope, links.links, scores),
                            canVote = links.canVote
                        )
                    }
                }
        }
    }

    /** Tapping the arrow of the current vote takes it back; one vote at a time, a failure is reported once. */
    fun voteLink(id: String, up: Boolean) {
        val hub = (_uiState.value as? RecordbookSubjectUiState.Content)?.hub ?: return
        val scope = hub.resourceScope ?: return
        val snapshot = (hub.links as? SubjectLinksState.Content)?.snapshot ?: return
        val link = (snapshot.mine + snapshot.shared + snapshot.previous).firstOrNull { it.id == id } ?: return
        voting.launch(Unit) {
            val result = linksLoader.vote(scope, link, up)
            if (result is AppResult.Failure) eventQueue.send(RecordbookSubjectEvent.VoteFailed(result.error))
        }
    }

    fun disconnectSheet() {
        val scope = (_uiState.value as? RecordbookSubjectUiState.Content)?.hub?.resourceScope ?: return
        viewModelScope.launch { sheetLoader.disconnect(scope) }
    }

    private fun SubjectHubState.withLinks(scope: ResourceScope, state: SubjectLinksState, lmsUrl: String?): SubjectHubState {
        val snapshot = (state as? SubjectLinksState.Content)?.snapshot
        return copy(
            resourceScope = scope,
            links = state,
            chips = subjectLinkChips(snapshot ?: EMPTY_LINKS, lmsUrl, limit = SubjectHubState.LINK_ROWS, linksLoader::arrange),
            linkCount = snapshot?.let { (it.mine + it.shared + it.previous).distinctBy { link -> link.id }.size } ?: 0,
            chats = snapshot?.let { (it.mine + it.shared).filter { link -> link.category == LinkCategory.CHAT }.distinctBy { link -> link.id } }
                .orEmpty()
        )
    }

    private fun updateHub(transform: SubjectHubState.() -> SubjectHubState) {
        _uiState.update { state ->
            if (state is RecordbookSubjectUiState.Content) state.copy(hub = state.hub.transform()) else state
        }
        (_uiState.value as? RecordbookSubjectUiState.Content)?.hub?.teachers?.let(::loadTeacherLevels)
    }

    /** Asks for tones only when the set of teachers with an ISU changes. */
    private fun loadTeacherLevels(teachers: List<SubjectTeacher>) {
        val isus = levelsLoader.isusOf(teachers)
        if (isus == levelIsus) return
        levelIsus = isus
        levelsJob?.cancel()
        if (isus.isEmpty()) {
            updateLevels(emptyMap())
            return
        }
        levelsJob = viewModelScope.launch { updateLevels(levelsLoader.levels(isus)) }
    }

    private fun updateLevels(levels: Map<Long, TeacherLevel>) = _uiState.update { state ->
        if (state is RecordbookSubjectUiState.Content && state.hub.teacherLevels != levels) {
            state.copy(hub = state.hub.copy(teacherLevels = levels))
        } else state
    }

    /** Same rule as the study root's default selection: the academic year rolls in September. */
    private fun RecordbookPeriod.isCurrent(): Boolean {
        val today = time.today()
        val yearStart = today.year - if (today.month.number < 9) 1 else 0
        val half = if (today.month.number in 2..8) 2 else 1
        return studyYear == "$yearStart/${yearStart + 1}" && semesterInCourse == half
    }

    private suspend fun myItmoControls(subject: RecordbookSubject): AppResult<List<RecordbookControl>> =
        if (subject.hasDetails) repository.getControls(entryId) else AppResult.Success(emptyList())

    private companion object {
        val EMPTY_LINKS = SubjectLinksSnapshot(emptyList(), emptyList(), emptyList(), null, emptyList(),
            premoderation = false, servicesEnabled = false)
    }
}
