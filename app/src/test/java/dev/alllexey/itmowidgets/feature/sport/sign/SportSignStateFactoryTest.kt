package dev.alllexey.itmowidgets.feature.sport.sign

import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import dev.alllexey.itmowidgets.domain.model.sport.SectionName
import dev.alllexey.itmowidgets.domain.model.sport.SportFilterCatalog
import dev.alllexey.itmowidgets.domain.model.sport.SportFilterOption
import dev.alllexey.itmowidgets.domain.model.sport.SportLesson
import dev.alllexey.itmowidgets.domain.model.sport.SportTimeSlot
import dev.alllexey.itmowidgets.domain.model.sport.UnavailableReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId

class SportSignStateFactoryTest {

    private val timeProvider = FixedTimeProvider()
    private val factory = SportSignStateFactory(timeProvider)
    private val catalog = SportFilterCatalog(
        buildings = listOf(
            SportFilterOption(id = -1, value = "Онлайн"),
            SportFilterOption(id = 10, value = "Кронверкский")
        ),
        sections = emptyList(),
        sportTypes = emptyList(),
        teachers = listOf(
            SportFilterOption(id = 100, value = "Иванов И. И."),
            SportFilterOption(id = 200, value = "Петров П. П.")
        )
    )
    private val timeSlots = listOf(
        SportTimeSlot(id = 1, start = "10:00", end = "11:30"),
        SportTimeSlot(id = 2, start = "12:00", end = "13:30")
    )

    @Test
    fun `creates filtered content and calendar metadata`() {
        val swimming = lesson(
            id = 1,
            section = "Плавание",
            buildingId = 10,
            teacherIsu = 100,
            timeSlotId = 1,
            start = "2026-07-22T10:00:00+03:00"
        )
        val yoga = lesson(
            id = 2,
            section = "Йога",
            buildingId = 10,
            teacherIsu = 200,
            timeSlotId = 2,
            start = "2026-07-22T12:00:00+03:00"
        )
        val filters = SportSignFilters(
            selectedSportNames = setOf(SectionName("Плавание")),
            selectedBuildingName = "Кронверкский",
            selectedTeacherName = "Иванов И. И.",
            selectedTimeSlot = "10:00-11:30",
            selectedDate = timeProvider.today()
        )

        val state = factory.create(
            lessons = listOf(swimming, yoga),
            catalog = catalog,
            timeSlots = timeSlots,
            userFilters = filters,
            hasPartialError = false
        )

        assertEquals(listOf(swimming), state.displayedLessons)
        assertEquals(6, state.calendarWeeks.size)
        assertEquals(7, state.displayedWeek.size)
        assertEquals(0, state.selectedWeekIndex)
        assertTrue(state.displayedWeek.single { it.date == timeProvider.today() }.hasLessons)
        assertTrue(state.hasActiveFilters)
        assertTrue(SectionName("Плавание") in state.availableSports)
        assertTrue(SectionName("Йога") in state.availableSports)
    }

    @Test
    fun `drops invalid dependent filters without mutating input`() {
        val filters = SportSignFilters(
            selectedBuildingName = "Несуществующий корпус",
            selectedTeacherName = "Несуществующий преподаватель",
            selectedTimeSlot = "00:00-00:00",
            selectedDate = timeProvider.today()
        )

        val state = factory.create(
            lessons = listOf(
                lesson(
                    id = 1,
                    section = "Плавание",
                    buildingId = 10,
                    teacherIsu = 100,
                    timeSlotId = 1,
                    start = "2026-07-22T10:00:00+03:00"
                )
            ),
            catalog = catalog,
            timeSlots = timeSlots,
            userFilters = filters,
            hasPartialError = false
        )

        assertNull(state.selectedBuildingName)
        assertNull(state.selectedTeacherName)
        assertNull(state.selectedTimeSlot)
        assertFalse(state.hasActiveFilters)
        assertEquals("Несуществующий корпус", filters.selectedBuildingName)
    }

    private fun lesson(
        id: Long,
        section: String,
        buildingId: Long?,
        teacherIsu: Int,
        timeSlotId: Long,
        start: String
    ): SportLesson {
        val startsAt = OffsetDateTime.parse(start)
        return SportLesson(
            isLessonReal = true,
            lessonId = id,
            start = startsAt,
            end = startsAt.plusHours(1),
            sectionId = id,
            sectionName = SectionName(section),
            sectionLevel = 1,
            lessonGroupId = id,
            lessonLevel = 1,
            typeId = 2,
            buildingId = buildingId,
            roomId = 1,
            roomName = "Аудитория",
            limit = 10,
            available = 5,
            comment = null,
            timeSlotId = timeSlotId,
            timeSlotStart = "10:00",
            timeSlotEnd = "11:30",
            intersection = false,
            canSignIn = true,
            unavailableReasons = emptyList<UnavailableReason>(),
            signed = false,
            teacherIsu = teacherIsu,
            teacherFio = "Преподаватель",
            signEntry = null,
            signQueue = null,
            friendsBookings = emptyList()
        )
    }

    private class FixedTimeProvider : AcademicTimeProvider {
        override val zoneId: ZoneId = ZoneId.of("Europe/Moscow")

        override fun today(): LocalDate = LocalDate.of(2026, 7, 22)

        override fun now(): OffsetDateTime =
            OffsetDateTime.parse("2026-07-22T09:00:00+03:00")
    }
}
