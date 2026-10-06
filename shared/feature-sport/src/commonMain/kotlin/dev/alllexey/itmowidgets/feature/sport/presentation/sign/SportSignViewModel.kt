package dev.alllexey.itmowidgets.feature.sport.presentation.sign

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.alllexey.itmowidgets.core.presentation.BusyKeys
import dev.alllexey.itmowidgets.core.presentation.EventQueue
import dev.alllexey.itmowidgets.core.presentation.RefreshMode
import dev.alllexey.itmowidgets.core.presentation.RefreshTracker
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.result.LoadState
import dev.alllexey.itmowidgets.core.result.errorOrNull
import dev.alllexey.itmowidgets.core.result.valueOrNull
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import dev.alllexey.itmowidgets.feature.sport.domain.model.SectionName
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportFilterCatalog
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportLesson
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportTimeSlot
import dev.alllexey.itmowidgets.feature.sport.domain.repository.SportDataRepository
import dev.alllexey.itmowidgets.feature.sport.domain.repository.SportScheduleRepository
import dev.alllexey.itmowidgets.feature.sport.domain.repository.SportSignPreferencesRepository
import dev.alllexey.itmowidgets.shared.feature.sport.Res
import dev.alllexey.itmowidgets.shared.feature.sport.sport_auto_sign_existing_day
import dev.alllexey.itmowidgets.shared.feature.sport.sport_auto_sign_existing_entry
import dev.alllexey.itmowidgets.shared.feature.sport.sport_auto_sign_free_description
import dev.alllexey.itmowidgets.shared.feature.sport.sport_auto_sign_future_description
import dev.alllexey.itmowidgets.shared.feature.sport.sport_auto_sign_limit_reached
import dev.alllexey.itmowidgets.shared.feature.sport.sport_auto_sign_title
import dev.alllexey.itmowidgets.shared.feature.sport.sport_community_services_disabled
import dev.alllexey.itmowidgets.shared.feature.sport.sport_sign_success
import dev.alllexey.itmowidgets.shared.feature.sport.sport_unsign_success
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.toLocalDateTime

