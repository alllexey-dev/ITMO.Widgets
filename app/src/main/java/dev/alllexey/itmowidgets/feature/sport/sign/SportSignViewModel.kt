package dev.alllexey.itmowidgets.feature.sport.sign

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.core.util.dataOrNull
import dev.alllexey.itmowidgets.core.util.throwableOrNull
import dev.alllexey.itmowidgets.domain.model.sport.SectionName
import dev.alllexey.itmowidgets.domain.model.sport.SportAutoSignEntry
import dev.alllexey.itmowidgets.domain.model.sport.SportFreeSignEntry
import dev.alllexey.itmowidgets.domain.model.sport.SportLesson
import dev.alllexey.itmowidgets.domain.repository.SportDataRepository
import dev.alllexey.itmowidgets.domain.repository.SportScheduleRepository
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
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
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
    private val bookingDelegate: SportBookingDelegate
) : ViewModel() {

    private val mutableUiState = MutableStateFlow<SportSignUiState>(SportSignUiState.Loading)
    val uiState: StateFlow<SportSignUiState> = mutableUiState.asStateFlow()

    private val eventChannel = Channel<SportSignEvent>(Channel.BUFFERED)
    val events: Flow<SportSignEvent> = eventChannel.receiveAsFlow()

    val userFiltersFlow: StateFlow<SportSignFilters> = filterController.filters

    val sportSections: List<SectionName>
        get() = (uiState.value as? SportSignUiState.Success)?.availableSports.orEmpty()

    val usedSportNames: Set<SectionName>
        get() = (uiState.value as? SportSignUiState.Success)?.usedSportNames.orEmpty()

    private val activeOperations = MutableStateFlow(0)
    private var refreshJob: Job? = null
    private var autoSignJob: Job? = null

    init {
        observeData()
        refreshAllData()
    }

    fun refreshAllData() {
        launchRefresh {
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
            action = { bookingDelegate.signIn(lesson) },
            successMessage = R.string.sport_sign_success
        )
    }

    fun unSignForLesson(lesson: SportLesson) {
        performLessonAction(
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

            when (val entry = lesson.signEntry) {
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
        viewModelScope.launch {
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
            combine(
                sportScheduleRepository.observeSportFilters(),
                sportScheduleRepository.observeSportTimeSlots(),
                sportScheduleRepository.observeSportSchedule(),
                activeOperations,
                filterController.filters
            ) { filtersState, timeSlotsState, scheduleState, operationCount, userFilters ->
                if (operationCount > 0) {
                    return@combine SportSignUiState.Loading
                }

                val filters = filtersState.dataOrNull()
                val timeSlots = timeSlotsState.dataOrNull()
                val lessons = scheduleState.dataOrNull()
                val errors = listOfNotNull(
                    filtersState.throwableOrNull(),
                    timeSlotsState.throwableOrNull(),
                    scheduleState.throwableOrNull()
                )

                if (lessons == null || filters == null || timeSlots == null) {
                    SportSignUiState.Error(AppError.Unknown(errors.firstOrNull()))
                } else {
                    stateFactory.create(
                        lessons = lessons,
                        catalog = filters,
                        timeSlots = timeSlots,
                        userFilters = userFilters,
                        hasPartialError = errors.isNotEmpty()
                    )
                }
            }.collect(mutableUiState::emit)
        }
    }

    private fun launchRefresh(action: suspend () -> Unit) {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            trackOperation(action)
        }
    }

    private fun performLessonAction(
        action: suspend () -> AppResult<Unit>,
        successMessage: Int
    ) {
        viewModelScope.launch {
            trackOperation {
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
}
