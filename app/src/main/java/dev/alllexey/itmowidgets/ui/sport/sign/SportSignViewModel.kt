package dev.alllexey.itmowidgets.ui.sport.sign

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import api.myitmo.MyItmoApi
import api.myitmo.model.sport.SportLesson
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

data class SportSignUiState(
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val allLessons: List<SportLesson> = emptyList(),
    val filteredLessons: List<SportLesson> = emptyList(),

    val selectedSportNames: Set<String> = emptySet(),
    val selectedBuildingName: String? = null,
    val selectedTeacherName: String? = null,
    val selectedTimeSlot: String? = null,
    val isFreeAttendance: Boolean = true, // true by default

    val availableSports: List<SelectableSport> = emptyList(),
    val availableBuildings: List<String> = emptyList(),
    val availableTeachers: List<String> = emptyList(),
    val availableTimeSlots: List<String> = emptyList()
)

data class SelectableSport(
    val name: String,
    val isFree: Boolean,
    val id: Long
)

class SportSignViewModel(private val myItmo: MyItmoApi) : ViewModel() {

    private val _uiState = MutableStateFlow(SportSignUiState())
    val uiState: StateFlow<SportSignUiState> = _uiState.asStateFlow()

    private var allSportsMap = mapOf<Long, SelectableSport>()
    private var allBuildingsMap = mapOf<Long, String>()
    private var allTeachersMap = mapOf<Long, String>()
    private var allTimeSlotsMap = mapOf<Long, String>()
    private var allScheduleLessons = listOf<SportLesson>()

    companion object {
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
                        LocalDate.now(), LocalDate.now().plusDays(14),
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
                            it.sectionName,
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
            .map { SelectableSport(it.sectionName, it.sectionLevel.isFreeSection(), it.sectionId) }
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

        val validTeacherName = if (availableTeachers.contains(currentState.selectedTeacherName)) {
            currentState.selectedTeacherName
        } else {
            null
        }

        val finalFilteredLessons = lessonsForTeacherCalculation.filter { lesson ->
            val teacherId = allTeachersMap.entries.find { it.value == validTeacherName }?.key
            val timeSlotId =
                allTimeSlotsMap.entries.find { it.value == currentState.selectedTimeSlot }?.key

            (validTeacherName == null || lesson.teacherIsu == teacherId) &&
                    (currentState.selectedTimeSlot == null || lesson.timeSlotId == timeSlotId)
        }

        _uiState.update {
            it.copy(
                filteredLessons = finalFilteredLessons,
                availableSports = availableSports,
                availableBuildings = listOf(ANY_BUILDING_KEY) + availableBuildings,
                availableTeachers = listOf(ANY_TEACHER_KEY) + availableTeachers,
                availableTimeSlots = listOf(ANY_TIME_KEY) + allTimeSlotsMap.values.sorted(),
                selectedBuildingName = validBuildingName,
                selectedTeacherName = validTeacherName
            )
        }
    }

    fun SportLesson.getRealBuildingId(): Long {
        return if (roomId == -1L) -1L
        else if (allBuildingsMap.contains(buildingId)) buildingId else 0L
    }
}