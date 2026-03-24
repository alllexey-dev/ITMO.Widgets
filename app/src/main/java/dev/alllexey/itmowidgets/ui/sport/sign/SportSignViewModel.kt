package dev.alllexey.itmowidgets.ui.sport.sign

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import api.myitmo.MyItmoApi
import api.myitmo.model.sport.SportLesson
import dev.alllexey.itmowidgets.appContainer
import dev.alllexey.itmowidgets.core.model.QueueEntryStatus
import dev.alllexey.itmowidgets.core.model.SportAutoSignEntry
import dev.alllexey.itmowidgets.core.model.SportAutoSignLimits
import dev.alllexey.itmowidgets.core.model.SportAutoSignQueue
import dev.alllexey.itmowidgets.core.model.SportAutoSignRequest
import dev.alllexey.itmowidgets.core.model.SportFreeSignEntry
import dev.alllexey.itmowidgets.core.model.SportFreeSignQueue
import dev.alllexey.itmowidgets.core.model.SportFreeSignRequest
import dev.alllexey.itmowidgets.data.repository.SportSharedData
import dev.alllexey.itmowidgets.data.repository.SportSharedRepository
import dev.alllexey.itmowidgets.util.SportUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.format.TextStyle
import java.util.Locale

fun Long.isFreeSection(): Boolean = this == 1L

