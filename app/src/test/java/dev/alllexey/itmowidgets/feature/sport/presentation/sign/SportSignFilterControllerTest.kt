package dev.alllexey.itmowidgets.feature.sport.presentation.sign

import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import dev.alllexey.itmowidgets.feature.sport.domain.model.SectionName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId

class SportSignFilterControllerTest {

    private val timeProvider = FixedTimeProvider()
    private val controller = SportSignFilterController(timeProvider)

    @Test
    fun `next and previous week stay inside visible range`() {
        repeat(MAX_WEEKS_FORWARD + 2) {
            controller.nextWeek()
        }

        assertEquals(
            timeProvider.today().with(java.time.DayOfWeek.MONDAY)
                .plusWeeks(MAX_WEEKS_FORWARD.toLong()),
            controller.filters.value.selectedDate
        )

        repeat(MAX_WEEKS_FORWARD + 2) {
            controller.previousWeek()
        }

        assertEquals(
            timeProvider.today().with(java.time.DayOfWeek.MONDAY),
            controller.filters.value.selectedDate
        )
    }

    @Test
    fun `reset preserves date and restores filter defaults`() {
        val selectedDate = timeProvider.today().plusDays(8)
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

    private class FixedTimeProvider : AcademicTimeProvider {
        override val zoneId: ZoneId = ZoneId.of("Europe/Moscow")

        override fun today(): LocalDate = LocalDate.of(2026, 7, 22)

        override fun now(): OffsetDateTime =
            OffsetDateTime.parse("2026-07-22T09:00:00+03:00")
    }
}
