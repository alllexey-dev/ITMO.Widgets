package dev.alllexey.itmowidgets.feature.sport.presentation.sign

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.alllexey.itmowidgets.core.lesson.LessonDay
import dev.alllexey.itmowidgets.core.presentation.EventQueue
import dev.alllexey.itmowidgets.core.presentation.RefreshMode
import dev.alllexey.itmowidgets.core.presentation.RefreshTracker
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.result.LoadState
import dev.alllexey.itmowidgets.core.result.errorOrNull
import dev.alllexey.itmowidgets.core.result.valueOrNull
import dev.alllexey.itmowidgets.core.schedule.DayLessonsGateway
import dev.alllexey.itmowidgets.core.text.DateTexts
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.core.text.toUiText
import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import dev.alllexey.itmowidgets.feature.sport.domain.model.SectionName
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportFilterCatalog
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportLesson
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportSignDisplayOptions
import dev.alllexey.itmowidgets.feature.sport.domain.repository.SportDataRepository
import dev.alllexey.itmowidgets.feature.sport.domain.repository.SportScheduleRepository
import dev.alllexey.itmowidgets.feature.sport.domain.repository.SportSignPreferencesRepository
import dev.alllexey.itmowidgets.feature.sport.presentation.common.SportBookingAction
import dev.alllexey.itmowidgets.feature.sport.presentation.common.SportCommonDetailsArgs
import dev.alllexey.itmowidgets.feature.sport.presentation.common.SportLessonActions
import dev.alllexey.itmowidgets.feature.sport.presentation.common.SportSectionsHolder
import dev.alllexey.itmowidgets.feature.sport.presentation.common.SportLessonSheetPresenter
import dev.alllexey.itmowidgets.feature.sport.presentation.common.SportLessonSheetUi
import dev.alllexey.itmowidgets.feature.sport.presentation.common.bookingConditions
import dev.alllexey.itmowidgets.feature.sport.presentation.common.toDetailsArgs
import dev.alllexey.itmowidgets.shared.core.CoreStrings
import dev.alllexey.itmowidgets.shared.feature.sport.SportStrings
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.format
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

/**
 * `Запись` on both platforms: the merged catalog with the viewer's queues and friends, filtered by the search, the
 * chips and the more-filters sheet, one day at a time on a six-week strip, and the booking actions of its rows and of
 * the lesson sheet ([SportLessonActions]).
 */
