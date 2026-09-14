package dev.alllexey.itmowidgets.feature.sport.presentation.sign

import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportFilterCatalog
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportLesson
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportTimeSlot
import dev.alllexey.itmowidgets.feature.sport.domain.model.UnavailableReason
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale
import javax.inject.Inject

class SportSignStateFactory @Inject constructor(
    private val timeProvider: AcademicTimeProvider
) {

    fun create(
        lessons: List<SportLesson>,
        catalog: SportFilterCatalog,
        timeSlots: List<SportTimeSlot>,
        userFilters: SportSignFilters,
        hasPartialError: Boolean
    ): SportSignUiState.Content = with(userFilters) {
        val today = timeProvider.today()
        val buildingsById = catalog.buildings.associate { it.id to it.value }
        val teachersByIsu = catalog.teachers.associate { it.id to it.value }
        val timeSlotsById = timeSlots.associate { it.id to it.displayName }

        val lessonsBySport = lessons.filter {
            selectedSportNames.isEmpty() || it.sectionName in selectedSportNames
        }

        fun SportLesson.buildingFilterId(): Long {
            return when {
                roomId == -1L -> -1L
                buildingId != null && buildingId > 0 && buildingsById.containsKey(buildingId) -> buildingId
                else -> 0L
            }
        }

        val validBuildingName = selectedBuildingName?.takeIf { name ->
            lessonsBySport.any { buildingsById[it.buildingFilterId()] == name }
        }
        val selectedBuildingId = buildingsById.entries
            .find { it.value == validBuildingName }
            ?.key

        val lessonsByBuilding = selectedBuildingId?.let { buildingId ->
            lessonsBySport.filter { it.buildingFilterId() == buildingId }
        } ?: lessonsBySport

        val validTeacherNames = lessonsByBuilding
            .mapNotNull { teachersByIsu[it.teacherIsu.toLong()] }
            .distinct()
        val validTeacherName = selectedTeacherName?.takeIf(validTeacherNames::contains)
        val selectedTeacherIsu = teachersByIsu.entries
            .find { it.value == validTeacherName }
            ?.key

        val selectedTimeSlotId = timeSlotsById.entries
            .find { it.value == selectedTimeSlot }
            ?.key
        val validTimeSlot = timeSlotsById[selectedTimeSlotId]

        val filteredLessons = lessonsByBuilding.filter { lesson ->
            val matchesTime =
                selectedTimeSlotId == null || lesson.timeSlotId == selectedTimeSlotId
            val matchesTeacher =
                selectedTeacherIsu == null || lesson.teacherIsu.toLong() == selectedTeacherIsu
            matchesTime && matchesTeacher
        }

        val availableSports = lessons
            .map(SportLesson::sectionName)
            .distinct()
            .sortedBy { it.shorten() }
        val availableBuildings = lessonsBySport
            .mapNotNull { buildingsById[it.buildingFilterId()] }
            .distinct()
            .sorted()
        val availableTeachers = validTeacherNames.sorted()

        val now = timeProvider.now()
        val visibleLessons = filteredLessons
            .asSequence()
            .filter { it.end > now }
            .filter { lesson ->
                val isAvailable = lesson.available > 0 && lesson.canSignIn
                val reasons = lesson.unavailableReasons
                val isAutoSignAvailable = showAutoSign &&
                    (reasons.isEmpty() || reasons.lastOrNull() == UnavailableReason.Full)
                val availableCheck =
                    !showOnlyAvailable || isAvailable || isAutoSignAvailable
                val autoSignCheck =
                    showAutoSign || (lesson.isLessonReal && lesson.available > 0)
                lesson.signed || (availableCheck && autoSignCheck)
            }
            .filter { !showOnlyFriends || it.friendsBookings.isNotEmpty() }
            .sortedWith(
                compareBy<SportLesson> { !(it.signed || it.signEntry != null) }
                    .thenBy(SportLesson::start)
                    .thenBy { it.sectionName.shorten() }
            )
            .toList()

        val displayedLessons = visibleLessons.filter {
            it.start.toLocalDate() == selectedDate
        }

        val currentMonday = today.with(DayOfWeek.MONDAY)
        val selectedMonday = selectedDate.with(DayOfWeek.MONDAY)
        val weekOffset = ChronoUnit.WEEKS
            .between(currentMonday, selectedMonday)
            .toInt()
        val datesWithLessons = visibleLessons
            .map { it.start.toLocalDate() }
            .toSet()
        val datesWithAvailableLessons = visibleLessons
            .filter(SportLesson::canSignIn)
            .map { it.start.toLocalDate() }
            .toSet()

        val calendarWeeks = (0..MAX_WEEKS_FORWARD).map { weekIndex ->
            val weekStart = currentMonday.plusWeeks(weekIndex.toLong())
            (0..6).map { dayOffset ->
                val date = weekStart.plusDays(dayOffset.toLong())
                CalendarDay(
                    date = date,
                    dayOfWeek = date.dayOfWeek.getDisplayName(
                        TextStyle.SHORT,
                        Locale.getDefault()
                    ),
                    dayOfMonth = date.dayOfMonth.toString(),
                    hasLessons = date in datesWithLessons,
                    hasAvailableLessons = date in datesWithAvailableLessons,
                    isSelected = date == selectedDate,
                    isToday = date == today
                )
            }
        }
        val selectedWeekIndex = weekOffset.coerceIn(0, MAX_WEEKS_FORWARD)
        val displayedWeek = calendarWeeks[selectedWeekIndex]
        val currentMonthName = displayedWeek[3].date.month
            .getDisplayName(TextStyle.FULL_STANDALONE, Locale.getDefault())
            .replaceFirstChar { character ->
                if (character.isLowerCase()) {
                    character.titlecase(Locale.getDefault())
                } else {
                    character.toString()
                }
            }

        SportSignUiState.Content(
            availableSports = availableSports,
            usedSportNames = lessons
                .filter { it.signed || it.signEntry != null }
                .map(SportLesson::sectionName)
                .toSet(),
            availableBuildings = availableBuildings,
            availableTeachers = availableTeachers,
            availableTimeSlots = timeSlotsById.values.toList(),
            selectedSportNames = selectedSportNames,
            selectedBuildingName = validBuildingName,
            selectedTeacherName = validTeacherName,
            selectedTimeSlot = validTimeSlot,
            showOnlyAvailable = showOnlyAvailable,
            showAutoSign = showAutoSign,
            showOnlyFriends = showOnlyFriends,
            displayedWeek = displayedWeek,
            calendarWeeks = calendarWeeks,
            selectedWeekIndex = selectedWeekIndex,
            currentMonthName = currentMonthName,
            canGoToPrevWeek = weekOffset > 0,
            canGoToNextWeek = weekOffset < MAX_WEEKS_FORWARD,
            hasActiveFilters = selectedSportNames.isNotEmpty() ||
                validBuildingName != null ||
                validTeacherName != null ||
                validTimeSlot != null ||
                !showOnlyAvailable ||
                !showAutoSign ||
                showOnlyFriends,
            displayedLessons = displayedLessons,
            hasPartialError = hasPartialError
        )
    }
}
