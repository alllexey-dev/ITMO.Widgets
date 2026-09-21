package dev.alllexey.itmowidgets.feature.sport.presentation.sign

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.core.util.dataOrNull
import dev.alllexey.itmowidgets.core.util.errorOrNull
import dev.alllexey.itmowidgets.feature.sport.presentation.common.bookingConditions
import dev.alllexey.itmowidgets.feature.sport.domain.model.SectionName
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportAutoSignEntry
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportFreeSignEntry
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportLesson
import dev.alllexey.itmowidgets.feature.sport.domain.repository.SportDataRepository
import dev.alllexey.itmowidgets.feature.sport.domain.repository.SportScheduleRepository
import dev.alllexey.itmowidgets.feature.sport.domain.repository.SportSignPreferencesRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportFilterCatalog
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import javax.inject.Inject

@HiltViewModel
class SportSignViewModel @Inject constructor(
    private val sportScheduleRepository: SportScheduleRepository,
    private val sportDataRepository: SportDataRepository,
    private val filterController: SportSignFilterController,
    private val stateFactory: SportSignStateFactory,
    private val bookingDelegate: SportBookingDelegate,
    private val preferencesRepository: SportSignPreferencesRepository
) : ViewModel() {

    private val mutableUiState = MutableStateFlow<SportSignUiState>(SportSignUiState.Loading)
    val uiState: StateFlow<SportSignUiState> = mutableUiState.asStateFlow()

    private val eventChannel = Channel<SportSignEvent>(Channel.BUFFERED)
    val events: Flow<SportSignEvent> = eventChannel.receiveAsFlow()

    val userFiltersFlow: StateFlow<SportSignFilters> = filterController.filters

    val sportSections: List<SectionName>
        get() = (uiState.value as? SportSignUiState.Content)?.availableSports.orEmpty()

    val usedSportNames: Set<SectionName>
        get() = (uiState.value as? SportSignUiState.Content)?.usedSportNames.orEmpty()

    /** Any refresh in flight. */
    private val activeOperations = MutableStateFlow(0)
    /** Only user-initiated refreshes; drives `Content.refreshing`. */
    private val userOperations = MutableStateFlow(0)
    private val inFlightLessons = InFlightLessons()
    private var refreshJob: Job? = null
    private var autoSignJob: Job? = null
    private var autoSignCommandJob: Job? = null
    private var lastContent: SportSignUiState.Content? = null

    init {
        observeData()
        refreshAllData(silent = true)
    }

    /** A pull shows the indicator; the first load stays silent under the calendar. */
    fun refreshAllData(silent: Boolean = false) {
        launchRefresh(silent) {
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
    }

    fun refreshMyItmoData() {
        launchRefresh {
            coroutineScope {
                awaitAll(
                    async { sportScheduleRepository.refreshSportFilters() },
                    async { sportScheduleRepository.refreshSportTimeSlots() },
                    async { sportScheduleRepository.refreshSportSchedule() }
                )
            }
        }
    }

    fun refreshCustomData() {
        launchRefresh {
            coroutineScope {
                awaitAll(
                    async { sportDataRepository.refreshSportQueueEntries() },
                    async { sportDataRepository.refreshSportQueues() },
                    async { sportDataRepository.refreshFriendsBookings() }
                )
            }
        }
    }

    fun selectDate(date: LocalDate) = filterController.selectDate(date)

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
        performLessonAction(
            lessonId = lesson.lessonId,
            action = { bookingDelegate.signIn(lesson) },
            successMessage = R.string.sport_sign_success
        )
    }

    fun unSignForLesson(lesson: SportLesson) {
        performLessonAction(
            lessonId = lesson.lessonId,
            action = { bookingDelegate.signOut(lesson) },
            successMessage = R.string.sport_unsign_success
        )
    }

    fun handleAutoSignClick(lesson: SportLesson) {
        autoSignJob?.cancel()
        autoSignJob = viewModelScope.launch {
            if (!bookingDelegate.areCommunityServicesEnabled()) {
                eventChannel.send(
                    SportSignEvent.ShowInfoDialog(
                        message = UiText.Resource(R.string.sport_community_services_disabled)
                    )
                )
                return@launch
            }

            when (val entry = lesson.signEntry.takeIf { lesson.bookingConditions().hasActiveQueue }) {
                is SportFreeSignEntry -> showDeleteDialog(
                    position = entry.position,
                    total = entry.total,
                    command = SportSignCommand.CancelFreeSign(entry.id)
                )

                is SportAutoSignEntry -> showDeleteDialog(
                    position = entry.position,
                    total = entry.total,
                    command = SportSignCommand.CancelAutoSign(entry.id)
                )

                null -> {
                    if (lesson.isLessonReal) {
                        showCreateFreeSignDialog(lesson)
                    } else {
                        showCreateAutoSignDialog(lesson)
                    }
                }
            }
        }
    }

    fun executeAutoSignCommand(command: SportSignCommand, forceSign: Boolean = false) {
        if (autoSignCommandJob?.isActive == true) return
        autoSignCommandJob = viewModelScope.launch {
            val result = when (command) {
                is SportSignCommand.CreateFreeSign ->
                    bookingDelegate.createFreeSign(command.lessonId, forceSign)
                is SportSignCommand.CancelFreeSign ->
                    bookingDelegate.cancelFreeSign(command.entryId)
                is SportSignCommand.CreateAutoSign ->
                    bookingDelegate.createAutoSign(command.prototypeLessonId)
                is SportSignCommand.CancelAutoSign ->
                    bookingDelegate.cancelAutoSign(command.entryId)
            }

            if (result is AppResult.Failure) {
                eventChannel.send(SportSignEvent.ShowError(result.error))
            }
        }
    }

    private fun observeData() {
        viewModelScope.launch {
            val operations = combine(activeOperations, userOperations) { all, user -> all to user }
            // Every source starts as "not answered yet", so the header renders before the catalogue.
            val contentState = combine(
                sportScheduleRepository.observeSportFilters().unansweredFirst(),
                sportScheduleRepository.observeSportTimeSlots().unansweredFirst(),
                sportScheduleRepository.observeSportSchedule().unansweredFirst(),
                operations,
                filterController.filters
            ) { filtersState, timeSlotsState, scheduleState, (operationCount, userCount), userFilters ->
                val refreshing = operationCount > 0
                val byUser = userCount > 0
                val filters = filtersState?.dataOrNull()
                val timeSlots = timeSlotsState?.dataOrNull()
                val lessons = scheduleState?.dataOrNull()
                val errors = listOfNotNull(
                    filtersState?.errorOrNull(),
                    timeSlotsState?.errorOrNull(),
                    scheduleState?.errorOrNull()
                )

                if (lessons == null || filters == null || timeSlots == null) {
                    val previous = lastContent
                    when {
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
                } else {
                    stateFactory.create(
                        lessons = lessons,
                        catalog = filters,
                        timeSlots = timeSlots,
                        userFilters = userFilters,
                        hasPartialError = errors.isNotEmpty()
                    ).copy(refreshing = byUser).also { lastContent = it }
                }
            }

            combine(
                contentState,
                preferencesRepository.observeDisplayOptions(),
                inFlightLessons.ids
            ) { state, displayOptions, busyLessonIds ->
                if (state is SportSignUiState.Content) {
                    state.copy(
                        hideTeacherSelector = displayOptions.hideTeacherSelector,
                        hideTimeSelector = displayOptions.hideTimeSelector,
                        busyLessonIds = busyLessonIds
                    )
                } else {
                    state
                }
            }.collect(mutableUiState::emit)
        }
    }

    /** Null until the source answers for the first time. */
    private fun <T> Flow<T>.unansweredFirst(): Flow<T?> = map<T, T?> { it }.onStart { emit(null) }

    private fun launchRefresh(silent: Boolean = false, action: suspend () -> Unit) {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            if (silent) trackOperation(action) else trackUserOperation(action)
        }
    }

    private suspend fun trackUserOperation(action: suspend () -> Unit) {
        userOperations.value += 1
        try {
            trackOperation(action)
        } finally {
            userOperations.value = (userOperations.value - 1).coerceAtLeast(0)
        }
    }

    /**
     * Booking actions mark only their own lesson as busy instead of going through
     * [trackOperation]: a screen-wide loading state would hide the list the user is
     * working with and still leave the tapped button clickable.
     */
    private fun performLessonAction(
        lessonId: Long,
        action: suspend () -> AppResult<Unit>,
        successMessage: Int
    ) {
        if (!inFlightLessons.tryStart(lessonId)) return

        viewModelScope.launch {
            try {
                when (val result = action()) {
                    is AppResult.Success -> {
                        eventChannel.send(
                            SportSignEvent.ShowToast(UiText.Resource(successMessage))
                        )
                    }

                    is AppResult.Failure -> {
                        eventChannel.send(SportSignEvent.ShowError(result.error))
                    }
                }
            } finally {
                inFlightLessons.finish(lessonId)
            }
        }
    }

    private suspend fun trackOperation(action: suspend () -> Unit) {
        activeOperations.value += 1
        try {
            action()
        } finally {
            activeOperations.value = (activeOperations.value - 1).coerceAtLeast(0)
        }
    }

    private suspend fun showDeleteDialog(
        position: Int,
        total: Int,
        command: SportSignCommand
    ) {
        eventChannel.send(
            SportSignEvent.ShowAutoSignDeleteDialog(
                message = UiText.Resource(
                    resourceId = R.string.sport_auto_sign_existing_entry,
                    arguments = listOf(position, total)
                ),
                command = command
            )
        )
    }

    private suspend fun showCreateFreeSignDialog(lesson: SportLesson) {
        eventChannel.send(
            SportSignEvent.ShowAutoSignConfirmDialog(
                title = UiText.Resource(R.string.sport_auto_sign_title),
                message = UiText.Resource(R.string.sport_auto_sign_free_description),
                showForceSignButton = true,
                command = SportSignCommand.CreateFreeSign(lesson.lessonId)
            )
        )
    }

    private suspend fun showCreateAutoSignDialog(lesson: SportLesson) {
        when (val result = bookingDelegate.loadAutoSignAvailability()) {
            is AppResult.Failure -> {
                eventChannel.send(SportSignEvent.ShowError(result.error))
            }

            is AppResult.Success -> {
                val availability = result.value
                val existingEntry = availability.entries
                    .filterIsInstance<SportAutoSignEntry>()
                    .find {
                        it.targetLesson.start.toLocalDate() == lesson.start.toLocalDate()
                    }

                when {
                    existingEntry != null -> {
                        val lessonData = existingEntry.targetLesson
                        eventChannel.send(
                            SportSignEvent.ShowInfoDialog(
                                message = UiText.Resource(
                                    resourceId = R.string.sport_auto_sign_existing_day,
                                    arguments = listOf(
                                        SectionName(lessonData.sectionName).shorten(),
                                        lessonData.teacherFio
                                    )
                                )
                            )
                        )
                    }

                    availability.limits.available > 0 -> {
                        eventChannel.send(
                            SportSignEvent.ShowAutoSignConfirmDialog(
                                title = UiText.Resource(R.string.sport_auto_sign_title),
                                message = UiText.Resource(
                                    R.string.sport_auto_sign_future_description
                                ),
                                showForceSignButton = false,
                                command = SportSignCommand.CreateAutoSign(lesson.lessonId)
                            )
                        )
                    }

                    else -> {
                        val nextDate = availability.limits.nextAvailableAt.format(
                            DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)
                        )
                        eventChannel.send(
                            SportSignEvent.ShowInfoDialog(
                                message = UiText.Resource(
                                    resourceId = R.string.sport_auto_sign_limit_reached,
                                    arguments = listOf(nextDate)
                                )
                            )
                        )
                    }
                }
            }
        }
    }

    private companion object {
        val EMPTY_CATALOG = SportFilterCatalog(emptyList(), emptyList(), emptyList(), emptyList())
    }
}
