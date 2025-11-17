package dev.alllexey.itmowidgets.ui.sport.sign

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import api.myitmo.MyItmoApi
import api.myitmo.model.sport.SportLesson
import dev.alllexey.itmowidgets.util.SportUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

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

    val allLessons: List<SportLesson> = emptyList(),
    val filteredLessons: List<SportLesson> = emptyList(),
    val displayedLessons: List<SportLesson> = emptyList(),

    val selectedSportNames: Set<String> = emptySet(),
    val selectedBuildingName: String? = null,
    val selectedTeacherName: String? = null,
    val selectedTimeSlot: String? = null,
    val isFreeAttendance: Boolean = true, // true by default
    val showOnlyAvailable: Boolean = true, // true by default

    val availableSports: List<SelectableSport> = emptyList(),
    val availableBuildings: List<String> = emptyList(),
    val availableTeachers: List<String> = emptyList(),
    val availableTimeSlots: List<String> = emptyList(),

    val displayedWeek: List<CalendarDay> = emptyList(),
    val currentMonthName: String = "",
    val canGoToPrevWeek: Boolean = false,
    val canGoToNextWeek: Boolean = true
)

data class SelectableSport(
    val name: String,
    val isFree: Boolean,
    val id: Long
)

class SportSignViewModel(private val myItmo: MyItmoApi) : ViewModel() {

    private val _uiState = MutableStateFlow(SportSignUiState())
    val uiState: StateFlow<SportSignUiState> = _uiState.asStateFlow()

    var allSportsMap = mapOf<Long, SelectableSport>()
    var allBuildingsMap = mapOf<Long, String>()
    var allTeachersMap = mapOf<Long, String>()
    var allTimeSlotsMap = mapOf<Long, String>()
    var allScheduleLessons = listOf<SportLesson>()

    private val today = LocalDate.now()
    private var selectedDate = today
    private var weekOffset = 0

    companion object {
        const val MAX_WEEKS_FORWARD = 3
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

                _uiState.update { it.copy(allLessons = allScheduleLessons) }
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
            selectedDate = today.plusWeeks(weekOffset.toLong()).with(java.time.DayOfWeek.MONDAY)
            updateCalendar()
            updateFiltersAndLessons()
        }
    }

    fun prevWeek() {
        if (weekOffset > 0) {
            weekOffset--
            selectedDate = today.plusWeeks(weekOffset.toLong()).with(java.time.DayOfWeek.MONDAY)
            updateCalendar()
            updateFiltersAndLessons()
        }
    }

    private fun updateCalendar() {
        val startOfWeek = today.plusWeeks(weekOffset.toLong()).with(java.time.DayOfWeek.MONDAY)
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
                dayOfWeek = date.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale("ru")),
                dayOfMonth = date.dayOfMonth.toString(),
                hasLessons = datesWithLessons.contains(date),
                hasAvailableLessons = datesWithAvailableLessons.contains(date),
                isSelected = date.isEqual(selectedDate),
                isToday = date.isEqual(today)
            )
        }

        val monthName = days[3].month.getDisplayName(java.time.format.TextStyle.FULL_STANDALONE, java.util.Locale("ru"))
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase(java.util.Locale("ru")) else it.toString() }

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
            }.sortedBy { it.date }

            val displayedLessons = finalFilteredLessons.filter { lesson ->
                if (!lesson.signed && currentState.showOnlyAvailable && !lesson.canSignIn.isCanSignIn) return@filter false
                val lessonDate = lesson.date.toLocalDate()
                lessonDate.isEqual(selectedDate)
            }

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

    fun signUpForLesson(lesson: SportLesson) {
        viewModelScope.launch(Dispatchers.IO) {
            try {

                myItmo.signInLessons(listOf(lesson.id)).execute()

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

    fun unSignForLesson(lesson: SportLesson) {
        viewModelScope.launch(Dispatchers.IO) {
            try {

                myItmo.signOutLessons(listOf(lesson.id)).execute()

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