class SportSignViewModel(
    private val sportScheduleRepository: SportScheduleRepository,
    private val sportDataRepository: SportDataRepository,
    private val filterController: SportSignFilterController,
    private val stateFactory: SportSignStateFactory,
    private val bookingDelegate: SportBookingDelegate,
    private val autoSignFlow: SportAutoSignFlow,
    private val sharedLessons: SportSharedLessonResolver,
    preferencesRepository: SportSignPreferencesRepository,
    private val timeProvider: AcademicTimeProvider
) : ViewModel() {

    private val refreshes = RefreshTracker(viewModelScope)
    private val busyLessons = BusyKeys<Long>(viewModelScope)
    private val autoSignCommands = BusyKeys<Unit>(viewModelScope)
    private val eventQueue = EventQueue<SportSignEvent>()

    /** Refreshes in flight, silent ones included: the calendar shows over a placeholder until the catalog answers. */
    private val operations = MutableStateFlow(0)
    private var autoSignJob: Job? = null
    private var lastContent: SportSignUiState.Content? = null

    /** Every merged lesson of the last catalog answer, before filters. */
    private var catalogLessons: List<SportLesson> = emptyList()

    // Every source starts as "not answered yet", so the header renders before the catalogue.
    private val contentState: Flow<SportSignUiState> = combine(
        sportScheduleRepository.observeSportFilters().unansweredFirst(),
        sportScheduleRepository.observeSportTimeSlots().unansweredFirst(),
        sportScheduleRepository.observeSportSchedule().unansweredFirst(),
        combine(operations, refreshes.refreshing) { count, byUser -> (count > 0) to byUser },
        filterController.filters
    ) { filters, timeSlots, schedule, (refreshing, byUser), userFilters ->
        toContentState(filters, timeSlots, schedule, refreshing, byUser, userFilters)
    }

    val uiState: StateFlow<SportSignUiState> = combine(
        contentState,
        preferencesRepository.observeDisplayOptions(),
        busyLessons.busy
    ) { state, displayOptions, busyLessonIds ->
        if (state !is SportSignUiState.Content) return@combine state
        state.copy(
            hideTeacherSelector = displayOptions.hideTeacherSelector,
            hideTimeSelector = displayOptions.hideTimeSelector,
            busyLessonIds = busyLessonIds
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, SportSignUiState.Loading)

    val events: Flow<SportSignEvent> = eventQueue.events

    init {
        refresh(RefreshMode.Silent)
    }

    /** A pull or a retry shows the indicator; the first load stays silent under the calendar. */
    fun refresh(mode: RefreshMode) {
        refreshes.launch(mode) { tracked { refreshAll() } }
    }

    fun selectDate(date: LocalDate) = filterController.selectDate(date)

    /** Opens the card of a lesson from a shared link and selects its day, once the merged catalog answers. */
    fun openSharedLesson(lessonId: Long, predicted: Boolean = false) {
        viewModelScope.launch {
            val event = sharedLessons.resolve(lessonId, predicted)
            if (event is SportSignEvent.OpenLessonDetails) {
                selectDate(event.lesson.start.toLocalDateTime(timeProvider.timeZone).date)
            }
            eventQueue.send(event)
        }
    }

    /** The current state of the lesson a link opened, also when filters hide it from the list. */
    fun linkedLesson(lessonId: Long): SportLesson? = sharedLessons.linkedLesson(lessonId, catalogLessons)

    fun resetFilters() = filterController.reset()

    fun nextWeek() = filterController.nextWeek()

    fun prevWeek() = filterController.previousWeek()

    fun showOnlyAvailable(show: Boolean) = filterController.showOnlyAvailable(show)

    fun showOnlyFriends(show: Boolean) = filterController.showOnlyFriends(show)

    fun showAutoSign(show: Boolean) = filterController.showAutoSign(show)

    fun selectSports(names: Set<SectionName>) = filterController.selectSports(names)

    fun selectBuilding(name: String?) = filterController.selectBuilding(name)

    fun selectTeacher(name: String?) = filterController.selectTeacher(name)

    fun selectTime(time: String?) = filterController.selectTime(time)

    fun signUpForLesson(lesson: SportLesson) {
        performLessonAction(lesson.lessonId, UiText.Res(Res.string.sport_sign_success)) {
            bookingDelegate.signIn(lesson)
        }
    }

    fun unSignForLesson(lesson: SportLesson) {
        performLessonAction(lesson.lessonId, UiText.Res(Res.string.sport_unsign_success)) {
            bookingDelegate.signOut(lesson)
        }
    }

    /** A newer tap replaces a decision still waiting for the auto-sign limits. */
    fun handleAutoSignClick(lesson: SportLesson) {
        autoSignJob?.cancel()
        autoSignJob = viewModelScope.launch { eventQueue.send(autoSignFlow.onClick(lesson).toEvent()) }
    }

    /** A confirmation while a command is in flight is ignored. */
    fun executeAutoSignCommand(command: SportSignCommand, forceSign: Boolean = false) {
        autoSignCommands.launch(Unit) {
            val result = autoSignFlow.execute(command, forceSign)
            if (result is AppResult.Failure) eventQueue.send(SportSignEvent.ShowError(result.error))
        }
    }

    private suspend fun refreshAll() {
        coroutineScope {
            awaitAll(
                async { sportScheduleRepository.refreshSportFilters() },
                async { sportScheduleRepository.refreshSportTimeSlots() },
                async { sportScheduleRepository.refreshSportSchedule() },
                async { sportDataRepository.refreshSportQueueEntries() },
                async { sportDataRepository.refreshSportQueues() },
                async { sportDataRepository.refreshFriendsBookings() }
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

    /**
     * Booking actions mark only their own lesson as busy instead of refreshing: a screen-wide loading state would
     * hide the list the user is working with and still leave the tapped button clickable. A second tap on a busy
     * lesson sends nothing.
     */
    private fun performLessonAction(lessonId: Long, successMessage: UiText, action: suspend () -> AppResult<Unit>) {
        busyLessons.launch(lessonId) {
            val event = when (val result = action()) {
                is AppResult.Success -> SportSignEvent.ShowToast(successMessage)
                is AppResult.Failure -> SportSignEvent.ShowError(result.error)
            }
            eventQueue.send(event)
        }
    }

    private fun SportAutoSignDecision.toEvent(): SportSignEvent = when (this) {
        SportAutoSignDecision.ServicesDisabled ->
            SportSignEvent.ShowInfoDialog(message = UiText.Res(Res.string.sport_community_services_disabled))
        is SportAutoSignDecision.LeaveQueue -> SportSignEvent.ShowAutoSignDeleteDialog(
            message = UiText.Res(Res.string.sport_auto_sign_existing_entry, listOf(position, total)),
            command = command
        )
        is SportAutoSignDecision.ConfirmFreeSign -> SportSignEvent.ShowAutoSignConfirmDialog(
            title = UiText.Res(Res.string.sport_auto_sign_title),
            message = UiText.Res(Res.string.sport_auto_sign_free_description),
            showForceSignButton = true,
            command = command
        )
        is SportAutoSignDecision.ConfirmAutoSign -> SportSignEvent.ShowAutoSignConfirmDialog(
            title = UiText.Res(Res.string.sport_auto_sign_title),
            message = UiText.Res(Res.string.sport_auto_sign_future_description),
            showForceSignButton = false,
            command = command
        )
        is SportAutoSignDecision.DayTaken -> SportSignEvent.ShowInfoDialog(
            message = UiText.Res(Res.string.sport_auto_sign_existing_day, listOf(section, teacher))
        )
        is SportAutoSignDecision.LimitReached -> SportSignEvent.ShowInfoDialog(
            message = UiText.Res(Res.string.sport_auto_sign_limit_reached, listOf(nextAvailable))
        )
        is SportAutoSignDecision.Failed -> SportSignEvent.ShowError(error)
    }

    private fun toContentState(
        filtersState: AppResult<SportFilterCatalog>?,
        timeSlotsState: AppResult<List<SportTimeSlot>>?,
        scheduleState: LoadState<List<SportLesson>>?,
        refreshing: Boolean,
        byUser: Boolean,
        userFilters: SportSignFilters
    ): SportSignUiState {
        val filters = filtersState?.valueOrNull()
        val timeSlots = timeSlotsState?.valueOrNull()
        val lessons = scheduleState?.valueOrNull()
        lessons?.let { catalogLessons = it }
        val errors = listOfNotNull(
            filtersState?.errorOrNull(),
            timeSlotsState?.errorOrNull(),
            scheduleState?.errorOrNull()
        )

        if (lessons != null && filters != null && timeSlots != null) {
            return stateFactory.create(lessons, filters, timeSlots, userFilters, hasPartialError = errors.isNotEmpty())
                .copy(refreshing = byUser)
                .also { lastContent = it }
        }
        val previous = lastContent
        return when {
            // Sources that failed keep the last content on screen with a snackbar.
            refreshing && previous != null -> previous.copy(refreshing = byUser)
            // The calendar is deterministic: it shows at once over a placeholder list.
            refreshing -> stateFactory.create(
                lessons = emptyList(),
                catalog = filters ?: EMPTY_CATALOG,
                timeSlots = timeSlots.orEmpty(),
                userFilters = userFilters,
                hasPartialError = false
            ).copy(initialLoading = true)
            previous != null -> previous.copy(hasPartialError = true, refreshing = false)
            errors.isNotEmpty() -> SportSignUiState.Error(errors.first())
            // Nothing answered and nothing running: the screen has not started loading yet.
            else -> SportSignUiState.Loading
        }
    }

    /** Null until the source answers for the first time. */
    private fun <T> Flow<T>.unansweredFirst(): Flow<T?> = map<T, T?> { it }.onStart { emit(null) }

    private companion object {
        val EMPTY_CATALOG = SportFilterCatalog(emptyList(), emptyList(), emptyList(), emptyList())
    }
}
