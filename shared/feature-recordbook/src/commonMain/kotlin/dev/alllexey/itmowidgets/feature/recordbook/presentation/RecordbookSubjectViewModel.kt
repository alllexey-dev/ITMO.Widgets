package dev.alllexey.itmowidgets.feature.recordbook.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.alllexey.itmowidgets.core.navigation.RecordbookSubjectArgs
import dev.alllexey.itmowidgets.core.presentation.BusyKeys
import dev.alllexey.itmowidgets.core.presentation.EventQueue
import dev.alllexey.itmowidgets.core.presentation.RefreshMode
import dev.alllexey.itmowidgets.core.resources.ResourceScope
import dev.alllexey.itmowidgets.core.resources.SubjectLinksState
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.reviews.TeacherLevel
import dev.alllexey.itmowidgets.core.social.PersonPhotosRepository
import dev.alllexey.itmowidgets.core.services.BackendGate
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import dev.alllexey.itmowidgets.feature.recordbook.domain.BarsPreferenceRepository
import dev.alllexey.itmowidgets.feature.recordbook.domain.BarsRecordbookRepository
import dev.alllexey.itmowidgets.feature.recordbook.domain.BarsSubjectDetails
import dev.alllexey.itmowidgets.feature.recordbook.domain.RecordbookBarsMerge
import dev.alllexey.itmowidgets.feature.recordbook.domain.RecordbookRepository
import dev.alllexey.itmowidgets.feature.recordbook.domain.RecordbookSportResolver
import dev.alllexey.itmowidgets.feature.recordbook.domain.RecordbookSportState
import dev.alllexey.itmowidgets.feature.recordbook.domain.marks.MarkTrackingRepository
import dev.alllexey.itmowidgets.feature.recordbook.domain.marks.StudyHalf
import dev.alllexey.itmowidgets.feature.recordbook.domain.marks.studyHalf
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.BarsJournalReference
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookControl
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookPeriod
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookSubject
import dev.alllexey.itmowidgets.feature.recordbook.domain.subjectNameKey
import dev.alllexey.itmowidgets.feature.recordbook.domain.withBars
import dev.alllexey.itmowidgets.shared.feature.recordbook.RecordbookStrings
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Предмет on both platforms: the My ITMO subject with its checkpoints (BARS laid over them while BARS is connected in
 * the recordbook), the teachers and the lessons ahead from the own schedule, the links and chats from the server, the
 * connected sheet's total. Each tab reads its loader; [SubjectPresenter] puts them into words.
 */
