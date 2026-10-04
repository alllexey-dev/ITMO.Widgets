package dev.alllexey.itmowidgets.feature.schedule.data.calendar

import dev.alllexey.itmowidgets.core.location.BuildingDirectory
import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.schedule.IcsFile
import dev.alllexey.itmowidgets.core.schedule.ScheduleExportRange
import dev.alllexey.itmowidgets.core.testing.FixedAcademicTime
import dev.alllexey.itmowidgets.core.testing.MainDispatcherRule
import dev.alllexey.itmowidgets.core.time.AcademicTimeProvider
import dev.alllexey.itmowidgets.feature.schedule.domain.calendar.OwnScheduleSource
import dev.alllexey.itmowidgets.feature.schedule.domain.model.Building
import dev.alllexey.itmowidgets.feature.schedule.domain.model.DaySchedule
import dev.alllexey.itmowidgets.feature.schedule.domain.model.Lesson
import dev.alllexey.itmowidgets.feature.schedule.domain.model.Room
import java.io.File
import java.io.IOException
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class IcsFileExportTest {
    @get:Rule val temporary = TemporaryFolder()
    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    private val directory by lazy { File(temporary.root, "ics") }
    private val requests = mutableListOf<Pair<LocalDate, LocalDate>>()
    private var days: List<DaySchedule> = emptyList()
    private var failure: Exception? = null
    private val export by lazy {
        IcsFileExport(
            OwnScheduleSource { start, end ->
                requests += start to end
                failure?.let { throw it }
                days
            },
            Today,
            BuildingDirectory(emptyList()),
            directory,
            mainDispatcherRule.appDispatchers
        ) { file -> "content://test/${file.name}" }
    }

    @Test
    fun `the range is asked from today and the file holds every lesson`() = runTest {
        days = listOf(day(TODAY, lesson(1), lesson(2, flowTypeId = 5)), day(TODAY.plusDays(6), lesson(3, flowTypeId = 3)))

        val result = export.export(ScheduleExportRange.Week)

        val name = "itmo-schedule-2026-10-02-2026-10-08.ics"
        assertEquals(listOf(TODAY to TODAY.plusDays(6)), requests)
        assertEquals(AppResult.Success(IcsFile("content://test/$name", name, 3)), result)
        val text = File(directory, name).readText()
        assertEquals(3, Regex("BEGIN:VEVENT").findAll(text).count())
        assertTrue("UID:lesson-3@widgets.alllexey.dev\r\n" in text)
    }

    @Test
    fun `a range without lessons writes nothing`() = runTest {
        assertEquals(AppResult.Success(null), export.export(ScheduleExportRange.TwoWeeks))
        assertEquals(listOf(TODAY to TODAY.plusDays(13)), requests)
        assertFalse(directory.exists())
    }

    @Test
    fun `only the latest file is kept`() = runTest {
        days = listOf(day(TODAY, lesson(1)))
        export.export(ScheduleExportRange.Week)

        export.export(ScheduleExportRange.Custom(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30)))

        assertEquals(listOf("itmo-schedule-2026-09-01-2026-09-30.ics"), directory.list()!!.toList())
    }

    @Test
    fun `a failed request is an error`() = runTest {
        failure = IOException("offline")

        assertEquals(AppResult.Failure(AppError.Network), export.export(ScheduleExportRange.Semester))
        assertEquals(listOf(TODAY to LocalDate.of(2027, 1, 31)), requests)
    }

    private fun day(date: LocalDate, vararg lessons: Lesson) = DaySchedule(date.dayOfWeek.value, 1, date, null, lessons.toList())

    private fun lesson(pairId: Long, flowTypeId: Int = 2) = Lesson(
        pairId = pairId, start = LocalTime.of(10, 0), end = LocalTime.of(11, 30), type = "Лекция",
        typeId = Lesson.TypeId(1), note = null, subjectName = "Физика", subjectId = pairId * 10,
        groupName = "ФИЗ ПИИКТ 3.2", flowId = pairId * 100, flowTypeId = flowTypeId, teacherIsu = 300001,
        teacherFio = "Тестовый преподаватель", room = Room("1506"), building = Building("Кронверкский пр., 49"),
        buildingId = 13, mainBuildingId = 13, format = "Очный", formatId = 1, zoomUrl = null, zoomPassword = null,
        zoomInfo = null
    )

    private object Today : AcademicTimeProvider by FixedAcademicTime(TODAY.atTime(9, 0))

    private companion object {
        val TODAY: LocalDate = LocalDate.of(2026, 10, 2)
    }
}
