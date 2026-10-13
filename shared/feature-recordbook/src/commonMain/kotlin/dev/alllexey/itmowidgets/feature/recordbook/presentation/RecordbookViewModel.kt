package dev.alllexey.itmowidgets.feature.recordbook.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.alllexey.itmowidgets.core.presentation.RefreshMode
import dev.alllexey.itmowidgets.core.resources.ResourceScope
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import dev.alllexey.itmowidgets.feature.recordbook.domain.BarsPreferenceRepository
import dev.alllexey.itmowidgets.feature.recordbook.domain.BarsRecordbookRepository
import dev.alllexey.itmowidgets.feature.recordbook.domain.RecordbookBarsMerge
import dev.alllexey.itmowidgets.feature.recordbook.domain.RecordbookRepository
import dev.alllexey.itmowidgets.feature.recordbook.domain.RecordbookSportResolver
import dev.alllexey.itmowidgets.feature.recordbook.domain.RecordbookSportState
import dev.alllexey.itmowidgets.feature.recordbook.domain.marks.BarsPlanMarks
import dev.alllexey.itmowidgets.feature.recordbook.domain.marks.MarkTrackingRepository
import dev.alllexey.itmowidgets.feature.recordbook.domain.marks.StudyHalf
import dev.alllexey.itmowidgets.feature.recordbook.domain.marks.of
import dev.alllexey.itmowidgets.feature.recordbook.domain.marks.studyHalf
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookControl
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookProgram
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookSubject
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetScore
import dev.alllexey.itmowidgets.feature.recordbook.domain.sheets.SheetScoresRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Зачётка on both platforms: My ITMO's subjects of the chosen period with BARS laid over them while BARS is connected
 * in the recordbook, the sport points of physical education, the totals of connected sheets and the new-mark dots.
 * [RecordbookPresenter] puts them into words; this class loads and keeps them.
 */
