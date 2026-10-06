package dev.alllexey.itmowidgets.feature.schedule.domain.model

import dev.alllexey.itmowidgets.core.navigation.LessonDetailsArgs
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LessonDetailsMappingTest {

    @Test
    fun `the flow name is trimmed and every other field is carried as before`() {
        val args = lesson(groupName = " ФИЗ ПИИКТ 3.2 ").toDetailsArgs(DATE)

        assertEquals(expected(flowName = "ФИЗ ПИИКТ 3.2"), args)
    }

    @Test
    fun `an empty flow name is no flow`() {
        assertNull(lesson(groupName = "").toDetailsArgs(DATE).flowName)
    }

    @Test
    fun `a blank flow name is no flow`() {
        assertEquals(expected(flowName = null), lesson(groupName = "  ").toDetailsArgs(DATE))
    }

    private fun expected(flowName: String?) = LessonDetailsArgs(
        pairId = 42, date = "2026-09-07", subjectName = "Физика", typeId = 1, format = "Очный",
        start = "08:20", end = "09:50", teacherFio = "Тестовый преподаватель", teacherIsu = 300001,
        room = "1506", building = "Кронверкский проспект, 49", buildingId = 13, mainBuildingId = 13,
        note = "Примечание", zoomUrl = "https://example.org/call", zoomPassword = "1234", zoomInfo = "Комната 1",
        flowName = flowName
    )

    private fun lesson(groupName: String) = Lesson(
        pairId = 42, start = LocalTime(8, 20), end = LocalTime(9, 50), type = "Лекция",
        typeId = Lesson.TypeId(1), note = "Примечание", subjectName = "Физика", subjectId = 7,
        groupName = groupName, flowId = 11, flowTypeId = 2, teacherIsu = 300001,
        teacherFio = "Тестовый преподаватель", room = Room("1506"), building = Building("Кронверкский проспект, 49"),
        buildingId = 13, mainBuildingId = 13, format = "Очный", formatId = 1,
        zoomUrl = "https://example.org/call", zoomPassword = "1234", zoomInfo = "Комната 1"
    )

    private companion object {
        val DATE: LocalDate = LocalDate(2026, 9, 7)
    }
}