class SportSignViewModel(
    private val scheduleRepository: SportScheduleRepository,
    private val dataRepository: SportDataRepository,
    private val preferences: SportSignPreferencesRepository,
    sections: SportSectionsHolder,
    timetable: DayLessonsGateway,
    private val sharedLessons: SportSharedLessonResolver,
    private val time: AcademicTimeProvider,
    delegate: SportBookingDelegate,
    autoSignFlow: SportAutoSignFlow,
) : ViewModel() {

    private val actions = SportLessonActions(viewModelScope, delegate, autoSignFlow, time)
    private val refreshes = RefreshTracker(viewModelScope)
    private val operations = MutableStateFlow(0)
    private val filters = MutableStateFlow(SportSignFilters(selectedDate = time.today()))
    private val filtersOpen = MutableStateFlow(false)
    private val eventQueue = EventQueue<SportSignEvent>()

    /** The last catalog answer, kept while a refresh runs or fails. */
    private var lessons: List<SportLesson>? = null
    private var input: SportSignInput? = null

    private val sources: Flow<Sources> = combine(
        scheduleRepository.observeSportSchedule().answered(),
        scheduleRepository.observeSportFilters().map { it.valueOrNull() }.onStart { emit(null) },
        timetable.observe(time.today(), time.today().plus(MAX_WEEKS_FORWARD + 1, DateTimeUnit.WEEK)).onStart { emit(emptyList()) },
        combine(sections.sectionNames(), preferences.observeDisplayOptions(), ::Prefs),
        combine(operations, refreshes.refreshing) { count, byUser -> (count > 0) to byUser },
    ) { schedule, catalog, days, prefs, (running, byUser) -> Sources(schedule, catalog, days, prefs, running, byUser) }

    val uiState: StateFlow<SportSignUiState> = combine(
        sources,
        filters,
        filtersOpen,
        actions.busy,
        actions.prompt,
    ) { sources, filters, open, busy, prompt -> state(sources, filters, open, busy).copy(prompt = prompt) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, SportSignUiState())

    val events: Flow<SportSignEvent> = merge(eventQueue.events, actions.notices.map { SportSignEvent.Notice(it) })

    init {
        refresh(RefreshMode.Silent)
        // A source that failed behind the shown day says so once, until it recovers.
        viewModelScope.launch {
            uiState.map { (it.body as? SportSignBody.Day)?.partialError == true }.distinctUntilChanged().collect { partial ->
                if (partial) eventQueue.send(SportSignEvent.Notice(UiText.Res(CoreStrings.common_partial_load_error)))
            }
        }
    }

    /** A pull or a retry shows the indicator; the first load stays silent under the strip. */
    fun refresh(mode: RefreshMode) {
        refreshes.launch(mode) { tracked { refreshAll() } }
    }

    fun setQuery(query: String) = filters.update { it.copy(query = query) }

    fun toggle(chip: SportQuickFilter) = filters.update {
        when (chip) {
            SportQuickFilter.FREE -> it.copy(onlyFree = !it.onlyFree)
            SportQuickFilter.MORNING -> it.copy(dayPart = if (it.dayPart == SportDayPart.MORNING) SportDayPart.ANYTIME else SportDayPart.MORNING)
            SportQuickFilter.EVENING -> it.copy(dayPart = if (it.dayPart == SportDayPart.EVENING) SportDayPart.ANYTIME else SportDayPart.EVENING)
            SportQuickFilter.PINNED -> it.copy(onlyPinned = !it.onlyPinned)
            SportQuickFilter.FRIENDS -> it.copy(onlyFriends = !it.onlyFriends)
        }
    }

    fun selectDate(date: LocalDate) = filters.update { it.copy(selectedDate = date) }

    /** A swipe to the week [index] of the strip: today in the current week, its Monday in another. */
    fun showWeek(index: Int) {
        val today = time.today()
        val week = index.coerceIn(0, MAX_WEEKS_FORWARD)
        if (today.weeksUntil(filters.value.selectedDate) == week) return
        selectDate(if (week == 0) today else today.weekMonday().plus(week, DateTimeUnit.WEEK))
    }

    fun openFilters() {
        filtersOpen.value = true
    }

    fun closeFilters() {
        filtersOpen.value = false
    }

    fun toggleSection(name: String) = filters.update {
        val section = SectionName(name)
        it.copy(sections = if (section in it.sections) it.sections - section else it.sections + section)
    }

    fun selectBuilding(name: String?) = filters.update { it.copy(building = name) }

    fun selectTeacher(name: String?) = filters.update { it.copy(teacher = name) }

    fun selectDayPart(part: SportDayPart) = filters.update { it.copy(dayPart = part) }

    fun setOnlyFree(on: Boolean) = filters.update { it.copy(onlyFree = on) }

    fun setOnlyPinned(on: Boolean) = filters.update { it.copy(onlyPinned = on) }

    fun setOnlyFriends(on: Boolean) = filters.update { it.copy(onlyFriends = on) }

    /** Every filter off; the search and the day stay. */
    fun resetFilters() = filters.update { it.cleared() }

    /** The way out of an empty day. */
    fun onEmptyAction(action: SportEmptyAction) = when (action) {
        is SportEmptyAction.ShowDay -> selectDate(action.date)
        is SportEmptyAction.ResetFilters -> resetFilters()
    }

    /** The row's own button: `Записаться` or `В очередь`, while the lesson still offers it. */
    fun act(lessonId: Long) {
        val lesson = lesson(lessonId) ?: return
        val action = lesson.bookingConditions().evaluate(time.now()).action
        if (action == SportBookingAction.SIGN || action == SportBookingAction.AUTO) actions.run(lesson, action)
    }

    /** The sheet of [lessonId] as the catalog has it now, also when the filters hide it after a link opened it. */
    fun details(lessonId: Long): SportCommonDetailsArgs? {
        val lesson = lesson(lessonId) ?: return null
        val clash = SportSignPresenter.clashingLesson(lesson, input?.timetable.orEmpty(), time.timeZone)
        return lesson.toDetailsArgs().copy(clashSubject = clash?.subject?.trim(), clashStart = clash?.start?.format(DateTexts.TIME))
    }

    /** The lesson sheet asked for [action] on [lessonId]; see [SportLessonActions.runOffered]. */
    fun onSheetAction(lessonId: Long, action: SportBookingAction) {
        lesson(lessonId)?.let { actions.runOffered(it, action) }
    }

    /** `Встать в очередь` or `Выйти` in the prompt; [lateSign] is the switch of a real lesson's queue. */
    /** The lesson sheet of [details] now, as the iOS host draws it; [submitted] once it has sent its action. */
    fun sheet(details: SportCommonDetailsArgs, submitted: Boolean): SportLessonSheetUi =
        SportLessonSheetPresenter.present(details, time.now(), time.timeZone, busy = isBusy(details.lessonId), submitted = submitted)

    /** Whether a request for [lessonId] is in flight: its sheet opens with the action disabled. */
    fun isBusy(lessonId: Long): Boolean = lessonId in actions.busy.value

    fun confirmPrompt(lateSign: Boolean) = actions.confirm(lateSign)

    fun dismissPrompt() = actions.dismiss()

    /** A shared link: its day is selected and its sheet opens once the catalog answers. */
    fun openSharedLesson(lessonId: Long, predicted: Boolean) {
        viewModelScope.launch {
            val event = when (val found = sharedLessons.resolve(lessonId, predicted)) {
                is SportLinkedLesson.Found -> {
                    selectDate(found.lesson.start.toLocalDateTime(time.timeZone).date)
                    SportSignEvent.OpenLesson(found.lesson.lessonId)
                }
                SportLinkedLesson.Unavailable -> SportSignEvent.Notice(UiText.Res(SportStrings.sport_link_unavailable_text))
                is SportLinkedLesson.Failed -> SportSignEvent.Notice(found.error.toUiText())
            }
            eventQueue.send(event)
        }
    }

    /**
     * The lesson [lessonId] names now. A prediction keeps its prototype's id, two weeks apart: the shown day's comes
     * first, then the one a link opened, then any.
     */
    private fun lesson(lessonId: Long): SportLesson? {
        val all = lessons.orEmpty()
        val candidates = all.filter { it.lessonId == lessonId && it.end > time.now() }
        return candidates.firstOrNull { it.start.toLocalDateTime(time.timeZone).date == filters.value.selectedDate }
            ?: sharedLessons.linkedLesson(lessonId, all)
            ?: candidates.firstOrNull()
    }

    private fun state(sources: Sources, filters: SportSignFilters, open: Boolean, busy: Set<Long>): SportSignUiState {
        val schedule = sources.schedule
        schedule?.valueOrNull()?.let { lessons = it }
        val loaded = lessons
        val input = SportSignInput(
            lessons = loaded.orEmpty(),
            catalog = sources.catalog ?: EMPTY_CATALOG,
            filters = filters,
            pinned = sources.prefs.pinned,
            busy = busy,
            timetable = sources.days,
            showTeacher = !sources.prefs.display.hideTeacherSelector,
            showDayPart = !sources.prefs.display.hideTimeSelector,
            now = time.now(),
            today = time.today(),
            zone = time.timeZone,
        ).also { input = it }
        val state = SportSignPresenter.present(input, open)
        val error: AppError? = schedule?.errorOrNull()
        val body = when {
            loaded == null && error != null && !sources.running -> SportSignBody.Failed(error)
            loaded == null -> SportSignBody.Loading
            else -> (state.body as SportSignBody.Day).copy(refreshing = sources.byUser, partialError = error != null)
        }
        return state.copy(body = body)
    }

    private suspend fun refreshAll() {
        coroutineScope {
            awaitAll(
                async { scheduleRepository.refreshSportFilters() },
                async { scheduleRepository.refreshSportTimeSlots() },
                async { scheduleRepository.refreshSportSchedule() },
                async { dataRepository.refreshSportQueueEntries() },
                async { dataRepository.refreshSportQueues() },
                async { dataRepository.refreshFriendsBookings() },
            )
        }
    }

    private suspend fun tracked(operation: suspend () -> Unit) {
        operations.update { it + 1 }
        try {
            operation()
        } finally {
            operations.update { it - 1 }
        }
    }

    /** Null until the source answers for the first time. */
    private fun <T> Flow<LoadState<T>>.answered(): Flow<LoadState<T>?> = map<LoadState<T>, LoadState<T>?> { it }.onStart { emit(null) }

    private class Prefs(val pinned: Set<SectionName>, val display: SportSignDisplayOptions)

    private class Sources(
        val schedule: LoadState<List<SportLesson>>?,
        val catalog: SportFilterCatalog?,
        val days: List<LessonDay>,
        val prefs: Prefs,
        val running: Boolean,
        val byUser: Boolean,
    )

    private companion object {
        val EMPTY_CATALOG = SportFilterCatalog(emptyList(), emptyList(), emptyList(), emptyList())
    }
}