class SportSignViewModel(
    private val myItmo: MyItmoApi,
    private val sharedRepository: SportSharedRepository,
    context: Context
) : ViewModel() {

    private val appContainer = context.appContainer()

    private val _uiState = MutableStateFlow(SportSignUiState())
    val uiState: StateFlow<SportSignUiState> = _uiState.asStateFlow()

    private val _events = Channel<SportSignEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    var allSportsMap = mapOf<Long, SelectableSport>()
    var allBuildingsMap = mapOf<Long, String>()
    var allTeachersMap = mapOf<Long, String>()
    var allTimeSlotsMap = mapOf<Long, String>()
    var allScheduleLessons = listOf<SportLesson>()

    var autoSignLimits: SportAutoSignLimits? = null
    var usedSportNames: Set<String> = emptySet()
    var freeSignEntries: List<SportFreeSignEntry> = emptyList()
    var freeSignQueues: List<SportFreeSignQueue> = emptyList()
    var autoSignEntries: List<SportAutoSignEntry> = emptyList()
    var autoSignQueues: List<SportAutoSignQueue> = emptyList()

    private val today = LocalDate.now()
    private var selectedDate = today
    private var weekOffset = 0

    companion object {
        const val MAX_WEEKS_FORWARD = 5
        const val ANY_BUILDING_KEY = "Любой корпус"
        const val ANY_TEACHER_KEY = "Любой преподаватель"
        const val ANY_TIME_KEY = "Любое время"
    }

    init {
        observeSharedData()
        loadAllData()
    }

    private fun observeSharedData() {
        viewModelScope.launch {
            sharedRepository.state.collectLatest { shared ->
                applySharedData(shared)

                if (!shared.isLoading && allScheduleLessons.isNotEmpty()) {
                    allScheduleLessons = shared.scheduleLessons
                    rebuildUsedSportNames()
                    updateFiltersAndLessons()
                }
            }
        }
    }

    fun loadOnlyCustomData() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                supervisorScope {
                    val sharedDeferred = async { sharedRepository.reloadCustom() }
                    sharedDeferred.await()
                    applySharedData(sharedRepository.state.value)

                    _uiState.update { it.copy() }
                    updateFiltersAndLessons()
                }
            } catch (e: Exception) {
                appContainer.errorLogRepository.logThrowable(e, SportSignViewModel::class.java.name)
                _events.send(SportSignEvent.ShowError("Ошибка загрузки данных"))
            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun loadAllData() {
        _uiState.update { it.copy(isLoading = true) }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                supervisorScope {
                    val filtersDeferred = async { myItmo.sportFilters.execute().body()!!.result }
                    val timeSlotsDeferred = async { myItmo.timeSlots.execute().body()!!.data }
                    val sharedDeferred = async { sharedRepository.reloadAll() }

                    val filters = filtersDeferred.await()
                    val timeSlots = timeSlotsDeferred.await()

                    sharedDeferred.await()
                    applySharedData(sharedRepository.state.value)

                    allSportsMap = allScheduleLessons
                        .distinctBy { it.sectionId }
                        .associate {
                            it.sectionId to SelectableSport(
                                SportUtils.shortenSectionName(it.sectionName) ?: "",
                                it.sectionLevel.isFreeSection(),
                                it.sectionId
                            )
                        }

                    allBuildingsMap = filters.buildingId.associate { it.id to it.value }
                    allTeachersMap = filters.teacherIsu.associate { it.id to it.value }
                    allTimeSlotsMap = timeSlots.associate { it.id to "${it.timeStart}-${it.timeEnd}" }

                    rebuildUsedSportNames()

                    _uiState.update { it.copy(allLessons = allScheduleLessons) }
                    updateFiltersAndLessons()
                }
            } catch (e: Exception) {
                appContainer.errorLogRepository.logThrowable(e, SportSignViewModel::class.java.name)
                _events.send(SportSignEvent.ShowError("Ошибка загрузки данных"))
            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    private fun applySharedData(shared: SportSharedData) {
        allScheduleLessons = shared.scheduleLessons

        freeSignEntries = shared.freeSignEntries
            .filter { it.status in QueueEntryStatus.notifiableStatuses }

        freeSignQueues = shared.freeSignQueues

        autoSignLimits = shared.autoSignLimits

        autoSignEntries = shared.autoSignEntries
            .filter { it.status in QueueEntryStatus.notifiableStatuses }

        autoSignQueues = shared.autoSignQueues
    }

    private fun rebuildUsedSportNames() {
        usedSportNames = allScheduleLessons
            .filter { it.signed }
            .mapNotNull { it.sectionName }
            .plus(freeSignEntries.map { it.targetLesson.sectionName })
            .plus(autoSignEntries.map { it.targetLesson.sectionName })
            .toSet()
    }

    private fun updateFiltersAndLessons() {
        viewModelScope.launch(Dispatchers.Default) {
            val state = _uiState.value

            val lessonsBySport = allScheduleLessons.filter { lesson ->
                val matchesType = lesson.sectionLevel.isFreeSection() == state.isFreeAttendance
                val matchesName = state.selectedSportNames.isEmpty() || lesson.sectionName in state.selectedSportNames
                matchesType && matchesName
            }

            val validBuildingName = state.selectedBuildingName?.takeIf { name ->
                lessonsBySport.any { allBuildingsMap[it.getRealBuildingId()] == name }
            }

            val buildingId = allBuildingsMap.entries.find { it.value == validBuildingName }?.key
            val lessonsByBuilding = if (validBuildingName != null) {
                lessonsBySport.filter { it.getRealBuildingId() == buildingId }
            } else {
                lessonsBySport
            }

            val availableTeacherNames = lessonsByBuilding
                .mapNotNull { allTeachersMap[it.teacherIsu] }
                .distinct()

            val validTeacherName = state.selectedTeacherName?.takeIf { it in availableTeacherNames }
            val teacherId = allTeachersMap.entries.find { it.value == validTeacherName }?.key

            val timeSlotId = allTimeSlotsMap.entries.find { it.value == state.selectedTimeSlot }?.key

            val finalFilteredLessons = lessonsByBuilding.filter { lesson ->
                val matchesTime = state.selectedTimeSlot == null || lesson.timeSlotId == timeSlotId
                val matchesTeacher = validTeacherName == null || lesson.teacherIsu == teacherId
                matchesTime && matchesTeacher
            }

            val availableSports = allScheduleLessons
                .filter { it.sectionLevel.isFreeSection() == state.isFreeAttendance }
                .distinctBy { it.sectionName }
                .map {
                    SelectableSport(
                        it.sectionName,
                        it.sectionLevel.isFreeSection(),
                        it.sectionId
                    )
                }
                .sortedBy { it.name }

            val availableBuildings = lessonsBySport
                .mapNotNull { allBuildingsMap[it.getRealBuildingId()] }
                .distinct()
                .sorted()

            val availableTeachers = availableTeacherNames.sorted()

            val todayRealLessons = finalFilteredLessons
                .filter { lesson ->
                    val isToday = lesson.date.toLocalDate().isEqual(selectedDate)
                    val isAvailable = lesson.available > 0 && lesson.canSignIn.isCanSignIn
                    val shouldShow =
                        !state.showOnlyAvailable || (lesson.dateEnd > OffsetDateTime.now() && (
                                lesson.signed ||
                                        isAvailable ||
                                        (state.showAutoSign && lesson.canSignIn.isCanSignIn)
                                ))

                    isToday && shouldShow
                }
                .map { createSportLessonData(it, isReal = true) }
                .filter { item ->
                    !state.showOnlyAvailable || (
                            item.apiData.dateEnd > OffsetDateTime.now() && (
                                    item.canSignIn ||
                                            item.apiData.signed ||
                                            (state.showAutoSign && item.apiData.available <= 0)
                                    )
                            )
                }

            val phantomLessons = if (state.showAutoSign) {
                val twoWeeksAgoDate = selectedDate.minusWeeks(2)

                val historicalLessons = finalFilteredLessons.filter {
                    it.date.toLocalDate().isEqual(twoWeeksAgoDate)
                }

                val futureDateStart = selectedDate.plusWeeks(2).atStartOfDay()
                val futureDateEnd = selectedDate.plusWeeks(2).plusDays(1).atStartOfDay()

                val futureLessons = allScheduleLessons.filter {
                    val lDate = it.date.toLocalDateTime()
                    lDate >= futureDateStart && lDate < futureDateEnd
                }

                historicalLessons
                    .filter { historyLesson ->
                        val existsInFuture = futureLessons.any { futureLesson ->
                            futureLesson.sectionId == historyLesson.sectionId &&
                                    futureLesson.teacherIsu == historyLesson.teacherIsu
                        }
                        !existsInFuture
                    }
                    .map { createSportLessonData(it, isReal = false) }
                    .filter { item -> !state.showOnlyAvailable || item.canSignIn }
            } else {
                emptyList()
            }

            val displayedLessons = (todayRealLessons + phantomLessons).sortedWith(
                compareBy<SportLessonData> { !(it.apiData.signed || it.autoSignStatus != null || it.freeSignStatus != null) }
                    .thenBy { it.apiData.date }
                    .thenBy { it.apiData.sectionName }
            )

            _uiState.update {
                it.copy(
                    filteredLessons = finalFilteredLessons,
                    displayedLessons = displayedLessons,
                    availableSports = availableSports,
                    availableBuildings = listOf(ANY_BUILDING_KEY) + availableBuildings,
                    availableTeachers = listOf(ANY_TEACHER_KEY) + availableTeachers,
                    availableTimeSlots = listOf(ANY_TIME_KEY) + allTimeSlotsMap.values.sorted(),
                    selectedBuildingName = validBuildingName,
                    selectedTeacherName = validTeacherName
                )
            }

            updateCalendar()
        }
    }

    private fun createSportLessonData(lesson: SportLesson, isReal: Boolean): SportLessonData {
        val reasons = if (isReal) {
            UnavailableReason.getSortedUnavailableReasons(lesson)
        } else {
            val allowed = setOf(
                UnavailableReason.LessonInPast::class,
                UnavailableReason.SelectionFailed::class,
                UnavailableReason.HealthGroupMismatch::class,
                UnavailableReason.Other::class,
                UnavailableReason.ExternatOnly::class
            )
            UnavailableReason.getSortedUnavailableReasons(lesson)
                .filter { it::class in allowed }
        }

        return SportLessonData(
            apiData = lesson,
            isReal = isReal,
            unavailableReasons = reasons,
            canSignIn = if (isReal) lesson.canSignIn.isCanSignIn else reasons.isEmpty(),
            freeSignStatus = if (isReal) freeSignEntries.find { it.lessonId == lesson.id } else null,
            freeSignQueue = if (isReal) freeSignQueues.find { it.lessonId == lesson.id } else null,
            autoSignStatus = if (!isReal) autoSignEntries.find { it.prototypeLessonId == lesson.id } else null,
            autoSignQueue = if (!isReal) autoSignQueues.find { it.prototypeLessonId == lesson.id } else null,
        )
    }

    private fun updateCalendar() {
        val startOfWeek = today.plusWeeks(weekOffset.toLong()).with(DayOfWeek.MONDAY)
        val days = (0..6).map { startOfWeek.plusDays(it.toLong()) }

        val datesWithLessons = _uiState.value.filteredLessons.map { it.date.toLocalDate() }.toSet()
        val datesAvailable = _uiState.value.filteredLessons
            .filter { it.canSignIn.isCanSignIn }
            .map { it.date.toLocalDate() }
            .toSet()

        val calendarDays = days.map { date ->
            CalendarDay(
                date = date,
                dayOfWeek = date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale("ru")),
                dayOfMonth = date.dayOfMonth.toString(),
                hasLessons = datesWithLessons.contains(date),
                hasAvailableLessons = datesAvailable.contains(date),
                isSelected = date.isEqual(selectedDate),
                isToday = date.isEqual(today)
            )
        }

        val monthName = days[3].month.getDisplayName(TextStyle.FULL_STANDALONE, Locale("ru"))
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale("ru")) else it.toString() }

        _uiState.update {
            it.copy(
                displayedWeek = calendarDays,
                currentMonthName = monthName,
                canGoToPrevWeek = weekOffset > 0,
                canGoToNextWeek = weekOffset < MAX_WEEKS_FORWARD
            )
        }
    }

    fun selectDate(date: LocalDate) {
        selectedDate = date
        updateCalendar()
        updateFiltersAndLessons()
    }

    fun nextWeek() {
        if (weekOffset < MAX_WEEKS_FORWARD) {
            weekOffset++
            selectedDate = today.plusWeeks(weekOffset.toLong()).with(DayOfWeek.MONDAY)
            updateCalendar()
            updateFiltersAndLessons()
        }
    }

    fun prevWeek() {
        if (weekOffset > 0) {
            weekOffset--
            selectedDate = today.plusWeeks(weekOffset.toLong()).with(DayOfWeek.MONDAY)
            updateCalendar()
            updateFiltersAndLessons()
        }
    }

    fun setFreeAttendance(isFree: Boolean) {
        val currentSelectedSports = _uiState.value.selectedSportNames
        val availableSportsInNewMode = allScheduleLessons
            .filter { it.sectionLevel.isFreeSection() == isFree }
            .mapNotNull { it.sectionName }
            .toSet()

        _uiState.update {
            it.copy(
                isFreeAttendance = isFree,
                selectedSportNames = currentSelectedSports.intersect(availableSportsInNewMode)
            )
        }
        updateFiltersAndLessons()
    }

    fun setShowOnlyAvailable(show: Boolean) {
        _uiState.update { it.copy(showOnlyAvailable = show) }
        updateFiltersAndLessons()
    }

    fun setShowAutoSign(show: Boolean) {
        _uiState.update { it.copy(showAutoSign = show) }
        updateFiltersAndLessons()
    }

    fun selectSports(names: Set<String>) {
        _uiState.update { it.copy(selectedSportNames = names) }
        updateFiltersAndLessons()
    }

    fun selectBuilding(name: String?) {
        _uiState.update { it.copy(selectedBuildingName = if (name == ANY_BUILDING_KEY) null else name) }
        updateFiltersAndLessons()
    }

    fun selectTeacher(name: String?) {
        _uiState.update { it.copy(selectedTeacherName = if (name == ANY_TEACHER_KEY) null else name) }
        updateFiltersAndLessons()
    }

    fun selectTime(time: String?) {
        _uiState.update { it.copy(selectedTimeSlot = if (time == ANY_TIME_KEY) null else time) }
        updateFiltersAndLessons()
    }

    fun signUpForLesson(lesson: SportLessonData) {
        performLessonAction(
            action = { myItmo.signInLessons(listOf(lesson.apiData.id)).execute() },
            successMessage = "Вы успешно записались"
        )
    }

    fun unSignForLesson(lesson: SportLessonData) {
        performLessonAction(
            action = { myItmo.signOutLessons(listOf(lesson.apiData.id)).execute() },
            successMessage = "Запись отменена"
        )
    }

    private fun performLessonAction(action: () -> Unit, successMessage: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                _uiState.update { it.copy(isLoading = true) }
                action()
                _events.send(SportSignEvent.ShowToast(successMessage))
                loadAllData()
            } catch (e: Exception) {
                e.printStackTrace()
                _events.send(SportSignEvent.ShowError("Ошибка выполнения операции"))
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun handleAutoSignClick(lesson: SportLessonData) {
        viewModelScope.launch {
            if (!appContainer.storage.settings.getCustomServicesState()) {
                _events.send(
                    SportSignEvent.ShowInfoDialog(
                        message = "У вас выключены неофициальные сервисы 😝\n\nИх можно включить в настройках"
                    )
                )
                return@launch
            }

            if (lesson.isReal) {
                val entry = freeSignEntries.find { it.lessonId == lesson.apiData.id }
                if (entry != null) {
                    _events.send(
                        SportSignEvent.ShowAutoSignDeleteDialog(
                            message = "У вас уже есть автозапись на это занятие. Позиция в очереди: ${entry.position} из ${entry.total}",
                            action = { deleteFreeSignEntry(entry.id) }
                        )
                    )
                } else {
                    _events.send(
                        SportSignEvent.ShowAutoSignConfirmDialog(
                            title = "Автозапись",
                            message = "Вы можете встать в очередь на автозапись.\n\nПриложение попробует вас записать, когда место освободится.",
                            showForceSignButton = true,
                            action = { forceSign ->
                                createFreeSignEntry(
                                    lesson.apiData.id,
                                    forceSign
                                )
                            }
                        )
                    )
                }
            } else {
                val entry = autoSignEntries.find { it.prototypeLessonId == lesson.apiData.id }
                val thisDayEntry = autoSignEntries.find {
                    it.targetLesson.dateStart.toLocalDate()
                        .isEqual(lesson.apiData.date.toLocalDate())
                }

                if (entry != null) {
                    _events.send(
                        SportSignEvent.ShowAutoSignDeleteDialog(
                            message = "У вас уже есть автозапись на это занятие. Позиция в очереди: ${entry.position} из ${entry.total}",
                            action = { deleteAutoSignEntry(entry.id) }
                        )
                    )
                } else if (thisDayEntry != null) {
                    val lessonData = thisDayEntry.targetLesson
                    val msg = "У вас уже есть автозапись на этот день: \n${
                        SportUtils.shortenSectionName(lessonData.sectionName)
                    }\n${lessonData.teacherFio}"
                    _events.send(SportSignEvent.ShowInfoDialog(message = msg))
                } else {
                    val available = autoSignLimits?.available ?: 1
                    if (available > 0) {
                        _events.send(
                            SportSignEvent.ShowAutoSignConfirmDialog(
                                title = "Автозапись",
                                message = "Вы можете встать в очередь на автозапись.\n\nПриложение попробует вас записать, когда на это занятие откроется запись.",
                                showForceSignButton = false,
                                action = { createAutoSignEntry(lesson.apiData.id) }
                            )
                        )
                    } else {
                        val nextDate = autoSignLimits?.nextAvailableAt
                        _events.send(
                            SportSignEvent.ShowInfoDialog(
                                message = "Вы достигли месячного лимита автозаписи.\n\nВ следующий раз можно будет записаться $nextDate"
                            )
                        )
                    }
                }
            }
        }
    }

    private fun createFreeSignEntry(lessonId: Long, forceSign: Boolean) {
        launchAutoSignAction {
            appContainer.itmoWidgets.api.createSportFreeSignEntry(
                SportFreeSignRequest(
                    lessonId,
                    forceSign
                )
            )
        }
    }

    private fun deleteFreeSignEntry(entryId: Long) {
        launchAutoSignAction {
            appContainer.itmoWidgets.api.cancelSportFreeSignEntry(entryId)
        }
    }

    private fun createAutoSignEntry(prototypeId: Long) {
        launchAutoSignAction {
            appContainer.itmoWidgets.api.createSportAutoSignEntry(
                SportAutoSignRequest(prototypeLessonId = prototypeId)
            )
        }
    }

    private fun deleteAutoSignEntry(entryId: Long) {
        launchAutoSignAction {
            appContainer.itmoWidgets.api.cancelSportAutoSignEntry(entryId)
        }
    }

    private fun launchAutoSignAction(apiCall: suspend () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                apiCall()
                loadOnlyCustomData()
            } catch (e: Exception) {
                e.printStackTrace()
                _events.send(SportSignEvent.ShowError("Ошибка: ${e.message}"))
            }
        }
    }

    private fun SportLesson.getRealBuildingId(): Long {
        return if (roomId == -1L) -1L
        else if (allBuildingsMap.contains(buildingId)) buildingId else 0L
    }
}