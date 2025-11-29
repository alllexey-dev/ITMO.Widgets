package dev.alllexey.itmowidgets.ui.sport.sign

import api.myitmo.model.sport.SportLesson
import dev.alllexey.itmowidgets.core.model.SportAutoSignEntry
import dev.alllexey.itmowidgets.core.model.SportAutoSignQueue
import dev.alllexey.itmowidgets.core.model.SportFreeSignEntry
import dev.alllexey.itmowidgets.core.model.SportFreeSignQueue
import java.time.LocalDate

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
    val showAutoSign: Boolean = true, // true by default

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

data class SportLessonData(
    val apiData: SportLesson,
    val isReal: Boolean, // false means its predicted
    val unavailableReasons: List<UnavailableReason>,
    val canSignIn: Boolean,
    val freeSignStatus: SportFreeSignEntry?,
    val freeSignQueue: SportFreeSignQueue?,
    val autoSignStatus: SportAutoSignEntry?,
    val autoSignQueue: SportAutoSignQueue?,
)

sealed interface SportSignEvent {
    data class ShowToast(val message: String) : SportSignEvent
    data class ShowError(val message: String) : SportSignEvent

    data class ShowAutoSignConfirmDialog(val title: String, val message: String, val action: () -> Unit) : SportSignEvent
    data class ShowAutoSignDeleteDialog(val message: String, val action: () -> Unit) : SportSignEvent
    data class ShowInfoDialog(val title: String? = null, val message: String) : SportSignEvent
}