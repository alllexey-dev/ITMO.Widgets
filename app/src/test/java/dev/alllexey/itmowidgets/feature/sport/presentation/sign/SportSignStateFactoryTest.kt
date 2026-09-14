package dev.alllexey.itmowidgets.feature.sport.presentation.sign

import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import dev.alllexey.itmowidgets.feature.sport.domain.model.SectionName
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportFilterCatalog
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportFilterOption
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportLesson
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportTimeSlot
import dev.alllexey.itmowidgets.feature.sport.domain.model.UnavailableReason
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

    @Test
    fun `online and other venues are filter categories without changing real location ids`() {
        val base = lesson(1, "Плавание", 335, 100, 1, "2026-07-22T10:00:00+03:00")
        val external = base.copy(roomId = 20013, roomName = "Внешний бассейн")
        val anotherExternal = base.copy(lessonId = 2, buildingId = 493, roomId = 21765)
        val online = base.copy(lessonId = 3, buildingId = null, roomId = -1, roomName = "Online")
        val unknownOffline = base.copy(lessonId = 4, buildingId = null, roomId = 10)
        val missingOnlineMarker = base.copy(lessonId = 5, buildingId = -1, roomId = 10)
        val rows = listOf(external, anotherExternal, online, unknownOffline, missingOnlineMarker)
        val filters = catalog.copy(buildings = catalog.buildings + SportFilterOption(0, "Другие объекты"))
        fun state(building: String?) = factory.create(rows, filters, timeSlots,
            SportSignFilters(selectedDate = timeProvider.today(), selectedBuildingName = building), false)

        assertEquals(listOf(online), state("Онлайн").displayedLessons)
        assertEquals(listOf(external, anotherExternal, unknownOffline, missingOnlineMarker), state("Другие объекты").displayedLessons)
        assertEquals(rows, state(null).displayedLessons)
        assertEquals(335L, external.buildingId)
        assertEquals(493L, anotherExternal.buildingId)
        assertNull(online.buildingId)
        assertEquals(-1L, online.roomId)
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
