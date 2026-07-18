package dev.alllexey.itmowidgets.feature.sport.sign

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import api.myitmo.MyItmo
import api.myitmo.model.sport.SportFilters
import api.myitmo.model.sport.TimeSlot
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.alllexey.itmowidgets.core.network.WidgetsClient
import dev.alllexey.itmowidgets.core.model.SportAutoSignEntry
import dev.alllexey.itmowidgets.core.model.SportAutoSignRequest
import dev.alllexey.itmowidgets.core.model.SportFreeSignEntry
import dev.alllexey.itmowidgets.core.model.SportFreeSignRequest
import dev.alllexey.itmowidgets.core.storage.AppSettingsStorage
import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import dev.alllexey.itmowidgets.core.util.dataOrNull
import dev.alllexey.itmowidgets.core.util.throwableOrNull
import dev.alllexey.itmowidgets.domain.model.sport.SectionName
import dev.alllexey.itmowidgets.domain.model.sport.SportLesson
import dev.alllexey.itmowidgets.domain.model.sport.UnavailableReason
import dev.alllexey.itmowidgets.domain.repository.ScheduleRepository
import dev.alllexey.itmowidgets.domain.repository.SportBookingRepository
import dev.alllexey.itmowidgets.domain.repository.SportDataRepository
import dev.alllexey.itmowidgets.domain.repository.SportScheduleRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale
import javax.inject.Inject

sealed class SportSignUiState {

    object Loading : SportSignUiState()

    data class Success(

        // region filters
        val availableSports: List<SectionName> = emptyList(),
        val availableBuildings: List<String> = emptyList(),
        val availableTeachers: List<String> = emptyList(),
        val availableTimeSlots: List<String> = emptyList(),

        val selectedSportNames: Set<SectionName> = emptySet(),
        val selectedBuildingName: String? = null,
        val selectedTeacherName: String? = null,
        val selectedTimeSlot: String? = null,
        val showOnlyAvailable: Boolean = true, // true by default
        val showAutoSign: Boolean = true, // true by default
        val showOnlyFriends: Boolean = false,

        val displayedWeek: List<CalendarDay> = emptyList(),
        val currentMonthName: String = "",
        val canGoToPrevWeek: Boolean = false,
        val canGoToNextWeek: Boolean = true,

        // endregion filters

        val displayedLessons: List<SportLesson> = emptyList(),

        val hasPartialError: Boolean = false
    ) : SportSignUiState()

    data class Error(val message: String) : SportSignUiState()
}

data class SportSignFilters(
    val selectedSportNames: Set<SectionName> = emptySet(),
    val selectedBuildingName: String? = null,
    val selectedTeacherName: String? = null,
    val selectedTimeSlot: String? = null,
    val showOnlyAvailable: Boolean = true,
    val showAutoSign: Boolean = true,
    val showOnlyFriends: Boolean = false,
    val selectedDate: LocalDate
)

sealed interface SportSignEvent {
    data class ShowToast(val message: String) : SportSignEvent
    data class ShowError(val message: String) : SportSignEvent
    data class ShowAutoSignConfirmDialog(
        val title: String,
        val message: String,
        val showForceSignButton: Boolean,
        val action: (forceSign: Boolean) -> Unit
    ) : SportSignEvent

    data class ShowAutoSignDeleteDialog(val message: String, val action: () -> Unit) :
        SportSignEvent

    data class ShowInfoDialog(val title: String? = null, val message: String) : SportSignEvent
}

data class CalendarDay(
    val date: LocalDate,
    val dayOfWeek: String,
    val dayOfMonth: String,
    val hasLessons: Boolean,
    val hasAvailableLessons: Boolean,
    val isSelected: Boolean,
    val isToday: Boolean
)

