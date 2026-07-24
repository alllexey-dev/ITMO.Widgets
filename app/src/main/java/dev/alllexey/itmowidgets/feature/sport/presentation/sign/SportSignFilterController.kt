package dev.alllexey.itmowidgets.feature.sport.presentation.sign

import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import dev.alllexey.itmowidgets.feature.sport.domain.model.SectionName
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import javax.inject.Inject

class SportSignFilterController @Inject constructor(
    private val timeProvider: AcademicTimeProvider
) {

    private val mutableFilters = MutableStateFlow(
        SportSignFilters(selectedDate = timeProvider.today())
    )
    val filters: StateFlow<SportSignFilters> = mutableFilters.asStateFlow()

    fun selectDate(date: LocalDate) {
        update { copy(selectedDate = date) }
    }

    fun reset() {
        mutableFilters.value = SportSignFilters(
            selectedDate = mutableFilters.value.selectedDate
        )
    }

    fun nextWeek() {
        moveWeek(offset = 1)
    }

    fun previousWeek() {
        moveWeek(offset = -1)
    }

    fun showOnlyAvailable(show: Boolean) {
        update { copy(showOnlyAvailable = show) }
    }

    fun showOnlyFriends(show: Boolean) {
        update { copy(showOnlyFriends = show) }
    }

    fun showAutoSign(show: Boolean) {
        update { copy(showAutoSign = show) }
    }

    fun selectSports(names: Set<SectionName>) {
        update { copy(selectedSportNames = names) }
    }

    fun selectBuilding(name: String?) {
        update { copy(selectedBuildingName = name) }
    }

    fun selectTeacher(name: String?) {
        update { copy(selectedTeacherName = name) }
    }

    fun selectTime(time: String?) {
        update { copy(selectedTimeSlot = time) }
    }

    private fun moveWeek(offset: Int) {
        val today = timeProvider.today()
        val currentMonday = today.with(DayOfWeek.MONDAY)
        val selectedMonday = mutableFilters.value.selectedDate.with(DayOfWeek.MONDAY)
        val currentOffset = ChronoUnit.WEEKS
            .between(currentMonday, selectedMonday)
            .toInt()
        val targetOffset = (currentOffset + offset).coerceIn(0, MAX_WEEKS_FORWARD)

        if (targetOffset != currentOffset) {
            update {
                copy(selectedDate = currentMonday.plusWeeks(targetOffset.toLong()))
            }
        }
    }

    private inline fun update(transform: SportSignFilters.() -> SportSignFilters) {
        mutableFilters.value = mutableFilters.value.transform()
    }
}
