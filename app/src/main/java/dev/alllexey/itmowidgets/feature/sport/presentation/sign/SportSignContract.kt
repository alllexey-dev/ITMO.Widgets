package dev.alllexey.itmowidgets.feature.sport.presentation.sign

import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.text.UiText
import dev.alllexey.itmowidgets.feature.sport.domain.model.SectionName
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportLesson
import java.time.LocalDate

sealed interface SportSignUiState {

    data object Loading : SportSignUiState

    data class Content(
        val availableSports: List<SectionName> = emptyList(),
        val usedSportNames: Set<SectionName> = emptySet(),
        val availableBuildings: List<String> = emptyList(),
        val availableTeachers: List<String> = emptyList(),
        val availableTimeSlots: List<String> = emptyList(),
        val selectedSportNames: Set<SectionName> = emptySet(),
        val selectedBuildingName: String? = null,
        val selectedTeacherName: String? = null,
        val selectedTimeSlot: String? = null,
        val showOnlyAvailable: Boolean = true,
        val showAutoSign: Boolean = true,
        val showOnlyFriends: Boolean = false,
        val displayedWeek: List<CalendarDay> = emptyList(),
        val calendarWeeks: List<List<CalendarDay>> = emptyList(),
        val selectedWeekIndex: Int = 0,
        val currentMonthName: String = "",
        val canGoToPrevWeek: Boolean = false,
        val canGoToNextWeek: Boolean = true,
        val hasActiveFilters: Boolean = false,
        val displayedLessons: List<SportLesson> = emptyList(),
        val hasPartialError: Boolean = false,
        val hideTeacherSelector: Boolean = true,
        val hideTimeSelector: Boolean = true,
        /** Lessons with a booking request in flight; their action is blocked. */
        val busyLessonIds: Set<Long> = emptySet()
    ) : SportSignUiState

    data class Error(val error: AppError) : SportSignUiState
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

sealed interface SportSignCommand {
    data class CreateFreeSign(val lessonId: Long) : SportSignCommand
    data class CancelFreeSign(val entryId: Long) : SportSignCommand
    data class CreateAutoSign(val prototypeLessonId: Long) : SportSignCommand
    data class CancelAutoSign(val entryId: Long) : SportSignCommand
}

sealed interface SportSignEvent {
    data class ShowToast(val message: UiText) : SportSignEvent
    data class ShowError(val error: AppError) : SportSignEvent
    data class ShowAutoSignConfirmDialog(
        val title: UiText,
        val message: UiText,
        val showForceSignButton: Boolean,
        val command: SportSignCommand
    ) : SportSignEvent

    data class ShowAutoSignDeleteDialog(
        val message: UiText,
        val command: SportSignCommand
    ) : SportSignEvent

    data class ShowInfoDialog(
        val title: UiText? = null,
        val message: UiText
    ) : SportSignEvent
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

internal const val MAX_WEEKS_FORWARD = 5