@HiltViewModel
class SportSignViewModel @Inject constructor(
    private val settings: AppSettingsStorage,
    private val myItmo: MyItmo,
    private val widgets: WidgetsClient,
    private val scheduleRepository: ScheduleRepository,
    private val sportBookingRepository: SportBookingRepository,
    private val sportScheduleRepository: SportScheduleRepository,
    private val sportDataRepository: SportDataRepository,
    private val timeProvider: AcademicTimeProvider
) : ViewModel() {

    private val _uiState = MutableStateFlow<SportSignUiState>(SportSignUiState.Loading)
    val uiState: SharedFlow<SportSignUiState> = _uiState

    private val _events = Channel<SportSignEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    var sportSections: List<SectionName> = listOf()
    var usedSportNames: List<SectionName> = listOf()

    private val _userFilters = MutableStateFlow(
        SportSignFilters(selectedDate = timeProvider.today())
    )
    val userFiltersFlow = _userFilters.asStateFlow()

    private val isRefreshing = MutableStateFlow(false)

    var observeJob: Job? = null

    init {
        observeData()
        refreshAllData()
    }

    fun refreshAllData() {
        viewModelScope.launch {
            isRefreshing.value = true
            try {
                awaitAll(
                    async { sportScheduleRepository.refreshSportFilters() },
                    async { sportScheduleRepository.refreshSportTimeSlots() },
                    async { sportScheduleRepository.refreshSportSchedule() },
                    async { sportDataRepository.refreshSportQueueEntries() },
                    async { sportDataRepository.refreshSportQueues() },
                    async { sportDataRepository.refreshFriendsBookings() }
                )
            } finally {
                isRefreshing.value = false
            }
        }
    }

    fun refreshMyItmoData() {
        viewModelScope.launch {
            isRefreshing.value = true
            try {
                awaitAll(
                    async { sportScheduleRepository.refreshSportFilters() },
                    async { sportScheduleRepository.refreshSportTimeSlots() },
                    async { sportScheduleRepository.refreshSportSchedule() }
                )
            } finally {
                isRefreshing.value = false
            }
        }
    }

    fun refreshCustomData() {
        viewModelScope.launch {
            isRefreshing.value = true
            try {
                awaitAll(
                    async { sportDataRepository.refreshSportQueueEntries() },
                    async { sportDataRepository.refreshSportQueues() },
                    async { sportDataRepository.refreshFriendsBookings() }
                )
            } finally {
                isRefreshing.value = false
            }
        }
    }

    private fun observeData() {
        observeJob?.cancel()

        observeJob = viewModelScope.launch {
            combine(
                sportScheduleRepository.observeSportFilters(),
                sportScheduleRepository.observeSportTimeSlots(),
                sportScheduleRepository.observeSportSchedule(),
                isRefreshing,
                userFiltersFlow
            ) { filtersState, timeSlotsState, scheduleState, refreshing, userFiltersFlow ->
                if (refreshing) {
                    return@combine SportSignUiState.Loading
                }

                val filters = filtersState.dataOrNull()
                val timeSlots = timeSlotsState.dataOrNull()
                val schedules = scheduleState.dataOrNull()

                val errors = listOfNotNull(
                    filtersState.throwableOrNull(),
                    timeSlotsState.throwableOrNull(),
                    scheduleState.throwableOrNull()
                )

                when {
                    schedules == null || filters == null || timeSlots == null -> {
                        errors.forEach { it.printStackTrace() }
                        SportSignUiState.Error("Не удалось загрузить данные")
                    }

                    else -> {
                        newSuccessState(
                            schedules,
                            filters,
                            timeSlots,
                            userFiltersFlow,
                            errors.isNotEmpty()
                        )
                    }
                }
            }
                .collect {
                    _uiState.value = it
                }
        }
    }

    fun newSuccessState(
        lessons: List<SportLesson>,
        filters: SportFilters,
        timeSlots: List<TimeSlot>,
        userFilters: SportSignFilters,
        hasPartialError: Boolean
    ): SportSignUiState.Success {
        userFilters.apply {

            val today = timeProvider.today()

            val allBuildingsMap = filters.buildingId.associate { it.id to it.value }
            val allTeachersMap = filters.teacherIsu.associate { it.id to it.value }
            val allTimeSlotsMap = timeSlots.associate { it.id to "${it.timeStart}-${it.timeEnd}" }

            // filter by sport
            val lessonsBySport = lessons.filter {
                selectedSportNames.isEmpty() or (it.sectionName in selectedSportNames)
            }

            fun SportLesson.realBuildingId(): Long {
                // online
                return if (roomId == -1L || buildingId == null) -1
                // present in filters
                else if (allBuildingsMap.contains(buildingId)) buildingId
                // other
                else 0
            }

            val selectedBuildingName = selectedBuildingName?.takeIf { name ->
                lessonsBySport.any { allBuildingsMap[it.realBuildingId()] == name }
            }

            val selectedBuildingId =
                allBuildingsMap.entries.find { it.value == selectedBuildingName }?.key

            // filter by building

            val lessonsByBuilding = if (selectedBuildingId != null) {
                lessonsBySport.filter { it.realBuildingId() == selectedBuildingId }
            } else {
                lessonsBySport
            }

            val validTeacherNames = lessonsByBuilding
                .mapNotNull { allTeachersMap[it.teacherIsu.toLong()] }
                .distinct()

            val selectedTeacherName = selectedTeacherName?.takeIf { it in validTeacherNames }
            val selectedTeacherIsu =
                allTeachersMap.entries.find { it.value == selectedTeacherName }?.key

            val timeSlotId = allTimeSlotsMap.entries.find { it.value == selectedTimeSlot }?.key
            val selectedTimeSlot = allTimeSlotsMap[timeSlotId]

            val filteredLessons = lessonsByBuilding.filter { lesson ->
                val matchesTime = timeSlotId == null || lesson.timeSlotId == timeSlotId
                val matchesTeacher =
                    selectedTeacherIsu == null || lesson.teacherIsu.toLong() == selectedTeacherIsu
                matchesTime && matchesTeacher
            }

            // filters

            val availableSports = lessons
                .map { it.sectionName }
                .distinct()
                .sortedBy { it.shorten() }

            val availableBuildings = lessonsBySport
                .mapNotNull { allBuildingsMap[it.realBuildingId()] }
                .distinct()
                .sorted()

            val availableTeachers = validTeacherNames.sorted()

            // today lessons

            val now = timeProvider.now()

            val finalFilteredLessons = filteredLessons
                .filter { it.end > now }
                .filter {
                    val isAvailable = it.available > 0 && it.canSignIn
                    val reasons = it.unavailableReasons
                    val isAutoSignAvailable =
                        showAutoSign && (reasons.isEmpty() || reasons.lastOrNull() == UnavailableReason.Full)
                    val availableCheck = !showOnlyAvailable || isAvailable || isAutoSignAvailable
                    val autoSignCheck = showAutoSign || (it.isLessonReal && it.available > 0)
                    it.signed || (availableCheck && autoSignCheck)
                }
                .filter {
                    !showOnlyFriends || it.friendsBookings.isNotEmpty()
                }
                .sortedWith(
                    compareBy<SportLesson> { !(it.signed || it.signEntry != null) }
                        .thenBy { it.start }
                        .thenBy { it.sectionName.shorten() }
                )

            val displayedLessons = finalFilteredLessons
                .filter { it.start.toLocalDate() == selectedDate }

            // calendar

            val startOfWeek = selectedDate.with(DayOfWeek.MONDAY)
            val days = (0..6).map { startOfWeek.plusDays(it.toLong()) }
            val datesWithLessons = finalFilteredLessons.map { it.start.toLocalDate() }.toSet()
            val datesWithAvailableLessons = finalFilteredLessons
                .filter { it.canSignIn }
                .map { it.start.toLocalDate() }
                .toSet()

            val calendarDays = days.map { date ->
                CalendarDay(
                    date = date,
                    dayOfWeek = date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                    dayOfMonth = date.dayOfMonth.toString(),
                    hasLessons = datesWithLessons.contains(date),
                    hasAvailableLessons = datesWithAvailableLessons.contains(date),
                    isSelected = date.isEqual(selectedDate),
                    isToday = date.isEqual(today)
                )
            }

            val monthName =
                days[3].month.getDisplayName(TextStyle.FULL_STANDALONE, Locale.getDefault())
                    .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }

            val currentMonday = today.with(DayOfWeek.MONDAY)
            val weekOffset = ChronoUnit.WEEKS
                .between(currentMonday, startOfWeek)
                .toInt()

            sportSections = availableSports
            usedSportNames =
                lessons.filter { it.signed || it.signEntry != null }.map { it.sectionName }

            return SportSignUiState.Success(
                availableSports = availableSports,
                availableBuildings = listOf(ANY_BUILDING_KEY) + availableBuildings,
                availableTeachers = listOf(ANY_TEACHER_KEY) + availableTeachers,
                availableTimeSlots = listOf(ANY_TIME_KEY) + allTimeSlotsMap.values,
                selectedSportNames = selectedSportNames,
                selectedBuildingName = selectedBuildingName,
                selectedTeacherName = selectedTeacherName,
                selectedTimeSlot = selectedTimeSlot,
                showOnlyAvailable = showOnlyAvailable,
                showAutoSign = showAutoSign,
                showOnlyFriends = showOnlyFriends,
                displayedWeek = calendarDays,
                currentMonthName = monthName,
                canGoToPrevWeek = weekOffset > 0,
                canGoToNextWeek = weekOffset < MAX_WEEKS_FORWARD,
                displayedLessons = displayedLessons,
                hasPartialError = hasPartialError
            )
        }
    }

    // filters

    fun selectDate(date: LocalDate) {
        _userFilters.value = _userFilters.value.copy(selectedDate = date)
    }

    fun nextWeek() {
        val startOfWeek = _userFilters.value.selectedDate.with(DayOfWeek.MONDAY)
        val today = timeProvider.today()
        val currentMonday = today.with(DayOfWeek.MONDAY)
        val weekOffset = ChronoUnit.WEEKS
            .between(currentMonday, startOfWeek)
            .toInt()
        if (weekOffset < MAX_WEEKS_FORWARD) {
            _userFilters.value = _userFilters.value.copy(
                selectedDate = today.plusWeeks(weekOffset + 1L)
                    .with(DayOfWeek.MONDAY)
            )
        }
    }

    fun prevWeek() {
        val startOfWeek = _userFilters.value.selectedDate.with(DayOfWeek.MONDAY)
        val today = timeProvider.today()
        val currentMonday = today.with(DayOfWeek.MONDAY)
        val weekOffset = ChronoUnit.WEEKS
            .between(currentMonday, startOfWeek)
            .toInt()

        if (weekOffset > 0) {
            _userFilters.value = _userFilters.value.copy(
                selectedDate = today.plusWeeks(weekOffset - 1L)
                    .with(DayOfWeek.MONDAY)
            )
        }
    }

    fun showOnlyAvailable(show: Boolean) {
        _userFilters.value = _userFilters.value.copy(showOnlyAvailable = show)
    }

    fun showOnlyFriends(show: Boolean) {
        _userFilters.value = _userFilters.value.copy(showOnlyFriends = show)
    }

    fun showAutoSign(show: Boolean) {
        _userFilters.value = _userFilters.value.copy(showAutoSign = show)
    }

    fun selectSports(names: Set<SectionName>) {
        _userFilters.value = _userFilters.value.copy(selectedSportNames = names)
    }

    fun selectBuilding(name: String?) {
        _userFilters.value =
            _userFilters.value.copy(selectedBuildingName = if (name == ANY_BUILDING_KEY) null else name)
    }

    fun selectTeacher(name: String?) {
        _userFilters.value =
            _userFilters.value.copy(selectedTeacherName = if (name == ANY_TEACHER_KEY) null else name)
    }

    fun selectTime(time: String?) {
        _userFilters.value =
            _userFilters.value.copy(selectedTimeSlot = if (time == ANY_TIME_KEY) null else time)
    }

    fun signUpForLesson(lesson: SportLesson) {
        performLessonAction(
            action = {
                myItmo.api.signInLessons(listOf(lesson.lessonId)).execute()
                CoroutineScope(Dispatchers.IO).launch {
                    awaitAll(
                        async { sportBookingRepository.refreshSportBookings() },
                        async {
                            scheduleRepository.refreshSchedule(
                                null,
                                lesson.start.toLocalDate(),
                                lesson.end.toLocalDate()
                            )
                        }
                    )
                }
            },
            successMessage = "Вы успешно записались",
            errorMessage = "Не получилось записать"
        )
    }

    fun unSignForLesson(lesson: SportLesson) {
        performLessonAction(
            action = {
                myItmo.api.signOutLessons(listOf(lesson.lessonId)).execute()
                CoroutineScope(Dispatchers.IO).launch {
                    awaitAll(
                        async { sportBookingRepository.refreshSportBookings() },
                        async {
                            scheduleRepository.refreshSchedule(
                                null,
                                lesson.start.toLocalDate(),
                                lesson.end.toLocalDate()
                            )
                        }
                    )
                }
            },
            successMessage = "Запись отменена",
            errorMessage = "Не получилось отписать"
        )
    }


    var autoSignRefreshJob: Job? = null

    fun handleAutoSignClick(lesson: SportLesson) {
        viewModelScope.launch {
            if (!settings.getCustomServicesEnabled()) {
                _events.send(
                    SportSignEvent.ShowInfoDialog(
                        message = "У вас выключены неофициальные сервисы 😝\n\nИх можно включить в настройках"
                    )
                )
                return@launch
            }

            val entry = lesson.signEntry
            when (entry) {
                is SportFreeSignEntry -> {
                    _events.send(
                        SportSignEvent.ShowAutoSignDeleteDialog(
                            message = "У вас уже есть автозапись на это занятие. Позиция в очереди: ${entry.position} из ${entry.total}",
                            action = { deleteFreeSignEntry(entry.id) }
                        )
                    )
                }

                is SportAutoSignEntry -> {
                    _events.send(
                        SportSignEvent.ShowAutoSignDeleteDialog(
                            message = "У вас уже есть автозапись на это занятие. Позиция в очереди: ${entry.position} из ${entry.total}",
                            action = { deleteAutoSignEntry(entry.id) }
                        )
                    )
                }

                null -> {
                    if (lesson.isLessonReal) {
                        _events.send(
                            SportSignEvent.ShowAutoSignConfirmDialog(
                                title = "Автозапись",
                                message = "Вы можете встать в очередь на автозапись.\n\nПриложение попробует вас записать, когда место освободится.",
                                showForceSignButton = true,
                                action = { forceSign ->
                                    createFreeSignEntry(
                                        lesson.lessonId,
                                        forceSign
                                    )
                                }
                            )
                        )
                    } else {
                        autoSignRefreshJob?.cancel()
                        autoSignRefreshJob = viewModelScope.launch {
                            combine(
                                sportDataRepository.observeSportAutoSignLimits(),
                                sportDataRepository.observeSportQueueEntries()
                            ) { limitsFlow, entriesFlow ->
                                limitsFlow.dataOrNull() to entriesFlow.dataOrNull()
                            }.collect { (limits, entries) ->
                                if (limits == null || entries == null) {
                                    _events.send(SportSignEvent.ShowError("Не удалось получить данные для автозаписи"))
                                } else {
                                    val thisDayEntry = entries.find {
                                        it is SportAutoSignEntry && it.targetLesson.start.toLocalDate()
                                            .isEqual(lesson.start.toLocalDate())
                                    }

                                    if (thisDayEntry != null) {
                                        val lessonData = thisDayEntry.targetLesson
                                        val msg = "У вас уже есть автозапись на этот день: \n${
                                            SectionName(lessonData.sectionName).shorten()
                                        }\n${lessonData.teacherFio}"
                                        _events.send(SportSignEvent.ShowInfoDialog(message = msg))
                                    } else {
                                        val available = limits.available
                                        if (available > 0) {
                                            _events.send(
                                                SportSignEvent.ShowAutoSignConfirmDialog(
                                                    title = "Автозапись",
                                                    message = "Вы можете встать в очередь на автозапись.\n\nПриложение попробует вас записать, когда на это занятие откроется запись.",
                                                    showForceSignButton = false,
                                                    action = { createAutoSignEntry(lesson.lessonId) }
                                                )
                                            )
                                        } else {
                                            val nextDate = limits.nextAvailableAt.format(
                                                DateTimeFormatter.ofLocalizedDateTime(
                                                    FormatStyle.MEDIUM
                                                )
                                            )
                                            _events.send(
                                                SportSignEvent.ShowInfoDialog(
                                                    message = "Вы достигли месячного лимита автозаписи.\n\nВ следующий раз можно будет записаться $nextDate"
                                                )
                                            )
                                        }
                                    }
                                }

                                autoSignRefreshJob?.cancel()
                            }
                        }

                        sportDataRepository.refreshSportAutoSignLimits()
                        sportDataRepository.refreshSportQueueEntries()
                    }
                }
            }
        }
    }

    private fun createFreeSignEntry(lessonId: Long, forceSign: Boolean) {
        launchAutoSignAction {
            widgets.api.createSportFreeSignEntry(
                SportFreeSignRequest(
                    lessonId,
                    forceSign
                )
            )
        }
    }

    private fun deleteFreeSignEntry(entryId: Long) {
        launchAutoSignAction {
            widgets.api.cancelSportFreeSignEntry(entryId)
        }
    }

    private fun createAutoSignEntry(prototypeId: Long) {
        launchAutoSignAction {
            widgets.api.createSportAutoSignEntry(
                SportAutoSignRequest(prototypeLessonId = prototypeId)
            )
        }
    }

    private fun deleteAutoSignEntry(entryId: Long) {
        launchAutoSignAction {
            widgets.api.cancelSportAutoSignEntry(entryId)
        }
    }

    private fun performLessonAction(
        action: suspend () -> Unit,
        successMessage: String,
        errorMessage: String
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                isRefreshing.value = true
                action()
                _events.send(SportSignEvent.ShowToast(successMessage))
                refreshMyItmoData()
            } catch (e: Exception) {
                e.printStackTrace()
                _events.send(SportSignEvent.ShowError(errorMessage))
            } finally {
                isRefreshing.value = false
            }
        }
    }

    private fun launchAutoSignAction(apiCall: suspend () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                apiCall()
                refreshCustomData()
            } catch (e: Exception) {
                e.printStackTrace()
                _events.send(SportSignEvent.ShowError("Ошибка: ${e.message}"))
            }
        }
    }

    companion object {
        const val MAX_WEEKS_FORWARD = 5
        const val ANY_BUILDING_KEY = "Любой корпус"
        const val ANY_TEACHER_KEY = "Любой преподаватель"
        const val ANY_TIME_KEY = "Любое время"
    }
}