class RecordbookSubjectViewModel(
    private val args: RecordbookSubjectArgs,
    private val repository: RecordbookRepository,
    private val bars: BarsRecordbookRepository,
    private val barsPreference: BarsPreferenceRepository,
    private val sportResolver: RecordbookSportResolver,
    private val time: AcademicTimeProvider,
    private val marks: MarkTrackingRepository,
    private val lessonsLoader: SubjectLessonsLoader,
    private val linksLoader: SubjectLinksLoader,
    private val sheetLoader: SubjectSheetLoader,
    private val levelsLoader: SubjectTeacherLevelsLoader,
    private val backendGate: BackendGate,
    private val photos: PersonPhotosRepository,
) : ViewModel() {
    private val period = RecordbookPeriod(studyYear = args.studyYear, semester = args.semester, course = 0, actual = false)
    private var journal: BarsJournalReference? = args.barsPlan?.let { plan ->
        BarsJournalReference(plan, args.barsType.orEmpty(), args.barsIdentifier.orEmpty(),
            period.studyYear.substringBefore('/').toInt(), period.semesterInCourse)
    }
    private val _uiState = MutableStateFlow<RecordbookSubjectUiState>(RecordbookSubjectUiState.Loading)
    val uiState: StateFlow<RecordbookSubjectUiState> = _uiState.asStateFlow()
    private val eventQueue = EventQueue<RecordbookSubjectEvent>()
    val events: Flow<RecordbookSubjectEvent> = eventQueue.events

    private var official: RecordbookSubject? = null
    private var shown: RecordbookSubject? = null
    private var controls: List<RecordbookControl> = emptyList()
    private var controlsError: AppError? = null
    private var barsError: AppError? = null
    private var barsEnabled = false
    private var sport: RecordbookSportState? = null
    private var failure: AppError? = null
    private var retrying = false
    private var refreshing = false
    private var refreshError: AppError? = null
    private var tab = SubjectTab.SCORES
    private var connected = false
    private var lessons: SubjectLessonsState = SubjectLessonsState.Loading
    private var lessonsExpanded = false
    private var teachers: List<SubjectTeacher> = emptyList()
    private var levels: Map<Long, TeacherLevel> = emptyMap()
    private var teacherPhotos: Map<Int, String?> = emptyMap()
    private var links: SubjectLinksState? = null
    private var canVote = false
    private var sheet: SubjectSheetState? = null

    private var loadJob: Job? = null
    private var lessonsJob: Job? = null
    private var linksJob: Job? = null
    private var levelsJob: Job? = null
    private var levelIsus: Set<Int> = emptySet()
    private var photosJob: Job? = null
    private var photoIsus: Set<Int> = emptySet()
    private val proposalRejected = MutableStateFlow(false)
    /** One vote at a time: a tap while one is sent is ignored. */
    private val voting = BusyKeys<Unit>(viewModelScope)

    init {
        viewModelScope.launch {
            backendGate.observeConnected().collect {
                connected = it
                publish()
            }
        }
        refresh(RefreshMode.Silent)
    }

    /**
     * Every mode reloads the page and replaces a load in flight. The load on entry stays silent behind the cached
     * subject; a pull or a retry shows the indicator and ranks the links afresh.
     */
    fun refresh(mode: RefreshMode) {
        loadJob?.cancel()
        if (mode.showsIndicator) linksLoader.rankAfresh()
        if (official == null) seedFromCache()
        refreshing = mode.showsIndicator && official != null
        retrying = mode.showsIndicator && failure != null
        refreshError = null
        publish()
        loadJob = viewModelScope.launch {
            barsEnabled = barsPreference.isEnabled()
            val subjects = repository.getSubjects(args.programId, period.semester)
            val subject = (subjects as? AppResult.Success)?.value?.firstOrNull { it.entryId == args.entryId }
            if (subject == null) {
                val error = (subjects as? AppResult.Failure)?.error ?: AppError.NotFound
                refreshing = false
                retrying = false
                if (official != null) refreshError = error else failure = error
                return@launch publish()
            }
            val first = official == null
            official = subject
            failure = null
            retrying = false
            val sportJob = async { sportResolver.resolve(period, listOf(subject)) }
            val details = if (barsEnabled) barsDetails(subject) else null
            barsError = (details as? AppResult.Failure)?.error
            when (details) {
                is AppResult.Success -> {
                    shown = subject.withBars(details.value.subject)
                    controls = details.value.controls
                    controlsError = null
                }
                else -> {
                    shown = subject
                    when (val own = myItmoControls(subject)) {
                        is AppResult.Success -> { controls = own.value; controlsError = null }
                        is AppResult.Failure -> { controls = emptyList(); controlsError = own.error }
                    }
                }
            }
            sport = sportJob.await()
            refreshing = false
            publish()
            if (first) markRead(subject)
            loadHub(subject)
        }
    }

    fun selectTab(selected: SubjectTab) {
        tab = selected
        publish()
    }

    /** `Все пары`: the rest of the window's lessons open under the first ones. */
    fun showAllLessons() {
        lessonsExpanded = true
        publish()
    }

    /** The user agrees that [subjectId] in the schedule is this discipline. */
    fun confirmBinding(subjectId: Long) {
        val subject = official ?: return
        viewModelScope.launch {
            lessonsLoader.bind(subject.disciplineId, subjectId)
            proposalRejected.value = false
        }
    }

    /** Not the same thing: nothing is stored, the question is gone for this screen. */
    fun rejectBinding() {
        proposalRejected.value = true
    }

    fun retryLessons() {
        official?.let(::loadLessons)
    }

    /** The BARS sign-in finished: BARS is connected in the recordbook from now on and the page loads again. */
    fun barsSignedIn() {
        viewModelScope.launch {
            barsPreference.setEnabled(true)
            refresh(RefreshMode.Force)
        }
    }

    /** Tapping the thumb of the current vote takes it back; one vote at a time, a failure is reported once. */
    fun voteLink(id: String, up: Boolean) {
        val scope = scope() ?: return
        val snapshot = (links as? SubjectLinksState.Content)?.snapshot ?: return
        val link = (snapshot.mine + snapshot.shared + snapshot.previous).firstOrNull { it.id == id } ?: return
        voting.launch(Unit) {
            val result = linksLoader.vote(scope, link, up)
            if (result is AppResult.Failure) eventQueue.send(RecordbookSubjectEvent.VoteFailed(result.error))
        }
    }

    fun disconnectSheet() {
        val scope = scope() ?: return
        viewModelScope.launch { sheetLoader.disconnect(scope) }
    }

    /**
     * The journal the list passed, else the one of this subject in the period's BARS answer (a notification, a new
     * sign-in); null when BARS has no such subject, which is no error.
     */
    private suspend fun barsDetails(subject: RecordbookSubject): AppResult<BarsSubjectDetails>? {
        val reference = journal ?: when (val all = bars.getSubjects(period)) {
            is AppResult.Failure -> return all
            is AppResult.Success -> RecordbookBarsMerge.apply(listOf(subject), all.value).single().barsJournal ?: return null
        }
        journal = reference
        return bars.getSubject(reference)
    }

    /** The list's last answer for this subject, so the page opens without a spinner; only with its controls known. */
    private fun seedFromCache() {
        val subject = repository.cachedSubjects(args.programId, period.semester)?.firstOrNull { it.entryId == args.entryId } ?: return
        controls = repository.cachedControls(args.entryId) ?: if (subject.hasDetails) return else emptyList()
        official = subject
        shown = subject
    }

    /** Opening the page reads the subject's new marks. */
    private fun markRead(subject: RecordbookSubject) {
        val half = period.studyHalf() ?: return
        viewModelScope.launch { marks.markRead(half, subjectNameKey(subject.name)) }
    }

    private fun loadHub(subject: RecordbookSubject) {
        loadLessons(subject)
        loadLinks(subject)
    }

    private fun loadLessons(subject: RecordbookSubject) {
        lessonsJob?.cancel()
        val fallback = listOfNotNull(subject.teacherName?.let { SubjectTeacher(it, isu = null, roles = emptyList()) })
        teachers = fallback
        loadTeacherPhotos()
        if (!period.isCurrent() || subject.isPhysicalEducation) {
            lessons = SubjectLessonsState.Hidden
            return publish()
        }
        lessons = SubjectLessonsState.Loading
        publish()
        lessonsJob = viewModelScope.launch {
            lessonsLoader.observe(subject, proposalRejected, fallback).collect { update ->
                lessons = update.lessons
                update.teachers?.let { teachers = it }
                loadTeacherPhotos()
                publish()
                loadTeacherLevels()
            }
        }
    }

    private fun loadLinks(subject: RecordbookSubject) {
        linksJob?.cancel()
        val scope = scopeOf(subject)
        if (links == null) links = linksLoader.cached(scope)
        linksJob = viewModelScope.launch {
            combine(linksLoader.observe(scope), sheetLoader.observe(scope)) { update, scores -> update to scores }
                .collect { (update, scores) ->
                    links = update.links
                    canVote = update.canVote
                    sheet = sheetLoader.state(scope, update.links, scores)
                    publish()
                }
        }
    }

    /** Asks for tones only when the set of teachers with an ISU changes. */
    private fun loadTeacherLevels() {
        val isus = levelsLoader.isusOf(teachers)
        if (isus == levelIsus) return
        levelIsus = isus
        levelsJob?.cancel()
        if (isus.isEmpty()) {
            levels = emptyMap()
            return publish()
        }
        levelsJob = viewModelScope.launch {
            levels = levelsLoader.levels(isus)
            publish()
        }
    }

    /** Photos are official directory data, so they load even with the Backend connection off. */
    private fun loadTeacherPhotos() {
        val isus = levelsLoader.isusOf(teachers)
        if (isus == photoIsus) return
        photoIsus = isus
        photosJob?.cancel()
        teacherPhotos = isus.associateWith(photos::cachedPhotoUrl)
        if (isus.isEmpty()) return
        photosJob = viewModelScope.launch {
            isus.forEach { isu ->
                launch {
                    val url = photos.photoUrl(isu)
                    ensureActive()
                    teacherPhotos = teacherPhotos + (isu to url)
                    publish()
                }
            }
        }
    }

    private fun publish() {
        val subject = shown
        val base = official
        if (subject == null || base == null) {
            _uiState.value = failure?.let { RecordbookSubjectUiState.Failed(it, retrying) } ?: RecordbookSubjectUiState.Loading
            return
        }
        val scope = scopeOf(base)
        _uiState.value = RecordbookSubjectUiState.Content(
            title = base.name,
            subtitle = UiText.Res(RecordbookStrings.subject_subtitle, listOf(base.controlType.trim(), period.semester)),
            tab = tab,
            scores = SubjectPresenter.scores(
                SubjectScoresInput(
                    official = base,
                    subject = subject,
                    barsEnabled = barsEnabled,
                    barsError = barsError,
                    controls = controls,
                    controlsError = controlsError,
                    sport = sport,
                    sheet = sheet,
                    links = links,
                    scope = scope,
                    today = time.today(),
                    timeZone = time.timeZone,
                ),
            ),
            lessons = SubjectLessons(
                teachers = SubjectPresenter.teachers(teachers, levels, connected, teacherPhotos),
                reviews = SubjectPresenter.reviews(teachers, levels, connected),
                binding = when (val current = lessons) {
                    is SubjectLessonsState.Proposed -> listOf(current.candidate)
                    is SubjectLessonsState.Ambiguous -> current.candidates
                    else -> null
                }?.let { candidates -> SubjectBinding(candidates.map { SubjectBindingCandidate(it.subjectId, it.name) }) },
                list = lessonList(),
            ),
            materials = SubjectPresenter.materials(links, connected, canVote, scope, linksLoader::arrange),
            refreshing = refreshing,
            refreshError = refreshError,
        )
    }

    private fun lessonList(): SubjectLessonList = when (val current = lessons) {
        SubjectLessonsState.Hidden, is SubjectLessonsState.Proposed, is SubjectLessonsState.Ambiguous -> SubjectLessonList.None
        SubjectLessonsState.Loading -> SubjectLessonList.Loading
        is SubjectLessonsState.Error -> SubjectLessonList.Failed(current.error)
        SubjectLessonsState.Unmatched -> SubjectLessonList.Unmatched
        is SubjectLessonsState.Content -> {
            val visible = if (lessonsExpanded) current.lessons else current.lessons.take(COLLAPSED_LESSONS)
            SubjectLessonList.Content(
                items = visible.map(SubjectPresenter::lessonItem),
                more = current.lessons.size - visible.size,
            )
        }
    }

    private fun scope(): ResourceScope? = official?.let(::scopeOf)

    private fun scopeOf(subject: RecordbookSubject) = ResourceScope(
        subject.disciplineId, subject.name, ResourceScope.periodKey(period.studyYear, period.semesterInCourse),
    )

    /** The current half-year: lessons ahead exist only for it. */
    private fun RecordbookPeriod.isCurrent(): Boolean = studyHalf() == StudyHalf.of(time.today())

    private suspend fun myItmoControls(subject: RecordbookSubject): AppResult<List<RecordbookControl>> =
        if (subject.hasDetails) repository.getControls(args.entryId) else AppResult.Success(emptyList())

    private companion object {
        const val COLLAPSED_LESSONS = 3
    }
}
