package dev.alllexey.itmowidgets.ui.sport.sign

import android.content.Context
import androidx.datastore.dataStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import api.myitmo.MyItmoApi
import api.myitmo.model.sport.SportLesson
import dev.alllexey.itmowidgets.ItmoWidgetsApp
import dev.alllexey.itmowidgets.core.model.SportAutoSignEntry
import dev.alllexey.itmowidgets.core.model.SportAutoSignLimits
import dev.alllexey.itmowidgets.core.model.SportAutoSignQueue
import dev.alllexey.itmowidgets.core.model.SportFreeSignEntry
import dev.alllexey.itmowidgets.core.model.SportFreeSignQueue
import dev.alllexey.itmowidgets.util.SportUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

fun Long.isFreeSection(): Boolean {
    return this == 1L
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

data class SportSignUiState(
    val isLoading: Boolean = true,
    val errorMessage: String? = null,

    // region filters

    val availableSports: List<SelectableSport> = emptyList(),
    val availableBuildings: List<String> = emptyList(),
    val availableTeachers: List<String> = emptyList(),
    val availableTimeSlots: List<String> = emptyList(),

    val selectedSportNames: Set<String> = emptySet(),
    val selectedBuildingName: String? = null,
    val selectedTeacherName: String? = null,
    val selectedTimeSlot: String? = null,
    val isFreeAttendance: Boolean = true, // true by default
    val showOnlyAvailable: Boolean = true, // true by default

    val displayedWeek: List<CalendarDay> = emptyList(),
    val currentMonthName: String = "",
    val canGoToPrevWeek: Boolean = false,
    val canGoToNextWeek: Boolean = true,

    // endregion filters

    val allLessons: List<SportLesson> = emptyList(),
    val filteredLessons: List<SportLesson> = emptyList(),
    val displayedLessons: List<SportLessonData> = emptyList(),
)

data class SelectableSport(
    val name: String,
    val isFree: Boolean,
    val id: Long
)

class SportSignViewModel(private val myItmo: MyItmoApi, context: Context) : ViewModel() {

    private val _uiState = MutableStateFlow(SportSignUiState())
    val uiState: StateFlow<SportSignUiState> = _uiState.asStateFlow()

    private val appContainer = (context.applicationContext as ItmoWidgetsApp).appContainer

    var allSportsMap = mapOf<Long, SelectableSport>()
    var allBuildingsMap = mapOf<Long, String>()
    var allTeachersMap = mapOf<Long, String>()
    var allTimeSlotsMap = mapOf<Long, String>()
    var allScheduleLessons = listOf<SportLesson>()

    var autoSignLimits: SportAutoSignLimits? = null
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
        loadInitialData()
    }

    fun loadInitialData() {
        _uiState.update { it.copy(isLoading = true) }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val filtersDeferred = async { myItmo.sportFilters.execute().body()!!.result }
                val timeSlotsDeferred = async { myItmo.timeSlots.execute().body()!!.data }
                val scheduleDeferred = async {
                    myItmo.getSportSchedule(
                        LocalDate.now(), LocalDate.now().plusDays(21),
                        null, null, null
                    ).execute().body()!!.result
                }

                if (appContainer.storage.settings.getCustomServicesState()) {
                    val freeSignEntriesDeferred =
                        async { appContainer.itmoWidgets.api().mySportFreeSignEntries() }
                    val freeSignQueuesDeferred =
                        async { appContainer.itmoWidgets.api().currentSportFreeSignQueues() }
                    val autoSignLimitsDeferred =
                        async { appContainer.itmoWidgets.api().sportAutoSignLimits() }
                    val autoSignEntriesDeferred =
                        async { appContainer.itmoWidgets.api().mySportAutoSignEntries() }
                    val autoSignQueuesDeferred =
                        async { appContainer.itmoWidgets.api().currentSportAutoSignQueues() }

                    freeSignEntries = freeSignEntriesDeferred.await().data.orEmpty()
                    freeSignQueues = freeSignQueuesDeferred.await().data.orEmpty()
                    autoSignLimits = autoSignLimitsDeferred.await().data
                    autoSignEntries = autoSignEntriesDeferred.await().data.orEmpty()
                    autoSignQueues = autoSignQueuesDeferred.await().data.orEmpty()
                }

                val filters = filtersDeferred.await()
                val timeSlots = timeSlotsDeferred.await()
                val schedule = scheduleDeferred.await()

                allSportsMap = schedule.flatMap { it.lessons ?: emptyList() }
                    .distinctBy { it.sectionId }
                    .map {
                        SelectableSport(
                            SportUtils.shortenSectionName(it.sectionName)!!,
                            it.sectionLevel.isFreeSection(),
                            it.sectionId
                        )
                    }
                    .associateBy { it.id }
                allBuildingsMap = filters.buildingId.associate { it.id to it.value }
                allTeachersMap = filters.teacherIsu.associate { it.id to it.value }
                allTimeSlotsMap = timeSlots.associate { it.id to "${it.timeStart}-${it.timeEnd}" }
                allScheduleLessons = schedule.flatMap { it.lessons ?: emptyList() }

                _uiState.update { it.copy(allLessons = allScheduleLessons, errorMessage = null) }
                updateFiltersAndLessons()

            } catch (e: Exception) {
                e.printStackTrace()
                _uiState.update { it.copy(errorMessage = "Ошибка загрузки данных") }
            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }
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

    private fun updateCalendar() {
        val startOfWeek = today.plusWeeks(weekOffset.toLong()).with(DayOfWeek.MONDAY)
        val days = (0..6).map { startOfWeek.plusDays(it.toLong()) }

        val datesWithLessons = _uiState.value.filteredLessons
            .map { it.date.toLocalDate() }
            .toSet()

        val datesWithAvailableLessons = _uiState.value.filteredLessons
            .filter { it.canSignIn.isCanSignIn }
            .map { it.date.toLocalDate() }
            .toSet()

        val calendarDays = days.map { date ->
            CalendarDay(
                date = date,
                dayOfWeek = date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale("ru")),
                dayOfMonth = date.dayOfMonth.toString(),
                hasLessons = datesWithLessons.contains(date),
                hasAvailableLessons = datesWithAvailableLessons.contains(date),
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

    fun setFreeAttendance(isFree: Boolean) {
        val currentSelectedSports = _uiState.value.selectedSportNames

        val availableSportsInNewMode = allScheduleLessons
            .filter { it.sectionLevel.isFreeSection() == isFree }
            .map { it.sectionName }
            .toSet()

        val commonSports = currentSelectedSports.intersect(availableSportsInNewMode)

        _uiState.update {
            it.copy(
                isFreeAttendance = isFree,
                selectedSportNames = commonSports
            )
        }
        updateFiltersAndLessons()
    }

    fun setShowOnlyAvailable(showOnlyAvailable: Boolean) {
        _uiState.update {
            it.copy(
                showOnlyAvailable = showOnlyAvailable
            )
        }
        updateFiltersAndLessons()
    }

    fun selectSports(sportNames: Set<String>) {
        _uiState.update {
            it.copy(
                selectedSportNames = sportNames
            )
        }
        updateFiltersAndLessons()
    }

    fun selectBuilding(buildingName: String?) {
        val newBuilding = if (buildingName == ANY_BUILDING_KEY) null else buildingName
        _uiState.update {
            it.copy(
                selectedBuildingName = newBuilding
            )
        }
        updateFiltersAndLessons()
    }

    fun selectTeacher(teacherName: String?) {
        val newTeacher = if (teacherName == ANY_TEACHER_KEY) null else teacherName
        _uiState.update { it.copy(selectedTeacherName = newTeacher) }
        updateFiltersAndLessons()
    }

    fun selectTime(time: String?) {
        val newTime = if (time == ANY_TIME_KEY) null else time
        _uiState.update { it.copy(selectedTimeSlot = newTime) }
        updateFiltersAndLessons()
    }


    private fun updateFiltersAndLessons() {
        viewModelScope.launch(Dispatchers.Default) {

            val currentState = _uiState.value

            val lessonsFilteredBySport = allScheduleLessons
                .filter { currentState.isFreeAttendance == it.sectionLevel.isFreeSection() }
                .filter {
                    currentState.selectedSportNames.isEmpty() ||
                            currentState.selectedSportNames.contains(it.sectionName)
                }

            val validBuildingName =
                if (lessonsFilteredBySport.any { allBuildingsMap[it.getRealBuildingId()] == currentState.selectedBuildingName }) {
                    currentState.selectedBuildingName
                } else {
                    null
                }

            val availableSports = allScheduleLessons
                .filter { currentState.isFreeAttendance == it.sectionLevel.isFreeSection() }
                .distinctBy { it.sectionName }
                .map {
                    SelectableSport(
                        it.sectionName,
                        it.sectionLevel.isFreeSection(),
                        it.sectionId
                    )
                }
                .sortedBy { it.name }

            val availableBuildings = lessonsFilteredBySport
                .mapNotNull { allBuildingsMap[it.getRealBuildingId()] }
                .distinct()
                .sorted()

            val lessonsForTeacherCalculation = if (validBuildingName != null) {
                val buildingId = allBuildingsMap.entries.find { it.value == validBuildingName }?.key
                lessonsFilteredBySport.filter { it.getRealBuildingId() == buildingId }
            } else {
                lessonsFilteredBySport
            }
            val availableTeachers = lessonsForTeacherCalculation
                .mapNotNull { allTeachersMap[it.teacherIsu] }
                .distinct()
                .sorted()

            val validTeacherName =
                if (availableTeachers.contains(currentState.selectedTeacherName)) {
                    currentState.selectedTeacherName
                } else {
                    null
                }

            val finalFilteredLessons = lessonsForTeacherCalculation.filter { lesson ->
                val timeSlotId =
                    allTimeSlotsMap.entries.find { it.value == currentState.selectedTimeSlot }?.key
                if (currentState.selectedTimeSlot != null && lesson.timeSlotId != timeSlotId) return@filter false

                val teacherId = allTeachersMap.entries.find { it.value == validTeacherName }?.key
                validTeacherName == null || lesson.teacherIsu == teacherId
            }

            val twoWeeksAgoLessons = finalFilteredLessons.filter { lesson ->
                val lessonDate = lesson.date.toLocalDate()
                lessonDate.isEqual(selectedDate.minusWeeks(2))
            }

            val todayDisplayedLessons = finalFilteredLessons.filter { lesson ->
                if (!lesson.signed && currentState.showOnlyAvailable && !lesson.canSignIn.isCanSignIn) return@filter false
                val lessonDate = lesson.date.toLocalDate()
                lessonDate.isEqual(selectedDate)
            }.map {
                val reasons = UnavailableReason.getSortedUnavailableReasons(it)
                SportLessonData(
                    apiData = it,
                    isReal = true,
                    unavailableReasons = reasons,
                    canSignIn = it.canSignIn.isCanSignIn,
                    freeSignStatus = freeSignEntries.find { e -> e.lessonId == it.id },
                    freeSignQueue = freeSignQueues.find { e -> e.lessonId == it.id },
                    autoSignStatus = null,
                    autoSignQueue = null,
                )
            }

            val allowedUnavailableReasons = listOf(UnavailableReason.SelectionFailed::javaClass,
                UnavailableReason.HealthGroupMismatch::javaClass,
                UnavailableReason.Other::javaClass)
            val todayLessonsByStartDate = todayDisplayedLessons.groupBy { it.apiData.date }
            val fakeLessons = twoWeeksAgoLessons.filter { lesson ->
                !(todayLessonsByStartDate[lesson.date.plusWeeks(2)]?.any {
                    it.apiData.sectionId == lesson.sectionId && it.apiData.teacherIsu == lesson.teacherIsu
                } ?: false)
            }.map {
                val reasons = UnavailableReason.getSortedUnavailableReasons(it)
                    .filter { r -> allowedUnavailableReasons.contains(r::javaClass) }
                SportLessonData(
                    apiData = it,
                    isReal = false,
                    unavailableReasons = reasons,
                    canSignIn = reasons.isEmpty(),
                    freeSignStatus = null,
                    freeSignQueue = null,
                    autoSignStatus = autoSignEntries.find { e -> e.prototypeLessonId == it.id },
                    autoSignQueue = autoSignQueues.find { e -> e.prototypeLessonId == it.id },
                )
            }.filter { !currentState.showOnlyAvailable || it.canSignIn }

            val displayedLessons = (todayDisplayedLessons + fakeLessons)
                .sortedWith(compareBy<SportLessonData> { it.apiData.signed && it.isReal }.thenBy { it.apiData.date }
                    .thenBy { it.apiData.sectionName })

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

    fun signUpForLesson(lesson: SportLessonData) {
        viewModelScope.launch(Dispatchers.IO) {
            try {

                myItmo.signInLessons(listOf(lesson.apiData.id)).execute()

                _uiState.update { it.copy(isLoading = true) }
                val schedule = myItmo.getSportSchedule(
                    LocalDate.now(), LocalDate.now().plusDays(21),
                    null, null, null
                ).execute().body()!!.result

                allScheduleLessons = schedule.flatMap { it.lessons ?: emptyList() }

                _uiState.update { it.copy(allLessons = allScheduleLessons) }
                updateFiltersAndLessons()

            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun unSignForLesson(lesson: SportLessonData) {
        viewModelScope.launch(Dispatchers.IO) {
            try {

                myItmo.signOutLessons(listOf(lesson.apiData.id)).execute()

                _uiState.update { it.copy(isLoading = true) }
                val schedule = myItmo.getSportSchedule(
                    LocalDate.now(), LocalDate.now().plusDays(21),
                    null, null, null
                ).execute().body()!!.result

                allScheduleLessons = schedule.flatMap { it.lessons ?: emptyList() }

                _uiState.update { it.copy(allLessons = allScheduleLessons) }
                updateFiltersAndLessons()

            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun SportLesson.getRealBuildingId(): Long {
        return if (roomId == -1L) -1L
        else if (allBuildingsMap.contains(buildingId)) buildingId else 0L
    }
}