class RecordbookViewModel(
    private val repository: RecordbookRepository,
    private val bars: BarsRecordbookRepository,
    private val barsPreference: BarsPreferenceRepository,
    private val sportResolver: RecordbookSportResolver,
    private val time: AcademicTimeProvider,
    private val marks: MarkTrackingRepository,
    private val sheets: SheetScoresRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(RecordbookUiState())
    val uiState: StateFlow<RecordbookUiState> = _uiState.asStateFlow()

    private var programs: List<RecordbookProgram> = emptyList()
    private var selection: RecordbookSelection? = null
    private var body: Body = Body.Loading
    private var official: List<RecordbookSubject> = emptyList()
    private var barsData: RecordbookBarsData = RecordbookBarsData.Off
    private var sport: RecordbookSportState? = null
    private var view: RecordbookView? = null
    private var sourcesId: Long? = null
    private var refreshing = false
    private var refreshError: AppError? = null
    private var barsEnabled = false
    private var offerHidden = false
    private var preferencesLoaded = false
    private var loadJob: Job? = null
    private var newsByHalf: Map<StudyHalf, Set<String>> = emptyMap()
    private var sheetScores: List<SheetScore> = emptyList()

    /** The last BARS answer of a period, kept to show dimmed once the session ends. */
    private var lastBars: Pair<RecordbookSelection, RecordbookBarsData.Applied>? = null

    private sealed interface Body {
        data object Loading : Body
        data class Failed(val error: AppError, val retrying: Boolean) : Body
        data object Empty : Body
        data object Content : Body
    }

    init {
        // Unread marks come from the local store; a new one reaches the open list without a request.
        viewModelScope.launch {
            marks.observeNews().collect { news ->
                newsByHalf = news.groupBy({ it.half }, { it.nameKey }).mapValues { it.value.toSet() }
                publish()
            }
        }
        // Stored totals only: a changed total reaches the open list, nothing is downloaded here.
        viewModelScope.launch {
            sheets.observe().collect { scores ->
                sheetScores = scores
                publish()
            }
        }
    }

    /**
     * [RefreshMode.Silent] is the entry: it loads the list once and does nothing while that load runs or after it
     * ended, so coming back to the list does not reload it. A pull or a retry shows the indicator and replaces a load
     * in flight.
     */
    fun refresh(mode: RefreshMode) {
        when {
            mode.showsIndicator -> load(silent = false)
            body == Body.Loading && loadJob?.isActive != true -> load(silent = true)
        }
    }

    fun selectPeriod(programId: Long, semester: Int) {
        val program = programs.firstOrNull { it.id == programId } ?: return
        val period = program.periods.firstOrNull { it.semester == semester } ?: return
        val selected = RecordbookSelection(program, period)
        if (selection == selected) return
        selection = selected
        view = null
        sourcesId = null
        load(silent = true)
    }

    /** `Семестр` or `Сессия`; the choice holds until another period is chosen. */
    fun selectView(selected: RecordbookView) {
        view = selected
        publish()
    }

    /** The BARS sign-in finished: BARS is connected in the recordbook from now on and the list loads again. */
    fun barsSignedIn() {
        barsEnabled = true
        viewModelScope.launch { barsPreference.setEnabled(true) }
        load(silent = false)
    }

    /** The cross on the offer: it stays hidden on this device. */
    fun hideBarsOffer() {
        offerHidden = true
        viewModelScope.launch { barsPreference.hideOffer() }
        publish()
    }

    /** `Откуда баллы` of the subject [id]. */
    fun openSources(id: Long) {
        sourcesId = id
        publish()
    }

    fun closeSources() {
        sourcesId = null
        publish()
    }

    /** A pull shows the indicator; loads on entry, period change and the BARS sign-in stay silent behind the list. */
    private fun load(silent: Boolean) {
        loadJob?.cancel()
        if (body != Body.Content || official.isEmpty()) seedFromCache()
        refreshError = null
        refreshing = !silent && body == Body.Content
        (body as? Body.Failed)?.let { body = it.copy(retrying = true) }
        publish()
        // Taken before any request: a background check written meanwhile makes this answer stale.
        val stamp = marks.readStarted()
        loadJob = viewModelScope.launch {
            if (!preferencesLoaded) {
                barsEnabled = barsPreference.isEnabled()
                offerHidden = barsPreference.isOfferHidden()
                preferencesLoaded = true
            }
            // Refresh the catalog as well: MyITMO's actual flag and available periods can change.
            when (val result = repository.getPrograms()) {
                is AppResult.Success -> programs = result.value
                is AppResult.Failure -> return@launch fail(result.error)
            }
            val selected = selection?.let(::reselect) ?: defaultSelection()
            if (selected == null) {
                body = Body.Empty
                refreshing = false
                return@launch publish()
            }
            if (selected != selection) view = null
            selection = selected
            val journals = if (barsEnabled) async { bars.getSubjects(selected.period) } else null
            when (val result = repository.getSubjects(selected.program.id, selected.period.semester)) {
                is AppResult.Success -> {
                    official = result.value
                    // What the list shows advances the snapshot of the current half-year; children of the load job.
                    val half = selected.period.studyHalf()?.takeIf { it == StudyHalf.of(time.today()) }
                    if (half != null) {
                        launch { marks.recordMyItmoSeen(stamp, half, selected.program.id, selected.period.semester, official) }
                    }
                    sport = sportResolver.resolve(selected.period, official)
                    body = if (official.isEmpty()) Body.Empty else Body.Content
                    if (journals == null) {
                        barsData = RecordbookBarsData.Off
                        refreshing = false
                        return@launch publish()
                    }
                    // MyITMO is on screen at once; a pull keeps its indicator until BARS answers.
                    if (barsData !is RecordbookBarsData.Applied || lastBars?.first != selected) barsData = RecordbookBarsData.Pending
                    publish()
                    barsData = when (val overlay = journals.await()) {
                        is AppResult.Success -> {
                            if (half != null) launch { marks.recordBarsSeen(stamp, half, barsPlans(overlay.value)) }
                            RecordbookBarsData.Applied(overlay.value, time.now()).also { lastBars = selected to it }
                        }
                        is AppResult.Failure -> if (overlay.error == AppError.Unauthorized) {
                            val earlier = lastBars?.takeIf { it.first == selected }?.second
                            RecordbookBarsData.Expired(earlier?.journals, earlier?.at)
                        } else {
                            RecordbookBarsData.Failed(overlay.error)
                        }
                    }
                    refreshing = false
                    publish()
                }
                is AppResult.Failure -> {
                    journals?.cancel()
                    fail(result.error)
                }
            }
        }
    }

    /** A failed load keeps the rows on screen with a note; without rows the list says it failed. */
    private fun fail(error: AppError) {
        refreshing = false
        if (body == Body.Content) refreshError = error else body = Body.Failed(error, retrying = false)
        publish()
    }

    /** A fresh screen renders the last answers at once; the sport card waits for the refresh. */
    private fun seedFromCache() {
        val cachedPrograms = repository.cachedPrograms() ?: return
        programs = cachedPrograms
        val selected = selection?.let(::reselect) ?: defaultSelection() ?: return
        val subjects = repository.cachedSubjects(selected.program.id, selected.period.semester) ?: return
        selection = selected
        official = subjects
        body = if (subjects.isEmpty()) Body.Empty else Body.Content
    }

    private fun publish() {
        val selected = selection
        val content = when (val current = body) {
            Body.Loading -> RecordbookBody.Loading
            is Body.Failed -> RecordbookBody.Failed(current.error, current.retrying)
            Body.Empty -> RecordbookBody.Empty
            Body.Content -> selected?.let { content(it) } ?: RecordbookBody.Loading
        }
        _uiState.value = RecordbookUiState(
            subtitle = selected?.let(RecordbookPresenter::subtitle),
            periods = selected?.let { RecordbookPresenter.periods(programs, it) },
            body = content,
        )
    }

    private fun content(selected: RecordbookSelection): RecordbookBody.Content {
        val shownBars = when (val data = barsData) {
            // While BARS loads, the earlier answer of the same period stays laid over the list.
            RecordbookBarsData.Pending -> lastBars?.takeIf { it.first == selected }?.second ?: RecordbookBarsData.Pending
            else -> if (barsEnabled) data else RecordbookBarsData.Off
        }
        val journals = (shownBars as? RecordbookBarsData.Applied)?.journals
        val shown = journals?.let { RecordbookBarsMerge.apply(official, it) } ?: official
        val input = RecordbookInput(
            selection = selected,
            official = official,
            bars = shownBars,
            sport = sport,
            attention = shown.mapNotNull { subject ->
                attentionReason(subject, sport, knownControls(subject), time.now())?.let { subject.entryId to it }
            }.toMap(),
            unread = selected.period.studyHalf()?.let(newsByHalf::get).orEmpty(),
            sheets = sheetTotals(selected),
            view = view,
            offerHidden = offerHidden,
            sourcesId = sourcesId,
            today = time.today(),
            timeZone = time.timeZone,
        )
        return RecordbookPresenter.content(input).copy(refreshing = refreshing, refreshError = refreshError)
    }

    /** Non-empty totals of the connected sheets of the period, by discipline. */
    private fun sheetTotals(selected: RecordbookSelection): Map<Long, RecordbookSheetTotal> {
        val periodKey = selected.period.studyHalf()?.periodKey ?: return emptyMap()
        val byScope = sheetScores.mapNotNull { score ->
            score.value?.takeIf(String::isNotBlank)?.let { score.scope.key to RecordbookSheetTotal(it, score.updatedAt) }
        }.toMap()
        return official.mapNotNull { subject ->
            byScope[ResourceScope(subject.disciplineId, subject.name, periodKey).key]?.let { subject.disciplineId to it }
        }.toMap()
    }

    /** Journals whose checkpoints an answer already parsed; the rest have nothing to compare. */
    private fun barsPlans(journals: List<RecordbookSubject>): List<BarsPlanMarks> = journals.mapNotNull { journal ->
        journal.barsJournal?.let(bars::cachedControls)?.let { BarsPlanMarks.of(journal, it) }
    }

    /** Only controls an earlier answer brought; the list never asks for them. */
    private fun knownControls(subject: RecordbookSubject): List<RecordbookControl>? =
        subject.barsJournal?.let(bars::cachedControls) ?: repository.cachedControls(subject.entryId)

    private fun reselect(old: RecordbookSelection): RecordbookSelection? =
        programs.firstOrNull { it.id == old.program.id }?.let { program ->
            program.periods.firstOrNull { it.semester == old.period.semester }?.let { RecordbookSelection(program, it) }
        }

    private fun defaultSelection(): RecordbookSelection? {
        val options = programs.flatMap { program -> program.periods.map { RecordbookSelection(program, it) } }
        val half = StudyHalf.of(time.today())
        return options.firstOrNull { it.period.studyHalf() == half }
            ?: options.firstOrNull { it.period.actual }
            ?: options.minByOrNull { it.period.semester }
    }
}
