package dev.alllexey.itmowidgets.feature.sport.presentation.sign

import dev.alllexey.itmowidgets.core.testing.FixedAcademicTime
import dev.alllexey.itmowidgets.feature.sport.domain.model.SectionName
import java.time.LocalDateTime
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SportSignFilterControllerTest {

    private val timeProvider = FixedAcademicTime(LocalDateTime.of(2026, 7, 22, 9, 0))
    private val controller = SportSignFilterController(timeProvider)

    @Test
    fun `next and previous week stay inside visible range`() {
        repeat(MAX_WEEKS_FORWARD + 2) {
            controller.nextWeek()
        }

        // Wednesday 22 July: the Monday of its week, five weeks on.
        assertEquals(LocalDate(2026, 8, 24), controller.filters.value.selectedDate)

        repeat(MAX_WEEKS_FORWARD + 2) {
            controller.previousWeek()
        }

        assertEquals(LocalDate(2026, 7, 20), controller.filters.value.selectedDate)
    }

    @Test
    fun `reset preserves date and restores filter defaults`() {
        val selectedDate = LocalDate(2026, 7, 30)
        controller.selectDate(selectedDate)
        controller.selectSports(setOf(SectionName("Плавание")))
        controller.selectBuilding("Кронверкский")
        controller.selectTeacher("Преподаватель")
        controller.selectTime("10:00-11:30")
        controller.showOnlyAvailable(false)
        controller.showOnlyFriends(true)
        controller.showAutoSign(false)

        controller.reset()

        val filters = controller.filters.value
        assertEquals(selectedDate, filters.selectedDate)
        assertEquals(emptySet<SectionName>(), filters.selectedSportNames)
        assertNull(filters.selectedBuildingName)
        assertNull(filters.selectedTeacherName)
        assertNull(filters.selectedTimeSlot)
        assertEquals(true, filters.showOnlyAvailable)
        assertEquals(false, filters.showOnlyFriends)
        assertEquals(true, filters.showAutoSign)
    }
}
