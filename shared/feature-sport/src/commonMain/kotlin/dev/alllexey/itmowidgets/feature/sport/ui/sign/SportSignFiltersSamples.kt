package dev.alllexey.itmowidgets.feature.sport.ui.sign

import dev.alllexey.itmowidgets.core.text.DateTexts
import dev.alllexey.itmowidgets.feature.sport.domain.model.SectionName
import dev.alllexey.itmowidgets.feature.sport.presentation.sign.CalendarDay
import dev.alllexey.itmowidgets.feature.sport.presentation.sign.SportSignUiState
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

/**
 * Synthetic `Запись` header states for the previews and host tests of the filters, the week strip and the section
 * picker; no Compose here, so the Russian sample text stays out of the Compose literal rule.
 */
internal object SportSignFiltersSamples {

    val sections: List<SectionName> = listOf(
        "Волейбол",
        "Общая физическая подготовка",
        "Плавание",
        "Спортивный туризм (северная ходьба)",
        "Бадминтон",
        "Фитнес (функциональная тренировка)",
        "Настольный теннис",
    ).map(::SectionName)

    val longSections: List<SectionName> = listOf(
        "Современные танцы (Клуб парных танцев \"Потанцуем\")",
        "Спортивный туризм (северная ходьба - маршруты)",
        "Оздоровительная физическая культура для студентов специальной медицинской группы",
        "Скалолазание на искусственном рельефе в спортивном комплексе на Ломоносова",
    ).map(::SectionName)

    val buildings = listOf("Кронверкский пр., 49", "ул. Ломоносова, 9", "Биржевая линия, 14")
    val teachers = listOf("Тихонова Марина Юрьевна", "Васильева Анастасия Романовна")
    val timeSlots = listOf("08:20", "10:00", "17:00", "20:20")

    /** Monday of the first week the strip shows; today is its Wednesday. */
    val firstMonday: LocalDate = LocalDate(2026, 9, 7)
    val today: LocalDate = LocalDate(2026, 9, 9)

    /** Six weeks from [firstMonday], `SportSignStateFactory`'s strip, with lessons on weekdays. */
    fun calendarWeeks(selected: LocalDate = today): List<List<CalendarDay>> = (0..LAST_WEEK).map { week ->
        (0 until DAYS_IN_WEEK).map { offset ->
            val date = firstMonday.plus(week * DAYS_IN_WEEK + offset, DateTimeUnit.DAY)
            val weekday = offset < WORKING_DAYS
            CalendarDay(
                date = date,
                dayOfWeek = DateTexts.shortWeekday(date.dayOfWeek),
                dayOfMonth = date.day.toString(),
                hasLessons = weekday,
                hasAvailableLessons = weekday && date >= today && offset % 2 == 0,
                isSelected = date == selected,
                isToday = date == today,
            )
        }
    }

    /** The header with nothing set: every selector shown, only today's default toggles on. */
    fun noneSet(): SportSignUiState.Content = week(0).copy(
        availableSports = sections,
        availableBuildings = buildings,
        availableTeachers = teachers,
        availableTimeSlots = timeSlots,
        hideTeacherSelector = false,
        hideTimeSelector = false,
    )

    /** Every filter set and every toggle flipped: the reset chip shows. */
    fun allSet(): SportSignUiState.Content = noneSet().copy(
        usedSportNames = setOf(sections[1]),
        selectedSportNames = setOf(sections[0], sections[2]),
        selectedBuildingName = buildings[0],
        selectedTeacherName = teachers[0],
        selectedTimeSlot = timeSlots[2],
        showOnlyAvailable = false,
        showAutoSign = false,
        showOnlyFriends = true,
        hasActiveFilters = true,
    )

    /** The default display options: the teacher and time selectors hidden. */
    fun hiddenSelectors(): SportSignUiState.Content =
        noneSet().copy(hideTeacherSelector = true, hideTimeSelector = true)

    /** Long section, building and teacher names selected. */
    fun longNames(): SportSignUiState.Content = noneSet().copy(
        availableSports = longSections + sections,
        availableBuildings = listOf(LONG_BUILDING) + buildings,
        availableTeachers = listOf(LONG_TEACHER) + teachers,
        selectedSportNames = longSections.take(3).toSet(),
        selectedBuildingName = LONG_BUILDING,
        selectedTeacherName = LONG_TEACHER,
        hasActiveFilters = true,
    )

    /** The strip on week [index] (0 is this week, [LAST_WEEK] the last one), its Monday or today selected. */
    fun week(index: Int): SportSignUiState.Content {
        val selected = if (index == 0) today else firstMonday.plus(index, DateTimeUnit.WEEK)
        val weeks = calendarWeeks(selected)
        return SportSignUiState.Content(
            displayedWeek = weeks[index],
            calendarWeeks = weeks,
            selectedWeekIndex = index,
            currentMonthName = DateTexts.standaloneMonth(weeks[index][3].date.month)
                .replaceFirstChar { it.titlecase() },
            canGoToPrevWeek = index > 0,
            canGoToNextWeek = index < LAST_WEEK,
        )
    }

    const val LAST_WEEK = 5
    private const val DAYS_IN_WEEK = 7
    private const val WORKING_DAYS = 6
    private const val LONG_BUILDING = "Спортивный комплекс университета ИТМО, ул. Ломоносова, 9, корпус 2, зал 3"
    private const val LONG_TEACHER = "Константинопольская-Преображенская Александра Владиславовна"
}
